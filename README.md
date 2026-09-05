<div align="center">

# Us — Relationship Co‑Pilot

**A private, offline‑first Android app that helps you understand your partner, defuse conflict, and say the hard thing kindly.**

[![Release APK](https://github.com/NikitHamal/Us/actions/workflows/release.yml/badge.svg)](https://github.com/NikitHamal/Us/actions/workflows/release.yml)
[![CI](https://github.com/NikitHamal/Us/actions/workflows/ci.yml/badge.svg)](https://github.com/NikitHamal/Us/actions/workflows/ci.yml)

Kotlin · Jetpack Compose · Material 3 · Hilt · Room + SQLCipher · minSdk 26 · targetSdk 34

</div>

---

## Get the APK (no local build needed)

**You never have to build this locally.** Every push to `main` produces a signed release APK.

1. Push to `main`, or open **Actions → Release APK → Run workflow**.
2. Wait for the green tick.
3. Open the run → **Artifacts** → download **`us-release-apk`**.
4. Inside you get `Us-v1.0.0-1-a1b2c3d-2026-09-05-release.apk` and `SHA256SUMS.txt`.

The workflow log prints the APK **path, size and SHA‑256**, and verifies the signature with `apksigner`.
Push a `v*` tag (e.g. `git tag v1.0.0 && git push --tags`) to also publish a **GitHub Release** with the APK attached.

### How signing works
- `keystore.properties` is committed with default passwords — this is a personal‑use app, and the key
  only signs your own builds.
- If `app/keystore/release.jks` is not present, CI **generates it** with `keytool` using those same
  defaults, so the build always signs.
- `app/build.gradle.kts` resolves signing material in this order:
  `env vars (US_STORE_FILE / US_STORE_PASSWORD / US_KEY_ALIAS / US_KEY_PASSWORD)` → `keystore.properties` → built‑in defaults.
- Adding repository secrets with those names silently upgrades CI to your own key. No workflow edit needed.
- Debug builds keep using the standard Android debug key, so `installDebug` still works.

---

## What it does

| Module | What you get |
|---|---|
| **A. Psychological Profiles** | A guided quiz builds *My Profile* and *Her Profile*: attachment style, ranked love languages, conflict style, triggers, soothers, Big Five, stress patterns, communication preferences. Fully structured data, editable, and **versioned** — every edit is a new version you can restore. |
| **B. Memory & Pattern Engine** | Log moments from shares, journal or notes. Searchable, filterable timeline. On‑device detection of conflict frequency, recurring triggers, Gottman's **Four Horsemen**, and repair attempts, surfaced on an insights dashboard with the 5:1 magic‑ratio check. |
| **C. Communication Coach** | **Share‑to‑App** from Instagram/Messenger, optional **notification tone check**, and a **Before You Send** verdict: harshness meter, horsemen with the exact evidence phrase, plus three NVC rewrites (soft / direct / playful) and a love‑language suggestion. |
| **D. Journal & Check‑ins** | Daily mood / energy / connection check‑in with streaks, a quick journal, and repair starters tailored to her attachment and conflict style. |

### It is not a surveillance tool
Us **never** scrapes your DMs. Text enters the app only when you share it in, type it, or turn on
the notification listener yourself — and even then it is analysed on device and never stored
automatically. The rewrite engine is built to help you be understood, not to help you win.

---

## Privacy model

| Data | Where it lives | Protection |
|---|---|---|
| Moments, profiles, check‑ins | Room database `us.db` | **SQLCipher AES‑256**, key generated on device and kept in EncryptedSharedPreferences (Android Keystore) |
| API key / base URL | `us_secure_settings` | **EncryptedSharedPreferences**, AES256‑GCM |
| Toggles and theme | DataStore | Non‑sensitive only |
| Cloud AI | **Off by default** | Only the text you explicitly analyse is sent, to the endpoint *you* configure |
| Backups | Disabled | `allowBackup=false`, extraction rules exclude everything |
| App lock | Optional biometric / device credential | Re‑locks whenever the app leaves the foreground |

---

## AI abstraction

```kotlin
interface LlmProvider {
    suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis>
    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet>
    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport>
    suspend fun embed(text: String): Outcome<FloatArray>
}
```

- **OfflineProvider** — deterministic on‑device engine (lexicon + NVC transformer + Gottman rules).
  Drop a `needle2.cact` bundle into `app/src/main/assets/models/` and `CactModelLoader` picks it up
  automatically, raising confidence and exposing the tool‑calling surface. No code change.
- **CloudProvider** — generic **OpenAI‑compatible** Ktor client. Point it at OpenAI, a local
  llama.cpp server, or your own proxy/scraper: just set base URL, key and model in Settings.
- **LlmRouter** — offline first, always. If offline confidence `< 0.7` **and** you enabled cloud AI,
  it re‑runs the request on the cloud and shows which engine answered.

---

## Architecture

Clean Architecture + MVVM, strict package layering, **max 600 lines per file** (enforced in CI by
`scripts/check_file_size.sh`).

```
ui → domain (usecases) → data (repositories) → local (Room/DataStore) | ai (providers)
```

Full module map, ADR log, data models and progress live in **[AGENTS.md](AGENTS.md)** — kept up to
date with every major change.

---

## Local development (optional)

```bash
# JDK 17, Android Studio Koala or newer
./gradlew installDebug        # debug build on a connected device
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # lint
bash scripts/check_file_size.sh
```

Again: this is optional. The supported path is push → Actions → download APK.

---

## License

Personal project. Not therapy, not medical advice, and not a substitute for talking to each other.
