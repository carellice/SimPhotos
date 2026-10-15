package com.simphotos.app.ui

import android.app.Application
import android.content.IntentSender
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simphotos.app.data.AnalyzedPhoto
import com.simphotos.app.data.AppSettings
import com.simphotos.app.data.DeleteMode
import com.simphotos.app.data.FeatureCache
import com.simphotos.app.data.PhotoRepository
import com.simphotos.app.data.SettingsRepository
import com.simphotos.app.data.SimilarGroup
import com.simphotos.app.engine.ImageAnalyzer
import com.simphotos.app.engine.SimilarityEngine
import com.simphotos.app.update.UpdateInfo
import com.simphotos.app.update.UpdateManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

sealed interface Screen {
    data object Home : Screen
    data object Scanning : Screen
    data object Results : Screen
    data class Detail(val groupId: Long) : Screen
    data object Settings : Screen
    data object Done : Screen
}

enum class ScanPhase { READING, ANALYZING, GROUPING }

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.READING,
    val done: Int = 0,
    val total: Int = 0,
    val currentUri: android.net.Uri? = null,
)

data class DeleteSummary(val count: Int, val bytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

data class UiState(
    val screen: Screen = Screen.Home,
    val galleryCount: Int? = null,
    val lastScanAt: Long = 0L,
    val totalFreedBytes: Long = 0L,
    val progress: ScanProgress = ScanProgress(),
    val groups: List<SimilarGroup> = emptyList(),
    /** Photo ids marked for deletion. */
    val toDelete: Set<Long> = emptySet(),
    val lastDelete: DeleteSummary? = null,
    val error: String? = null,
) {
    val toDeleteBytes: Long
        get() = groups.asSequence().flatMap { it.members }
            .filter { it.photo.id in toDelete }.sumOf { it.photo.sizeBytes }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val photos = PhotoRepository(app)
    private val cache = FeatureCache(app)
    private val settingsRepo = SettingsRepository(app)
    private val analyzer = ImageAnalyzer(app.contentResolver)
    private val updater = UpdateManager(app)

    val currentVersion: String get() = updater.currentVersion

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    private var updateJob: Job? = null

    val settings: StateFlow<AppSettings> = settingsRepo.settings

    private val _state = MutableStateFlow(
        UiState(lastScanAt = settingsRepo.lastScanAt, totalFreedBytes = settingsRepo.totalFreedBytes)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var analyzed: List<AnalyzedPhoto>? = null
    private var scanJob: Job? = null
    private var pendingDeleteIds: List<Long> = emptyList()

    init {
        // Any APK left from a previous update has been installed (or abandoned) by now.
        viewModelScope.launch(Dispatchers.IO) { updater.cleanUp() }
        // Changing the sensitivity after a scan regroups instantly, without re-analyzing.
        viewModelScope.launch {
            settings.drop(1).collect { s -> analyzed?.let { regroup(it, s) } }
        }
    }

    fun refreshGalleryCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { photos.loadPhotos().size }.getOrNull() }
            _state.update { it.copy(galleryCount = count) }
        }
    }

    private var settingsOrigin: Screen = Screen.Home

    fun navigate(screen: Screen) = _state.update {
        if (screen == Screen.Settings && it.screen != Screen.Settings) settingsOrigin = it.screen
        it.copy(screen = screen)
    }

    fun back(): Boolean {
        val s = _state.value.screen
        val target = when (s) {
            Screen.Home -> return false
            Screen.Scanning -> { cancelScan(); Screen.Home }
            is Screen.Detail -> Screen.Results
            Screen.Settings -> settingsOrigin
            Screen.Results, Screen.Done -> Screen.Home
        }
        navigate(target)
        return true
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepo.update(transform)

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) { cache.clear() }
        analyzed = null
        _state.update { it.copy(groups = emptyList(), toDelete = emptySet()) }
    }

    fun startScan() {
        scanJob?.cancel()
        _state.update { it.copy(screen = Screen.Scanning, progress = ScanProgress(), error = null) }
        scanJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { scan() }
                analyzed = result
                settingsRepo.lastScanAt = System.currentTimeMillis()
                _state.update { it.copy(lastScanAt = settingsRepo.lastScanAt, galleryCount = result.size) }
                regroup(result, settings.value)
                navigate(Screen.Results)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(screen = Screen.Home, error = "Scansione non riuscita: ${e.message}") }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private suspend fun scan(): List<AnalyzedPhoto> = coroutineScope {
        val all = photos.loadPhotos()
        val cached = cache.loadValid(all)
        val missing = all.filter { it.id !in cached }
        val total = all.size
        val done = AtomicInteger(cached.size)
        _state.update { it.copy(progress = ScanProgress(ScanPhase.ANALYZING, done.get(), total)) }

        val fresh = Collections.synchronizedList(ArrayList<AnalyzedPhoto>(missing.size))
        val pendingWrites = Collections.synchronizedList(ArrayList<AnalyzedPhoto>())
        val next = AtomicInteger(0)
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        (0 until workers).map {
            async(Dispatchers.IO) {
                while (true) {
                    ensureActive()
                    val i = next.getAndIncrement()
                    if (i >= missing.size) break
                    val photo = missing[i]
                    analyzer.analyze(photo)?.let { f ->
                        val a = AnalyzedPhoto(photo, f)
                        fresh += a
                        pendingWrites += a
                    }
                    val d = done.incrementAndGet()
                    if (d % 8 == 0 || d == total) {
                        _state.update { it.copy(progress = ScanProgress(ScanPhase.ANALYZING, d, total, photo.uri)) }
                    }
                    if (pendingWrites.size >= 200) flush(pendingWrites)
                }
            }
        }.awaitAll()
        flush(pendingWrites)

        _state.update { it.copy(progress = it.progress.copy(phase = ScanPhase.GROUPING, done = total)) }
        all.mapNotNull { p -> cached[p.id]?.let { AnalyzedPhoto(p, it) } } + fresh
    }

    private fun flush(list: MutableList<AnalyzedPhoto>) {
        val batch = synchronized(list) { ArrayList(list).also { list.clear() } }
        cache.putAll(batch)
    }

    private suspend fun regroup(items: List<AnalyzedPhoto>, s: AppSettings) {
        val groups = withContext(Dispatchers.Default) { SimilarityEngine.group(items, s) }
        _state.update { it.copy(groups = groups, toDelete = defaultSelection(groups, s)) }
    }

    private fun defaultSelection(groups: List<SimilarGroup>, s: AppSettings): Set<Long> =
        groups.flatMap { g ->
            g.members.filter { it.photo.id != g.bestId && !(s.protectFavorites && it.photo.isFavorite) }
                .map { it.photo.id }
        }.toSet()

    fun toggleDelete(photoId: Long) {
        val group = _state.value.groups.firstOrNull { g -> g.members.any { it.photo.id == photoId } } ?: return
        _state.update {
            val set = it.toDelete.toMutableSet()
            if (!set.remove(photoId)) set += photoId
            // Never allow deleting every photo of a group: at least one must survive.
            if (group.members.all { m -> m.photo.id in set }) set -= photoId
            it.copy(toDelete = set)
        }
    }

    /** Makes [photoId] the one to keep in its group; all others get selected for deletion. */
    fun keepOnly(groupId: Long, photoId: Long) {
        _state.update { st ->
            val groups = st.groups.map { if (it.id == groupId) it.copy(bestId = photoId) else it }
            val g = groups.first { it.id == groupId }
            val set = st.toDelete.toMutableSet()
            g.members.forEach { if (it.photo.id == photoId) set -= it.photo.id else set += it.photo.id }
            st.copy(groups = groups, toDelete = set)
        }
    }

    fun setGroupSelected(groupId: Long, selected: Boolean) {
        _state.update { st ->
            val g = st.groups.first { it.id == groupId }
            val set = st.toDelete.toMutableSet()
            g.members.forEach { if (it.photo.id != g.bestId) { if (selected) set += it.photo.id else set -= it.photo.id } }
            st.copy(toDelete = set)
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { st ->
            st.copy(toDelete = if (selected) defaultSelection(st.groups, settings.value.copy(protectFavorites = false)) else emptySet())
        }
    }

    /** Builds the system confirmation request for the selected photos. */
    fun buildDeleteRequest(): IntentSender? {
        val st = _state.value
        val members = st.groups.flatMap { it.members }.filter { it.photo.id in st.toDelete }
        if (members.isEmpty()) return null
        pendingDeleteIds = members.map { it.photo.id }
        val uris = members.map { it.photo.uri }
        val resolver = getApplication<Application>().contentResolver
        return when (settings.value.deleteMode) {
            DeleteMode.TRASH -> MediaStore.createTrashRequest(resolver, uris, true)
            DeleteMode.PERMANENT -> MediaStore.createDeleteRequest(resolver, uris)
        }.intentSender
    }

    fun onDeleteResult(confirmed: Boolean) {
        val ids = pendingDeleteIds.toSet()
        pendingDeleteIds = emptyList()
        if (!confirmed || ids.isEmpty()) return
        val st = _state.value
        val bytes = st.groups.flatMap { it.members }.filter { it.photo.id in ids }.sumOf { it.photo.sizeBytes }
        settingsRepo.totalFreedBytes += bytes
        viewModelScope.launch(Dispatchers.IO) { cache.remove(ids) }
        analyzed = analyzed?.filter { it.photo.id !in ids }
        val remaining = st.groups.mapNotNull { g ->
            val left = g.members.filter { it.photo.id !in ids }
            if (left.size < 2) null
            else g.copy(
                members = left,
                bestId = if (left.any { it.photo.id == g.bestId }) g.bestId else left.maxBy { it.score }.photo.id,
            )
        }
        _state.update {
            it.copy(
                groups = remaining,
                toDelete = it.toDelete - ids,
                lastDelete = DeleteSummary(ids.size, bytes),
                totalFreedBytes = settingsRepo.totalFreedBytes,
                galleryCount = it.galleryCount?.minus(ids.size),
                screen = Screen.Done,
            )
        }
    }

    /** Called when the settings screen opens. Skips the check while a download is in progress. */
    fun checkForUpdates() {
        when (_update.value) {
            is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> return
            else -> Unit
        }
        updateJob = viewModelScope.launch {
            _update.value = UpdateState.Checking
            _update.value = try {
                updater.checkForUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed("Impossibile verificare gli aggiornamenti. Controlla la connessione.")
            }
        }
    }

    fun downloadUpdate() {
        val info = (_update.value as? UpdateState.Available)?.info ?: return
        updateJob = viewModelScope.launch {
            _update.value = UpdateState.Downloading(info, 0f)
            try {
                var last = 0f
                val file = updater.download(info) { p ->
                    if (p - last >= 0.01f || p >= 1f) {
                        last = p
                        _update.value = UpdateState.Downloading(info, p)
                    }
                }
                _update.value = UpdateState.ReadyToInstall(info, file)
                installUpdate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _update.value = UpdateState.Available(info)
                throw e
            } catch (e: Exception) {
                _update.value = UpdateState.Failed("Download non riuscito. Riprova.")
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
    }

    fun installUpdate() {
        val ready = _update.value as? UpdateState.ReadyToInstall ?: return
        // The first time, Android asks to allow installs from SimPhotos; the user then taps again.
        if (updater.canInstall()) updater.install(ready.file) else updater.openInstallPermissionSettings()
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
