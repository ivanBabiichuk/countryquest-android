# CountryQuest

Learn geography through short quizzes about flags, capitals, and countries.

CountryQuest is a personal Android product built with Kotlin and Jetpack Compose. It combines a REST API, a local Room catalog, reactive UI state, and a quiz engine in a single-module application with separate presentation, domain, and data packages.

> **Status: playable v1.0 prototype, under active development.** Three quiz modes, catalog synchronization, flag loading, and round results work. Completed rounds and answer snapshots are persisted in Room. Statistics and game history browsing are implemented. Guaranteed offline flag availability is still planned.

The app UI is in English. Accounts, ads, analytics, and a custom backend are outside the v1.0 scope.

## Gameplay

| Mode | Challenge |
| --- | --- |
| Guess the Flag | Identify a country from its flag. |
| Guess the Capital | Choose the capital of a named country. |
| Guess the Country | Identify a country from its capital, region, and languages. |

Each round contains **10 questions**, with **4 answer options** and one correct answer per question. Target countries do not repeat within a round. A correct answer earns one point; the final score ranges from 0 to 10. There is no timer or score penalty.

After answering, the screen reveals the correct option and country details. Answers cannot be changed, and the next question becomes available only after an answer. After a successful save, Results loads the game by its ID, shows the score, and offers **Play Again** or **Back to Home**.

The generator filters countries according to the selected mode. Countries with ambiguous shared capitals are excluded from capital-based questions. If fewer than ten eligible countries remain, the app shows an insufficient-catalog state instead of generating a partial round.

## Current features

| Area | Implemented behavior |
| --- | --- |
| Home | Mode selection, saved-country count, manual catalog update, and loading/error feedback. |
| Quiz | Three modes, progress, score, answer feedback, and country details. |
| Results | Reads a saved game by ID; shows score and mode, replay, loading, retry, and not-found states. |
| Game history storage | Atomic result/answer writes, duplicate-save protection, and ordered answer snapshots. Saved games can be opened from Statistics. |
| Statistics | Reactive overall and per-mode totals, accuracy, best scores, and game history with links to saved Results. |
| Country catalog | API download, validation, partial updates, Room persistence, and reactive observation. |
| Synchronization | Startup refresh attempt, manual retry, shared refresh state, and concurrent-request protection. |
| Flags | SVG preferred, PNG fallback, loading/error states, and retry. Answers stay disabled until the flag loads. |
| Tests | Domain, API, repository, Room, synchronization, ViewModel, Compose UI, and image-loading tests. |

## Architecture

The project uses one `:app` module with package boundaries. ViewModels expose UI state to Compose; domain models and repository interfaces separate gameplay from persistence and networking. Hilt supplies dependencies.

```text
io.github.ivanbabiichuk.countryquest
├── MainActivity.kt
├── app/
│   ├── CountryQuestApplication.kt   # Hilt application and Coil ImageLoader
│   ├── CountryQuestApp.kt
│   ├── AppViewModel.kt              # Startup catalog initialization
│   ├── di/                         # Network, database, repository, quiz bindings
│   └── sync/                       # Shared catalog synchronization state
├── data/
│   ├── remote/                     # Retrofit API and DTOs
│   ├── local/                      # Room database, DAO, entity, converter
│   ├── mapper/                     # DTO/domain/entity conversion
│   └── repository/                 # OfflineFirstCountryRepository
├── domain/
│   ├── model/                      # Countries, flags, questions, answers, modes
│   ├── quiz/                       # Round generation and eligibility rules
│   └── repository/                 # Repository contract and refresh results
├── feature/
│   ├── home/
│   ├── quiz/                       # Includes CountryFlagImage
│   ├── results/
│   └── statistics/
├── navigation/                     # Serializable routes and navigation host
└── ui/theme/                       # Compose theme
```

### Catalog and game data flow

```text
Startup / manual update
        ↓
CountrySyncCoordinator → CountryRepository.refreshCountries()
        ↓
Retrofit + OkHttp → Country DTOs → validation / mapping → Room upsert
                                                           ↓
                                              Room Flow → HomeViewModel → Home
                                                           ↓
                                              Catalog snapshot → QuizRoundGenerator
                                                           ↓
                                                   QuizViewModel → Quiz
                                                                       ↓
                                                       Save to Room → Results by game ID
```

Room is the source for catalog reads. Starting a round loads a snapshot from Room; subsequent catalog updates do not change that round's questions or answers.

`CountrySyncCoordinator` attempts automatic refresh once per process when initialized. Manual refresh remains available after success or failure. Duplicate refresh actions are ignored while a refresh is running. The repository also serializes refresh operations.

### Validation and update policy

