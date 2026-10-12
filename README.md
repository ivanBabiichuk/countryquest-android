# CountryQuest

An Android geography quiz about flags, capitals, and countries.

CountryQuest is a personal product being built with Kotlin and Jetpack Compose. The first release is planned around short, ten-question rounds, local statistics, and a REST API with offline support.

> **Status: initial project setup.** The repository currently contains the Android Studio starter Activity, Compose theme, and template tests. Gameplay, API integration, persistence, and statistics are not implemented yet.

## Planned v1.0 experience

- **Guess the Flag:** identify a country from its flag.
- **Guess the Capital:** choose a country's capital.
- **Guess the Country:** identify a country from its capital, region, and languages.
- Ten questions per round, four answer options, and one correct answer.
- Answer feedback and a short country fact.
- Results and statistics for completed rounds.
- Country data updates through a REST API, with a local seed catalog and offline flags.

The four main screens are **Home**, **Quiz**, **Results**, and **Statistics**. App UI is in English. Accounts, ads, and a custom backend are outside the v1.0 scope.

## Technology

**Currently configured:** Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL, a version catalog, and template JUnit / Android instrumentation tests.

**Planned:** ViewModel with Coroutines and Flow, Navigation Compose, Hilt, Room, Retrofit / OkHttp, Coil, DataStore, MockK, and MockWebServer.

Dependency versions are maintained in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Run locally

1. Open this repository's root directory in Android Studio.
2. Install the Android SDK required by `app/build.gradle.kts` using SDK Manager. The current project uses compile / target SDK **37** and minimum SDK **24** (Android 7.0).
3. Use a Gradle JDK compatible with the configured Android Gradle Plugin and Gradle Wrapper. Android Studio's bundled JDK is the starting point; check compatibility if sync reports a mismatch. Java source compatibility is not the same as the JDK required to run Gradle.
4. Let Gradle sync finish, then run the `app` configuration on an emulator or device with API 24 or higher.

The current starter app needs no API credentials. First-time dependency downloads require an internet connection.

From the project root, with a compatible JDK and Android SDK configured:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Instrumentation tests require a running emulator or connected device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

On Windows, use `gradlew.bat`. These commands are provided for local verification; this README does not claim a successful build or CI run. Existing tests are template examples, not coverage of the planned quiz features.

## Architecture direction

Start with one `:app` module and clear package boundaries:

```text
io.github.ivanbabiichuk.countryquest
├── app/           # Application, Activity, dependency injection
├── core/          # Shared UI and utilities
├── data/          # API, Room, seed catalog, mappers, repository implementations
├── domain/        # Models, quiz engine, use cases, repository interfaces
├── feature/       # Home, Quiz, Results, Statistics
└── navigation/    # Routes and navigation host
```

This is the target structure, not the current starter layout. Code dependencies point **Presentation → Domain ← Data**; Hilt will connect implementations to domain interfaces.

Planned data flow:

```text
Country API → DTO validation / mapping → Repository → Room → Flow → ViewModel → Compose
Local seed ─────────────────────────────────────────→ Room
```

Room will provide the UI's local source of data. Failed updates must preserve the last usable catalog. Active rounds will keep a question snapshot so synchronization cannot change an answer mid-game. Offline flags must use bundled assets or persistent storage, rather than relying only on an evictable image cache.

A completed round and its ten answers will be saved atomically. Answer history will support a future Practice Your Mistakes mode.

## Design and planning

- [Product plan and architecture in Penpot](https://design.penpot.app/#/workspace?team-id=17193a1f-939a-80b5-8008-c6314074a7a3&file-id=52a5041f-08a1-80f1-8008-c63e8a28e875&page-id=52a5041f-08a1-80f1-8008-c63e8a28e876)
- [v1.0 screen designs in Penpot](https://design.penpot.app/#/workspace?team-id=17193a1f-939a-80b5-8008-c6314074a7a3&file-id=52a5041f-08a1-80f1-8008-c63e8a28e875&page-id=d938f842-ed1c-801a-8008-c647e0a1d33c)

Penpot links may require sign-in and file access. Design mockups do not represent implemented screens.

The initial estimate is ten working days, including dedicated time for API integration, synchronization, and network testing. It is a planning estimate, not a promised publication date.

## Roadmap

| Version | Planned scope |
| --- | --- |
| 1.0 | Three quiz modes, results, statistics, API synchronization, offline support |
| 1.1 | Continent filters and difficulty |
| 1.2 | Adventure progression, levels, XP, and stars |
| 1.3 | Country collection and discovery progress |
| 1.4 | Daily Challenge and achievements |
| 1.5 | Practice Your Mistakes |

## Data sources and attribution

The planned providers are [countries.dev](https://countries.dev/docs) for country data and [FlagCDN / Flagpedia](https://flagpedia.net/download/api) for flags. Their API contracts, dataset suitability, and redistribution / attribution requirements must be checked when integrating them. Maintain the required notices with any bundled data and flag assets before release.

## License

A source-code license has not been selected yet. Third-party libraries, data, and flag assets remain subject to their respective licenses. This repository does not grant additional rights to those materials.
