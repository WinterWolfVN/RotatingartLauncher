# RotatingartLauncher Architecture Notes

Use this reference after running the skill's `scripts/project_map.sh`.

## Gradle Shape

- `settings.gradle` includes only `:app`.
- `app` is the Android application module (`com.app.ralaunch` namespace).

## Startup Chain

1. `app/src/main/AndroidManifest.xml` declares `.feature.init.ui.InitializationActivity` as launcher entry.
2. `app/src/main/java/com/app/ralaunch/RaLaunchApp.kt` initializes:
   - density adapter
   - Koin DI
   - theme defaults
   - crash logging
   - patch extraction/install
3. `app/src/main/java/com/app/ralaunch/feature/main/ui/MainActivityCompose.kt` hosts the main Compose experience.

## Dependency Injection Boundaries

- `app/src/main/java/com/app/ralaunch/core/di/KoinInitializer.kt` starts Koin.
- `app/src/main/java/com/app/ralaunch/core/di/AppModule.kt` registers repositories, services, and viewmodels.
- `app/src/main/java/com/app/ralaunch/core/di/contract` defines launcher contracts.
- `app/src/main/java/com/app/ralaunch/core/di/service` provides concrete Android-backed implementations.

## High-Level Code Zones

- `app/src/main/java/com/app/ralaunch/core`:
  platform/runtime internals, repositories, navigation, theme, and utility managers.
- `app/src/main/java/com/app/ralaunch/feature`:
  product feature verticals (`main`, `game`, `controls`, `gog`, `installer`, `patch`, `init`, `settings`, `filebrowser`, `announcement`, `crash`, `sponsor`).
- `patches`:
  patch assets used by patch-management logic.
- `app/src/main/assets`:
  bundled controls, patches, and runtime support files used during init and launch flows.

## Native and Vendored Surface

- `app/src/main/cpp/main` and `app/src/main/cpp/dotnethost` are first-party runtime/native integration layers.
- `app/src/main/cpp/SDL`, `app/src/main/cpp/FNA3D`, `app/src/main/cpp/gl4es`, `app/src/main/cpp/FAudio` are large external trees.
- Default architecture summaries should separate these vendored trees from first-party code.

## Practical Mapping Heuristics

- Prefer on-disk structure over README tree snippets if they differ.
- Ignore generated directories (`build/`, `.gradle/`, `.kotlin/`) during architecture analysis.
- Confirm process boundaries from manifest declarations (`:game`, `:launcher`) before describing runtime flows.
- For change placement questions, identify the package boundary first (`core` vs `feature` vs `cpp` vs `patches`), then the concrete folder.

## Fast Commands

```bash
# Snapshot architecture
scripts/project_map.sh .

# List top-level feature folders
find app/src/main/java/com/app/ralaunch/feature -mindepth 1 -maxdepth 1 -type d | sort

# List top-level core folders
find app/src/main/java/com/app/ralaunch/core -mindepth 1 -maxdepth 1 -type d | sort
```
