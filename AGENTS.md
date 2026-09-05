# AGENTS.md — Us: Relationship Co‑Pilot

> Living architecture + progress document. **Update this file on every major change.**
> Last updated: 2026-09-05

---

## 1. Project Vision

**Us — Relationship Co‑Pilot** is a private, offline‑first Android app that helps *one* person
(the user) understand their partner better, defuse conflict, and communicate more kindly.

It is a **personal growth tool**, not a surveillance or manipulation tool.

- It **never scrapes** Instagram/Messenger DMs.
- Content enters the app only through **explicit user action** (Share‑to‑App, journal, manual
  entry) or an **opt‑in, on‑device Notification Listener** the user can disable at any time.
- All analysis defaults to **on device**. Cloud AI is opt‑in and off by default.

### Ethical guardrails (enforced in product copy + code)
- No "how do I win the argument" framing. The rephrase engine is built on **Nonviolent
  Communication (NVC)**: Observation → Feeling → Need → Request.
- The Partner Profile is a **model of empathy**, described in the UI as *"your understanding of
  her, which will be wrong sometimes"* — it is versioned and editable.
- Any future couple/shared feature must be **explicit dual opt‑in with consent records**. Nothing
  leaves the device without a toggle the user flipped themselves.

---

## 2. Architecture Decisions (ADR log)

| # | Decision | Rationale |
|---|----------|-----------|
| ADR‑1 | **Clean Architecture + MVVM**: `ui → domain (usecases) → data (repos) → local/ai` | Testable, no Android types in domain. |
| ADR‑2 | **Single Gradle module with strict package layering** instead of 12 Gradle modules | CI build time + reliability; layering is enforced by package boundaries and review, and the app is still fully modular in structure. Splitting into Gradle modules later is mechanical. |
| ADR‑3 | **Room + SQLCipher** (`SupportFactory`) with a random 256‑bit passphrase stored in **EncryptedSharedPreferences** (AES256‑GCM, Android Keystore backed) | Encryption at rest without asking the user for a password. |
| ADR‑4 | **DataStore (Proto‑less, Preferences)** for non‑secret settings; **EncryptedSharedPreferences** for API keys | Never store secrets in DataStore plaintext. |
| ADR‑5 | **`LlmProvider` interface** with `analyzeTone / rephrase / extractPatterns / embed`, selected at runtime by `LlmRouter` | Swappable providers; own proxy/scraper can be plugged in later with zero UI change. |
| ADR‑10 | **Nebians fleet as device-native providers** (`ai/nebians/`): TryingOpen + keyless guest scrapers (K2Think, Poolside, Motif, Yqcloud, ChatJimmy) + keyless OpenAI pools (LLM7, Kilo, Zen) + official BYOK (Agnes, OpenAI, Anthropic, Gemini, DeepSeek, Custom). Server-only reversals (chat.qwen.ai WAF, QwenCloud umid, LongCat H5guard, GeminiWeb cookies) are excluded — they need Python/TLS-impersonation backends, and TryingOpen already serves the same Qwen family on-device. | No backend of our own; every listed provider answers a plain HTTPS POST from the phone. |
| ADR‑11 | **Autonomous XML tool agent** (`NebiansAgentRunner` + `AgentProtocol`, ported from Nebians `runner.py`/`protocol.py`): tools advertised as Hermes `<tool_call>` schemas, results fed back as `<tool_response>`, loop capped at 6 with repeat detection. Routing is offline → Nebians → legacy cloud; agents prefer native cloud → Nebians XML → offline heuristic. | Scraped models have no native `tools` parameter; the protocol is in prose and execution stays local/auditable. |
| ADR‑12 | **Zero-shadow UI**: `UsCard` elevation 0, composer is a bordered single-line field (0 elevation), FABs flattened. | Explicit user preference. |
| ADR‑6 | **Offline‑first with confidence escalation**: offline provider returns `confidence`; if `< 0.7` **and** cloud is enabled → escalate | Privacy default, quality when the user allows it. |
| ADR‑7 | **StateFlow + immutable UiState data classes**, one per screen | Predictable recomposition, easy tests. |
| ADR‑8 | **CI‑only builds** via GitHub Actions with a committed default keystore | The owner never builds locally. See §7. |
| ADR‑9 | **Max 500–600 lines per file**, hard rule. Split by responsibility. | Reviewability. Current largest file is tracked in §9. |

---

## 3. Module / Package Map