- DTOs are mapped to app-owned models. The API's `capital` field is represented as a nullable string.
- Invalid required country fields reject that record. Optional fields are normalized, and invalid flag URLs are discarded.
- Identical records with the same country code are collapsed. Conflicting records with the same code are skipped.
- Valid records are upserted in a Room transaction, and the skipped-record count is exposed to the UI.
- Countries missing from an update are retained in the existing catalog.
- An entirely unusable response or a network, HTTP, parsing, or database failure preserves the previous catalog.

Record filtering applies after successful JSON decoding. A response that cannot be decoded is rejected as a whole.

### Flag loading and networking

Coil uses the application's shared OkHttp client and an SVG decoder. `CountryFlagImage` tries distinct, nonblank URLs in **SVG → PNG** order. If both fail, it shows **Try again**, which restarts from the first source.

Requests include a descriptive `CountryQuest/<version>` User-Agent with the repository URL. Debug builds log HTTP requests and response bodies, with `Authorization`, `Cookie`, and `Set-Cookie` headers redacted. The HTTP logging interceptor is omitted from release builds.

### Saved game history

`RoomGameHistoryRepository` stores each completed result and its answer snapshots through a single DAO transaction. A failed answer insert rolls back the whole game. Repeating the same save is a no-op; conflicting data for an existing ID is rejected. Answer positions are persisted and sorted on read. History does not depend on foreign keys to the live country catalog.

Database version 2 adds `game_results` and `game_answers` through an automatic migration from version 1, preserving saved countries. Both exported schemas are versioned.

The quiz exposes saving and save-error states and navigates only after a successful write. Results receives `gameId` through navigation and loads the record using a ViewModel with `SavedStateHandle`. Replay uses the mode stored in that record.

### Statistics and browsing history

Statistics observes saved games through one repository Flow. Overall and per-mode summaries include games played, correct/total answers, accuracy, and the best score among ten-question rounds. Accuracy is weighted by answer count; unplayed modes show no accuracy or record rather than an invented zero.

History is ordered by completion time descending, with game ID as a deterministic tie-breaker. Cards show mode, score, and the completion date in the device locale/time zone. Selecting a card opens Results by its saved ID without creating another game. System Back returns to Statistics; Back to Home returns home; Play Again starts the saved mode and clears the previous Statistics/Results destinations.

## Offline behavior and current limitations

The local catalog survives app restarts. After a successful download, text-based quizzes can use saved countries without a network connection, and a failed refresh does not remove them.

Full offline support is not finished:

- A fresh installation needs a successful catalog download; no bundled seed catalog exists yet.
- Flags use remote URLs and Coil caching. Cached images are not a guarantee that every flag will be available offline.
- If all flag sources fail, answers remain disabled. The user can retry or return Home; automatic question replacement is not implemented.
- Statistics currently loads all saved results and answer snapshots. History uses a lazy list for rendering, but database pagination and SQL aggregation are not implemented.
- If saving fails, retry reuses the same game ID and snapshot. Leaving the quiz discards the pending in-memory result; recovery of unsaved results after process death is not implemented.
- Active rounds are held in a ViewModel; restoration after process death is not implemented.
- Catalog freshness timestamps, scheduled synchronization, and app-level retry/backoff policies are not implemented.

## Technology

| Responsibility | Implementation |
| --- | --- |
| Language and build | Kotlin, Gradle Kotlin DSL, version catalog |
| UI | Jetpack Compose, Material 3 |
| State | ViewModel, Kotlin Coroutines, Flow / StateFlow |
| Navigation | Navigation Compose with serializable routes |
| Dependency injection | Hilt, KSP |
| Networking | Retrofit, OkHttp |
| JSON | kotlinx.serialization |
| Local database | Room, exported schemas |
| Images | Coil 3, OkHttp network fetcher, SVG decoder |
| Testing | JUnit 4, MockWebServer, AndroidX Test, Compose UI tests, test fakes |

Dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Room schemas live in [`app/schemas`](app/schemas). DataStore and MockK are not currently used.

## Run locally

```sh
git clone https://github.com/ivanBabiichuk/countryquest-android.git
cd countryquest-android
```

1. Open the repository root in Android Studio.
2. Install **Android SDK 37** using SDK Manager. The app targets SDK 37 and supports **API 24+** (Android 7.0+).
3. Let Gradle sync finish. The repository uses Gradle **9.5.0** and AGP **9.3.3**. Checked-in [daemon JVM criteria](gradle/gradle-daemon-jvm.properties) request **JDK 25**; allow toolchain provisioning or provide a matching installation. Java source/target compatibility is 11, which is separate from the Gradle daemon JDK.
4. Start an emulator or connect an Android device, then run the `app` configuration.
5. On first launch, allow the country catalog to download. Use the Home update/retry action if the request fails.

