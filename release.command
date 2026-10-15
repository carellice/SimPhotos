#!/bin/bash
# Pubblica una nuova versione di SimPhotos su GitHub:
#   1. incrementa versionCode e versionName (patch: 1.0.3 -> 1.0.4)
#   2. compila l'APK release
#   3. fa commit e push del cambio di versione
#   4. crea la release su GitHub con SimPhotos.apk allegato
#   5. elimina le release (e i tag) precedenti
# Doppio clic per avviarlo dal Finder.

set -euo pipefail
cd "$(dirname "$0")"

REPO="carellice/SimPhotos"
GRADLE_FILE="app/build.gradle.kts"
APK_SRC="app/build/outputs/apk/release/app-release.apk"
APK_OUT="SimPhotos.apk"

bold() { printf "\n\033[1;35m▶ %s\033[0m\n" "$1"; }
fail() { printf "\n\033[1;31m✖ %s\033[0m\n" "$1"; read -r -p "Premi Invio per chiudere…"; exit 1; }

# Il Finder non carica il PATH della shell: aggiungo i percorsi tipici di Homebrew.
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

command -v gh >/dev/null || fail "GitHub CLI (gh) non trovato. Installalo con: brew install gh"
gh auth status >/dev/null 2>&1 || fail "Non sei autenticato su GitHub. Esegui: gh auth login"
[ -z "$(git status --porcelain --untracked-files=no)" ] || fail "Ci sono modifiche non committate. Fai commit prima di pubblicare."

# --- Versione -----------------------------------------------------------------
OLD_CODE=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE")
OLD_NAME=$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE")
[ -n "$OLD_CODE" ] && [ -n "$OLD_NAME" ] || fail "Impossibile leggere la versione da $GRADLE_FILE"

NEW_CODE=$((OLD_CODE + 1))
IFS='.' read -r MAJ MIN PAT <<< "$OLD_NAME"
NEW_NAME="${MAJ:-1}.${MIN:-0}.$(( ${PAT:-0} + 1 ))"
TAG="v$NEW_NAME"

bold "Versione $OLD_NAME ($OLD_CODE) → $NEW_NAME ($NEW_CODE)"
cp "$GRADLE_FILE" "$GRADLE_FILE.bak"
restore() { [ -f "$GRADLE_FILE.bak" ] && mv "$GRADLE_FILE.bak" "$GRADLE_FILE"; }
trap 'restore' ERR

sed -i '' -E "s/^([[:space:]]*versionCode = )[0-9]+/\1$NEW_CODE/" "$GRADLE_FILE"
sed -i '' -E "s/^([[:space:]]*versionName = )\"[^\"]+\"/\1\"$NEW_NAME\"/" "$GRADLE_FILE"

# --- Build --------------------------------------------------------------------
bold "Compilo l'APK release…"
./gradlew :app:assembleRelease -q || { restore; fail "Compilazione non riuscita: versione ripristinata."; }
cp "$APK_SRC" "$APK_OUT"
rm -f "$GRADLE_FILE.bak"
trap - ERR

# --- Git ----------------------------------------------------------------------
PREV_TAG=$(gh release list --repo "$REPO" --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null || true)

bold "Commit e push della nuova versione…"
git add "$GRADLE_FILE"
git commit -q -m "Versione $NEW_NAME"
git push -q

# Note di rilascio: i commit dall'ultima release (escluse le versioni).
if [ -n "$PREV_TAG" ] && git rev-parse -q --verify "refs/tags/$PREV_TAG" >/dev/null 2>&1; then
    RANGE="$PREV_TAG..HEAD"
else
    git fetch -q --tags 2>/dev/null || true
    if [ -n "$PREV_TAG" ] && git rev-parse -q --verify "refs/tags/$PREV_TAG" >/dev/null 2>&1; then
        RANGE="$PREV_TAG..HEAD"
    else
        RANGE="HEAD"
    fi
fi
CHANGES=$(git log "$RANGE" --pretty='- %s' | grep -v '^- Versione ' | head -30 || true)
[ -n "$CHANGES" ] || CHANGES="- Miglioramenti e correzioni"
NOTES=$(printf "## Novità\n\n%s\n\n📲 Scarica **SimPhotos.apk** qui sotto, oppure aggiorna direttamente dall'app in Impostazioni → Aggiornamenti." "$CHANGES")

# --- Release ------------------------------------------------------------------
bold "Creo la release $TAG su GitHub…"
gh release create "$TAG" "$APK_OUT" --repo "$REPO" --title "SimPhotos $NEW_NAME" --notes "$NOTES" --latest
git fetch -q --tags

bold "Elimino le release precedenti…"
gh release list --repo "$REPO" --limit 100 --json tagName --jq '.[].tagName' | while read -r t; do
    if [ "$t" != "$TAG" ]; then
        echo "  • $t"
        gh release delete "$t" --repo "$REPO" --yes --cleanup-tag
        git tag -d "$t" >/dev/null 2>&1 || true
    fi
done

printf "\n\033[1;32m✔ SimPhotos %s pubblicata!\033[0m\n" "$NEW_NAME"
echo "  https://github.com/$REPO/releases/tag/$TAG"
echo
read -r -p "Premi Invio per chiudere…"