```
com.us.copilot
├── UsApplication.kt              @HiltAndroidApp, WorkManager config
├── MainActivity.kt               edge-to-edge host + biometric gate
├── core/
│   ├── model/                    pure Kotlin domain models + enums
│   └── util/                     Outcome<T>, time formatting, text utils
├── data/
│   ├── local/
│   │   ├── db/                   UsDatabase, Converters
│   │   ├── entity/               Room entities
│   │   ├── dao/                  DAOs (Flow-returning)
│   │   └── crypto/               DatabaseKeyProvider, SecureStore
│   ├── settings/                 SettingsRepositoryImpl (DataStore + SecureStore)
│   └── repository/               Profile / Memory / CheckIn / Analysis repo impls
├── domain/
│   ├── repository/               repository interfaces
│   └── usecase/                  one class per use case
├── ai/
│   ├── LlmProvider.kt            THE abstraction
│   ├── LlmRouter.kt              offline → cloud escalation policy
│   ├── model/                    ToneAnalysis, RephraseSet, PatternReport, Embedding
│   ├── nebians/                  Nebians fleet: catalog, Ktor clients, dispatcher, provider, XML agent
│   ├── offline/                  OfflineProvider, CactModelLoader (.cact), rules engines
│   └── cloud/                    CloudProvider (Ktor, OpenAI-compatible)
├── pattern/                      PatternEngine: horsemen, triggers, cadence, repairs
├── share/                        ShareReceiverActivity (ACTION_SEND)
├── notification/                 UsNotificationListenerService (opt-in)
├── work/                         InsightRefreshWorker
├── security/                     BiometricGate
├── di/                           Hilt modules
└── ui/
    ├── theme/                    M3 color/type/shape, dynamic color
    ├── navigation/               destinations + NavHost
    ├── components/               reusable M3 pieces + state scaffolds
    ├── home/ onboarding/ profile/ timeline/ insights/ coach/ journal/ settings/
```

---

## 4. Data Models

**Profile** (`profiles`) — versioned, one row per version, `isActive` marks current.
`id, owner(ME|PARTNER), name, attachmentStyle, loveLanguages[ranked 5], conflictStyle,
triggers[], soothers[], big5(o,c,e,a,n 0..100), stressPatterns[], commPreferences[],
version, isActive, updatedAt`

**Memory** (`memories`) — the atom of the app.
`id, text, emotion, intensity(1..5), timestamp, source(SHARE|JOURNAL|MANUAL|NOTIFICATION),
speaker(ME|PARTNER|BOTH), tags[], isUnresolved, resolvedAt, embedding(FloatArray?), appPackage`

**CheckIn** (`check_ins`) — `id, date, mood(1..5), energy(1..5), connection(1..5), note, gratitude`

**AnalysisRecord** (`analyses`) — cached AI output: `id, memoryId?, inputHash, toneJson,
rephraseJson, provider, confidence, createdAt`

Enums live in `core/model/Enums.kt`: `AttachmentStyle`, `LoveLanguage`, `ConflictStyle`,
`Emotion`, `MemorySource`, `Speaker`, `Horseman`, `RephraseStyle`, `ProfileOwner`.

---

## 5. AI Abstraction

```kotlin
interface LlmProvider {
    val id: ProviderId
    suspend fun isAvailable(): Boolean
    suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis>
    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet>
    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport>
    suspend fun embed(text: String): Outcome<FloatArray>
}
```

- **OfflineProvider** — deterministic on‑device engine (lexicon + NVC transformer + Gottman
  heuristics). It attempts to load `assets/models/needle2.cact` through `CactModelLoader`; when the
  file is absent it runs in `RULES_ONLY` mode and reports a lower `confidence`. Dropping a real
  14 MB `.cact` file into `app/src/main/assets/models/` upgrades it with **no code change**, and
  the loader exposes the tool‑calling surface (`ToolRegistry`) the model can invoke.
- **CloudProvider** — generic **OpenAI‑compatible** Ktor client. `baseUrl`, `apiKey`, `modelName`
  all come from secure settings at call time. **No key is ever in code, Git, or logs.**
- **LlmRouter** — runs offline first; if `confidence < 0.7` and `cloudEnabled`, re‑runs on cloud and
  returns the better result, tagging which provider answered so the UI can show it.

---

## 6. Privacy Model

| Data | Where | Protection |
|------|-------|------------|
| Memories, profiles, check‑ins, analyses | Room DB `us.db` | **SQLCipher AES‑256**, key in EncryptedSharedPreferences (Android Keystore) |
| API key / base URL | `secure_settings.xml` | **EncryptedSharedPreferences** AES256‑GCM |
| Toggles, theme, onboarding flags | DataStore `us_settings` | Non‑sensitive only |
| Network | Cloud provider only, **off by default** | Explicit toggle + visible banner while enabled |
| Backups | Disabled | `allowBackup=false`, `dataExtractionRules` excludes everything |
| App lock | Biometric / device credential | Optional, gates the whole UI at `MainActivity` |
| Notification Listener | Opt‑in, rationale screen, package allow‑list (IG/Messenger) | Text never leaves device; user can wipe captures |