No API key or account is required for the configured country endpoint. Internet access is needed for initial dependency/toolchain downloads and the first country download.

Build from the repository root:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat`. Keep the local SDK path in an untracked `local.properties` file when needed.

### Quick demo

1. Open Home and wait for the saved-country count to appear.
2. Choose a mode and complete ten questions.
3. View the score and use Play Again.
4. Return Home and update the catalog; inspect `okhttp.OkHttpClient` in Logcat in a debug build.
5. After the catalog has been saved, disable networking and try a capital-based round to explore local-data behavior.

Flag questions may still need a network connection, as described above.

## Testing

Run local JVM tests:

```sh
./gradlew :app:testDebugUnitTest
```

Run Android instrumentation tests with an emulator or device connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Run static Android checks separately:

```sh
./gradlew :app:lintDebug
```

| Suite | Covered scenarios |
| --- | --- |
| JVM | Round generation, eligibility, DTO mapping, API parsing and failures, partial catalog updates, synchronization coordination, User-Agent, and logging behavior. |
| Android | Real Room reads/writes and rollback, Home/Quiz ViewModels, Home UI states, flag loading with real Coil decoders, game-history transactions and migration, save/retry behavior, Results loading/UI states, reactive statistics, and history-card selection. |
| Flag regression tests | SVG success, PNG fallback after HTTP or decoding failure, retry recovery, missing/duplicate URLs, country changes, and answer availability after recovery. |

API tests use MockWebServer. Flag tests supply controlled HTTP responses through a test interceptor while retaining the production image-loader factory and real SVG/PNG decoders. They do not depend on live Wikimedia or FlagCDN availability.

**Latest local verification — 18 October 2026:** 81 JVM debug tests and 81 Android tests passed on a Pixel 10 Pro emulator running Android 17 / API 37. On 16 October, four targeted networking tests also passed for release using a temporary Gradle init script to enable release unit tests; that script is not part of the repository. These counts include the original template tests and describe local verification, not CI or a coverage percentage. Lint is listed as a runnable check, not as a verified result of that run.

## v1.0 milestones

- [x] Persist completed rounds and answers atomically, with protection against duplicate saves.
- [x] Read Results from Room by game ID, with loading, error, and not-found states.
- [x] Implement statistics and a history browsing screen from saved results.
- [ ] Add a bundled country catalog and reliable offline flag assets.
- [ ] Track catalog freshness and complete synchronization behavior.
- [ ] Provide a recovery path when no flag source is available.
- [ ] Finish lifecycle recovery, accessibility, animations, and visual polish.
- [ ] Complete release validation, screenshots, signing, and third-party notices.

Answer history is intended to support Practice Your Mistakes in a later release.

## Roadmap

| Version | Scope |
| --- | --- |
| **1.0 — Quiz Edition** | Three quiz modes, results, statistics, API synchronization, offline support, and tests. **In progress.** |
| 1.1 — Explore | Continent filters and difficulty. |
| 1.2 — Adventure | Levels, XP, stars, and progression. |
| 1.3 — Discovery | Country collection and discovery progress. |
| 1.4 — Challenges | Daily Challenge and achievements. |
| 1.5 — Learning | Practice Your Mistakes. |

Future versions describe product direction, not committed release dates.

## Design and planning

- [Product planning and architecture in Penpot](https://design.penpot.app/#/workspace?team-id=17193a1f-939a-80b5-8008-c6314074a7a3&file-id=52a5041f-08a1-80f1-8008-c63e8a28e875&page-id=52a5041f-08a1-80f1-8008-c63e8a28e876)
- [v1.0 screen designs in Penpot](https://design.penpot.app/#/workspace?team-id=17193a1f-939a-80b5-8008-c6314074a7a3&file-id=52a5041f-08a1-80f1-8008-c63e8a28e875&page-id=d938f842-ed1c-801a-8008-c647e0a1d33c)

Planning labels are Ukrainian; application UI labels are English. Penpot may require sign-in and file access. Designs describe the target experience and may differ from the current prototype.

## Data sources and attribution

Country data is fetched from [`GET https://countries.dev/countries`](https://countries.dev/countries). See the [countries.dev documentation](https://countries.dev/docs) for the provider's API details.

Flag URLs are supplied by the country response and can point to different hosts, including [FlagCDN / Flagpedia](https://flagpedia.net/download/api) and Wikimedia Commons. The app does not assume every flag is hosted by the same service. Data normalization does not independently verify geographic accuracy or remote image availability.

Provider and asset attribution, licenses, and any modifications to bundled data must be documented before distributing the planned offline catalog and flags. In-app third-party notices are still a release task.

## License

No source-code license has been added to this repository yet. Third-party libraries, country data, and flag assets remain subject to their respective terms.
