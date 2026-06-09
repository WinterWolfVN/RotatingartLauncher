# UI Implementation Notes

Concise project snapshot for landscape UI, routing, and MVVM.

## 1) Landscape Baseline

- Launcher activities are locked to landscape in `app/src/main/AndroidManifest.xml`:
  - `InitializationActivity`
  - `MainActivityCompose`
  - `GameActivity`
  - `ControlEditorActivity`
  - `CrashReportActivity`
  - `SponsorsActivity`
- `BaseActivity` also enforces `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` in `app/src/main/java/com/app/ralaunch/core/ui/BaseActivity.kt`.
- Compose screens are implemented as landscape-first split panes (rail + content, list + detail).

## 2) Routing Snapshot

- Route model: `app/src/main/java/com/app/ralaunch/core/navigation/NavRoutes.kt`
  - `Screen` sealed class defines route strings and parameterized screens.
  - `NavDestination` enum defines main rail tabs.
- Route state: `app/src/main/java/com/app/ralaunch/core/navigation/AppNavHost.kt`
  - `NavState` stores `currentScreen`, back stack, and navigation direction.
- Route helpers: `app/src/main/java/com/app/ralaunch/core/navigation/NavigationExtensions.kt`
  - typed helpers (`navigateToGames`, `navigateToSettings`, etc.)
  - `handleBackPress()` fallback policy.
- Route rendering:
  - `app/src/main/java/com/app/ralaunch/feature/main/ui/MainApp.kt`
  - `app/src/main/java/com/app/ralaunch/feature/main/ui/MainActivityCompose.kt`

## 3) MVVM Snapshot

- Main feature contract:
  - `app/src/main/java/com/app/ralaunch/feature/main/contracts/MainContracts.kt`
  - `MainUiState`, `MainUiEvent`, `MainUiEffect`.
- Main orchestration VM:
  - `app/src/main/java/com/app/ralaunch/feature/main/vm/MainViewModel.kt`
  - state via `MutableStateFlow`, effects via `MutableSharedFlow`, event reducer via `onEvent`.
- Reusable feature VM example:
  - `app/src/main/java/com/app/ralaunch/feature/settings/vm/SettingsViewModel.kt`
  - same state/event/effect pattern drives settings flows inside the app module.
- Android-specific side effects stay in wrapper composables (for example `SettingsScreenWrapper` using activity result APIs and file pickers).

## 4) Practical Placement Rules

- Put reusable UI logic in existing `core` or `feature` packages inside `app`.
- Put Android/runtime integration in wrapper/activity/platform files.
- Route contract changes happen in `core/navigation` first; main page rendering and dependency wiring happen in `feature/main`.
- Keep role-based subpackages consistent:
  - `.../ui` for Compose screens, wrappers, and UI helpers
  - `.../vm` for ViewModels and state coordinators
  - `.../model` for feature-owned models and DTO-style data shapes
  - `.../contract` for contracts and interfaces
  - `.../service` for concrete services, managers, and repository implementations
- Prefer existing component families before adding new top-level UI patterns.

## 5) MD3 + Glass Design Baseline

- Theme root:
  - `app/src/main/java/com/app/ralaunch/core/theme/Theme.kt` (`RaLaunchTheme`)
  - dynamic light/dark color scheme generated from seed color.
- Tokens:
  - colors: `MaterialTheme.colorScheme.*` and `RaLaunchTheme.extendedColors`
  - typography: `app/src/main/java/com/app/ralaunch/core/theme/Typography.kt`
  - shape system: `app/src/main/java/com/app/ralaunch/core/theme/Shape.kt`
- Glass primitives:
  - `app/src/main/java/com/app/ralaunch/core/ui/component/GlassComponents.kt`
  - prefer `GlassSurface` / `GlassSurfaceRegular` for blur-backed panels.
- Existing visual language references:
  - `app/src/main/java/com/app/ralaunch/feature/main/ui/AppNavigationRail.kt`
  - `app/src/main/java/com/app/ralaunch/feature/main/ui/GameCard.kt`
  - `app/src/main/java/com/app/ralaunch/feature/main/ui/GameDetailPanel.kt`
  - `app/src/main/java/com/app/ralaunch/feature/main/ui/SplashOverlay.kt`

## 6) Good UI Checklist (Landscape Launcher)

- Information architecture:
  - clear primary action and grouped secondary actions.
  - split panes for dense management tasks.
- Visual hierarchy:
  - use surface container levels and typography scale, not random font/alpha changes.
- Interaction states:
  - implement loading, empty, error, pressed, selected, and destructive-confirm states.
- Accessibility and readability:
  - ensure contrast over image/video backgrounds.
  - provide content descriptions and maintain practical touch target size.
- Performance:
  - keep file/network/storage operations out of Composables.
  - use stable item keys in lazy containers and avoid unnecessary recomposition churn.
