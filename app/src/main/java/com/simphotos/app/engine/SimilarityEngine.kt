package com.simphotos.app.engine

import com.simphotos.app.data.AnalyzedPhoto
import com.simphotos.app.data.AppSettings
import com.simphotos.app.data.GroupMember
import com.simphotos.app.data.SimilarGroup
import com.simphotos.app.data.TimeWindow
import kotlin.math.ln

/**
 * Groups visually similar photos taken close in time, and picks the best photo of each group.
 */
object SimilarityEngine {

    /** Below this pHash similarity two photos cannot reach [threshold], so we skip the full check. */
    private fun minPHashSimilarity(threshold: Float) = (2f * threshold - 1f).coerceAtLeast(0f)

    fun group(photos: List<AnalyzedPhoto>, settings: AppSettings): List<SimilarGroup> {
        val items = photos
            .filter { settings.includeScreenshots || !it.photo.isScreenshot }
            .sortedBy { it.photo.dateTaken }
        if (items.size < 2) return emptyList()

        val uf = UnionFind(items.size)
        val threshold = settings.threshold
        val maxHamming = ((1f - minPHashSimilarity(threshold)) * 64).toInt()
        val window = settings.timeWindow.millis

        // 1. Similar photos within the time window.
        for (i in items.indices) {
            val a = items[i]
            var j = i + 1
            while (j < items.size) {
                val b = items[j]
                if (window != TimeWindow.UNLIMITED.millis &&
                    b.photo.dateTaken - a.photo.dateTaken > window
                ) break
                if (java.lang.Long.bitCount(a.features.pHash xor b.features.pHash) <= maxHamming &&
                    ImageAnalyzer.similarity(a.features, b.features) >= threshold
                ) uf.union(i, j)
                j++
            }
        }

        // 2. Identical copies anywhere in the gallery (e.g. images saved twice from chats).
        if (settings.findExactDuplicatesEverywhere) {
            val byHash = HashMap<Pair<Long, Long>, Int>()
            items.forEachIndexed { i, it ->
                val key = it.features.pHash to it.features.dHash
                val first = byHash.putIfAbsent(key, i)
                if (first != null && ImageAnalyzer.similarity(items[first].features, it.features) >= 0.985f) {
                    uf.union(first, i)
                }
            }
        }

        val clusters = items.indices.groupBy { uf.find(it) }.values.filter { it.size > 1 }
        return clusters.mapNotNull { idx -> buildGroup(idx.map { items[it] }, settings) }
            .sortedByDescending { it.members.first().photo.dateTaken }
    }

    private fun buildGroup(members: List<AnalyzedPhoto>, settings: AppSettings): SimilarGroup? {
        val logSharp = members.map { ln(1.0 + it.features.sharpness) }
        val maxSharp = logSharp.max().coerceAtLeast(1e-6)
        val maxMp = members.maxOf { it.photo.megapixels }.coerceAtLeast(1e-6)
        val maxExposure = members.maxOf { it.features.exposure }

        val scored = members.mapIndexed { i, m ->
            val sharpRel = (logSharp[i] / maxSharp).toFloat()
            val resRel = (m.photo.megapixels / maxMp).toFloat()
            val exposure = m.features.exposure
            val raw = 0.55f * sharpRel + 0.25f * exposure + 0.20f * resRel
            Triple(m, raw, Triple(sharpRel, exposure, resRel))
        }

        val exact = members.all {
            it.features.pHash == members[0].features.pHash && it.features.dHash == members[0].features.dHash
        }

        val favorites = scored.filter { it.first.photo.isFavorite }
        val pool = if (settings.protectFavorites && favorites.isNotEmpty()) favorites else scored
        val best = if (exact) {
            // Identical copies: keep the highest resolution / largest file, then the oldest original.
            pool.maxWith(
                compareBy<Triple<AnalyzedPhoto, Float, *>>({ it.first.photo.megapixels }, { it.first.photo.sizeBytes })
                    .thenByDescending { it.first.photo.dateTaken }
            )
        } else {
            pool.maxWith(compareBy({ it.second }, { it.first.photo.megapixels }, { it.first.photo.sizeBytes }))
        }.first

        // Drop photos that only joined through a chain of intermediate shots but don't really
        // look like the photo we're keeping.
        val tolerance = 0.05f
        val result = scored.mapNotNull { (m, raw, parts) ->
            val sim = if (m === best) 1f else ImageAnalyzer.similarity(m.features, best.features)
            if (sim < settings.threshold - tolerance) return@mapNotNull null
            val reasons = buildList {
                if (m.photo.isFavorite) add("Preferita")
                if (parts.first >= 0.999f && members.size > 1) add("La più nitida")
                if (m.features.exposure >= maxExposure - 1e-4f && members.size > 1) add("Luce migliore")
                if (parts.third >= 0.999f) add("Risoluzione massima")
            }
            GroupMember(
                photo = m.photo,
                features = m.features,
                score = (raw * 100).toInt().coerceIn(0, 100),
                similarityToBest = sim,
                reasons = reasons,
            )
        }
        if (result.size < 2) return null

        return SimilarGroup(
            id = best.photo.id,
            members = result.sortedWith(
                compareByDescending<GroupMember> { it.photo.id == best.photo.id }.thenByDescending { it.score }
            ),
            bestId = best.photo.id,
            isExactDuplicate = exact,
        )
    }

    private class UnionFind(n: Int) {
        private val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
    }
}