---

## 7. CI/CD — GitHub Actions is the ONLY build path

**You never build locally.** Push → Actions → download APK.

Workflow: `.github/workflows/release.yml` — the single build workflow. There is no separate CI
workflow; tests and the file-size guard run inside this same job before the APK is assembled.
1. Triggers: `push` to **any branch** filtered on `app/**`, `gradle/**`, `*.gradle.kts`,
   `gradle.properties`, `settings.gradle.kts`, `keystore.properties`, `scripts/**` and the workflow
   file itself; any `v*` tag; and `workflow_dispatch` from any branch.
2. `ubuntu-latest`, **JDK 17** (Temurin), Android SDK, Gradle cache via `gradle/actions/setup-gradle`.
3. **Keystore**: `keystore.properties` + `app/keystore/release.jks` are committed with default
   passwords (personal‑use project). If they are missing, CI **generates** them with `keytool`
   using the same defaults, so signing always works. Env vars
   `US_STORE_FILE / US_STORE_PASSWORD / US_KEY_ALIAS / US_KEY_PASSWORD` override the defaults if you
   later add repo secrets.
4. Builds **one** artifact: `gradle assembleRelease` (APK, not AAB).
5. APK is signed by Gradle's `signingConfigs.release`.
6. Renamed to `Us-v{versionName}-{versionCode}-{shortSha}-{yyyy-MM-dd}-release.apk`.
7. Uploaded as a workflow artifact; on tag push also attached to a GitHub Release.
8. Logs print APK **path, size and SHA‑256**.

> The build uses `gradle/actions/setup-gradle` with a pinned Gradle version, so no
> `gradle-wrapper.jar` binary needs to live in the repo.

**Getting your APK:** GitHub → *Actions* → latest **Release APK** run → *Artifacts* → `us-release-apk`.

---

## 8. How to run / build

- **Normal path:** `git push` → Actions → artifact. Nothing else.
- Optional local: Android Studio Koala+, JDK 17, `./gradlew installDebug` (debug is signed with the
  standard Android debug key).
- Quality: `gradle lint testDebugUnitTest`.

---

## 9. Current Progress

- [x] AGENTS.md (day 1) + README with CI badge
- [x] Gradle Kotlin DSL, version catalog, R8/Proguard, signing config with env/property fallback
- [x] GitHub Actions `release.yml` (sign + rename + sha256 + artifact + release)
- [x] Theme, dynamic color, light/dark, edge‑to‑edge, adaptive icon + splash
- [x] Encrypted Room (SQLCipher) + DAOs + repositories + DataStore/SecureStore
- [x] `LlmProvider` abstraction, OfflineProvider (+ .cact loader), CloudProvider (Ktor), LlmRouter
- [x] A. Psychological Profiles (onboarding quiz, versioned profiles, editor)
- [x] B. Memory & Pattern engine (timeline, search/filter, insights dashboard)
- [x] C. Communication Coach (share receiver, NVC rephrase, Before‑You‑Send, notification listener)
- [x] D. Journal & daily check‑ins, repair starters
- [x] Settings: AI provider, biometric lock, notification access, data wipe
- [x] Unit tests for PatternEngine / NVC rephraser / LlmRouter escalation

**File size guard:** no source file exceeds 600 lines (checked in CI by `scripts/check_file_size.sh`).

### Changelog
- **2026-09-05** — Routing fix: explicit selection wins. `LlmRouter` used to run offline first and only escalate below 0.7 confidence, so the offline engine (often 0.62–0.74 confident) answered and the chosen Nebians model never fired. Now a usable Nebians selection answers first with offline as fallback; cloud-off means fully offline. The coach model sheet gained its own cloud-AI switch (was Settings-only), and Nebians failures log to logcat (`NebiansProvider`/`NebiansAgent`) instead of vanishing into silent fallback.
- **2026-09-05** — Nebians fleet integration + zero-shadow UI. New `ai/nebians/` package: `NebiansCatalog` (15 providers / ~50 models with vision/thinking/file/reasoning capability flags), device-native Ktor clients (`NebiansOfficialClient` for OpenAI/Anthropic/Gemini wire formats incl. keyless pools, `TryingOpenClient` with quick/balanced/deep effort + 3×5 MB file upload, `GuestScraperClients` for K2Think/Poolside/Motif/Yqcloud/ChatJimmy), `NebiansDispatcher`, `NebiansProvider` (tone/rephrase/patterns single-shot), `AgentProtocol` (Hermes/Qwen-fn/fence/JSON parser) and `NebiansAgentRunner` (autonomous 6-turn XML tool loop with repeat guard). Settings stores provider/model/effort/temperature/maxTokens in DataStore and keys in SecureStore; coach has a model bottom sheet + attach button (file-capable models only); `LlmRouter` escalates offline → Nebians → legacy cloud and `AgentCoordinator` prefers native cloud → Nebians XML → offline. Composer is now single-line with a hairline border and 0 elevation; `UsCard` and FABs flattened. Tests: `AgentProtocolTest`, `NebiansCatalogTest`, `NebiansAgentRunnerTest`, extended `LlmRouterTest`.
- **2026-09-05** — Initial end‑to‑end implementation of all modules A–D, CI/CD, privacy layer.
- **2026-09-05** — Workflow activated at `.github/workflows/release.yml`; triggers on any branch,
  path-filtered to app/gradle/scripts changes. Separate `ci.yml` removed — one workflow only.
- **2026-09-05** — Notification capture, agentic AI, onboarding art, app-lock hardening.
  **Capture**: new `captured_notifications` table (schema v2, real migration — destructive
  fallback stays off because journals cannot be recreated). The watch list is chosen by the
  user from installed launchable apps via a `<queries>` manifest declaration rather than
  `QUERY_ALL_PACKAGES`, and starts empty, so enabling capture alone captures nothing.
  Entries store `sharedWithAi = false`; handing text to a model is a separate per-entry
  action. History screen supports filter, per-row share/delete, stop-sharing-all and
  clear-all, with retention capped at 500.
  **Agent**: `ai/agent` adds a tool contract and seven tools (six read, one write).
  `CloudAgentRunner` runs a real multi-turn tool-calling loop capped at six iterations;
  `OfflineAgentRunner` executes the same tools on a deterministic keyword plan and is
  read-only — it is grounded retrieval, not autonomy, because the bundled `.cact` model
  cannot tool-call. `read_shared_notifications` is structurally unable to see unshared
  captures. Replies expose the tool trail.
  **Onboarding**: Compose-drawn animated illustrations, animated progress, section labels.
  **Fixes**: 'Today' tab was dead (popUpTo targeted the popped ONBOARDING start
  destination); 'Not sure yet' was unselectable (`takeIf { it != UNKNOWN }` nulled it);
  theme selector and radio labels overflowed; **BiometricGate returned success when no
  authenticator was enrolled, silently unlocking a locked app** — it now reports typed
  states and the lock screen explains them.
- **2026-09-05** — UI/UX overhaul. Design system now derives from the launcher icon
  (rose `#8F4A5C`, blush `#FFD9E0`) rather than drifting from it; added `UsGradients`
  and `BrandHero`/`QuietGroup`/`BrandBackdrop`. Typography moved to **Poppins** via the
  Google Fonts downloadable-font provider (`font_certs.xml`), with negative tracking on
  display/headline sizes and a graceful fallback to system sans when the provider is
  unavailable, preserving offline-first. Dynamic colour now defaults **off** so the
  wallpaper cannot repaint the brand. The Coach was rebuilt as a real **chat**
  (`ChatModels`/`ChatBubbles`/`ChatComposer`): a scrolling transcript of user drafts,
  coach lines, tone and rephrase cards, an animated typing bubble, inline retryable
  errors, a floating IME-aware composer, and starter chips. Home gained a gradient hero.
  CI actions bumped to Node-24-native majors to clear the deprecation warnings.
- **2026-09-05** — Fixed R8 minify failure: `androidx.security:security-crypto` pulls in Tink,
  which references Error Prone annotations that are compile-time only. Added `-dontwarn` for
  `com.google.errorprone.annotations.**` plus Tink keep rules. Also added keep rules for
  serialized enums (`values()`/`valueOf()` are needed to decode cloud responses) and for
  WorkManager workers, which are instantiated reflectively. Cleared all four compiler warnings:
  AutoMirrored `Send`/`Notes` icons, `@OptIn(ExperimentalSerializationApi)` for `explicitNulls`,
  and `@OptIn(FlowPreview)` for `debounce {}`.
- **2026-09-05** — Fixed release compile errors: `ScaleSlider` was being called with a trailing
  lambda, which Kotlin bound to the last parameter (`valueLabels`) instead of `onValueChange`. The
  five OCEAN sliders are now one shared `BigFiveSliders` composable using named arguments, so the
  bug cannot recur in two places. Also removed an illegal top-level import of
  `androidx.compose.foundation.layout.weight`, which is internal to `RowScope`/`ColumnScope`.
