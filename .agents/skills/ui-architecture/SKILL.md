---
name: ui-architecture
description: Project-specific UI implementation guideline for RotatingartLauncher. Use when adding, refactoring, or reviewing launcher UI so changes stay aligned with the app's landscape-first design, custom NavState/Screen routing, and MVVM state-event-effect patterns across the current app-only codebase.
---

# UI Architecture

## Quick Start

1. Confirm where the UI belongs:
   - `app/src/main/java/com/app/ralaunch/core/...` for app-wide navigation, theme, reusable components, dialogs, and infrastructure.
   - `app/src/main/java/com/app/ralaunch/feature/...` for feature-specific UI, viewmodels, wrappers, and user flows.
   - Within those trees, place Compose code under `.../ui`, ViewModels under `.../vm`, feature-owned models under `.../model`, contracts/interfaces under `.../contract`, and concrete implementations under `.../service` where applicable.
2. Confirm landscape constraints before layout work (manifest + base activity).
3. Follow existing routing contracts (`Screen`, `NavDestination`, `NavState`) instead of introducing a parallel navigation stack.
4. Follow state/event/effect MVVM flow before adding mutable local state.
5. Keep visual design aligned with this repo's MD3 + glass style (`RaLaunchTheme`, `GlassSurface`, motion and state feedback patterns).

## Mandatory Pre-Modification Context

Before proposing UI changes, read these files first:

1. `app/src/main/AndroidManifest.xml`
2. `app/src/main/java/com/app/ralaunch/core/ui/BaseActivity.kt`
3. `app/src/main/java/com/app/ralaunch/feature/main/ui/MainActivityCompose.kt`
4. `app/src/main/java/com/app/ralaunch/feature/main/ui/MainApp.kt`
5. `app/src/main/java/com/app/ralaunch/core/navigation/NavRoutes.kt`
6. `app/src/main/java/com/app/ralaunch/core/navigation/AppNavHost.kt`
7. `app/src/main/java/com/app/ralaunch/core/navigation/NavigationExtensions.kt`
8. `app/src/main/java/com/app/ralaunch/feature/main/contracts/MainContracts.kt`
9. `app/src/main/java/com/app/ralaunch/feature/settings/vm/SettingsViewModel.kt`
10. `app/src/main/java/com/app/ralaunch/feature/main/ui/GameListContent.kt`
11. `app/src/main/java/com/app/ralaunch/core/theme/Theme.kt`
12. `app/src/main/java/com/app/ralaunch/core/theme/Color.kt`
13. `app/src/main/java/com/app/ralaunch/core/theme/Shape.kt`
14. `app/src/main/java/com/app/ralaunch/core/theme/Typography.kt`
15. `app/src/main/java/com/app/ralaunch/core/ui/component/GlassComponents.kt`
16. `app/src/main/java/com/app/ralaunch/feature/main/ui/AppNavigationRail.kt`
17. `app/src/main/java/com/app/ralaunch/feature/settings/ui/SettingsScreenWrapper.kt`
18. `app/src/main/java/com/app/ralaunch/feature/filebrowser/ui/FileBrowserScreenWrapper.kt`

For deeper project context, load:
- `../map-project-structure/references/rotatingart-architecture.md`
- `references/ui-implementation-notes.md`

## Landscape Rules

1. Keep screens landscape-first. All launcher activities are locked to `sensorLandscape`; do not add portrait-only UI assumptions.
2. Prefer split-pane `Row` layouts for high-information screens (list + detail).
3. Reuse existing layout proportions when possible (for example `0.62/0.38` or `0.65/0.35`) before introducing new ratios.
4. Validate both landscape widths commonly seen on tablets and phones; avoid fixed widths that break at smaller heights.

## Routing Notes (Brief)

1. Routing is custom and state-driven (`NavState`), not Navigation Compose `NavController`.
2. Add new routes to `Screen` first (`NavRoutes.kt`).
3. If the screen is a primary rail tab, add/update `NavDestination` too.
4. Render route content in `MainApp` page routing and wire Android-specific dependencies in `MainActivityCompose` wrapper slots.
5. Keep back behavior consistent with `handleBackPress()`:
   - Pop sub-screen stack first.
   - If on a non-games root destination, return to games before exiting.

## MVVM Notes (Brief)

1. Prefer unidirectional data flow:
   - `UiState` as `StateFlow`
   - `UiEvent` input to ViewModel
   - `UiEffect` for one-time side effects
2. Keep domain/business logic in repository/use-case layers, not Composables.
3. Keep Android integration in wrapper/activity/platform files:
   - activity result launchers
   - permission flows
   - intents/toasts/navigation side effects
4. Reuse existing feature viewmodels and app-level contracts before adding new state holders.
5. Avoid new legacy MVP-style code for new UI work; align with existing Compose + ViewModel paths.
6. Keep package placement predictable:
   - Compose screens, wrappers, and UI helpers in `.../ui`
   - ViewModels and state reducers in `.../vm`
   - feature-owned models, DTOs, and state/data shapes in `.../model`
   - contracts and interfaces in `.../contract`
   - concrete services, managers, and repository implementations in `.../service`

## MD3 Design Rules

1. Always render feature UI under `RaLaunchTheme`; do not create parallel theming systems.
2. Prefer semantic tokens over hard-coded values:
   - color: `MaterialTheme.colorScheme.*` and `RaLaunchTheme.extendedColors`
   - type: `MaterialTheme.typography.*`
   - shape: `MaterialTheme.shapes.*` / `AppShapes`
3. Use MD3 surface layering for hierarchy (`surfaceContainerLow` -> `surfaceContainerHighest`) instead of arbitrary alpha overlays.
4. Reuse existing glass primitives for blurred panels (`GlassSurface`, `GlassSurfaceRegular`) rather than custom blur stacks.
5. Keep state communication explicit:
   - selected, pressed, disabled, focused states must be visually distinct
   - actions should use MD3 components (`Button`, `AssistChip`, `DropdownMenu`, etc.) before custom controls
6. Maintain consistency with current motion language:
   - short transitions for feedback (`150-300ms`)
   - medium transitions for page/surface changes (`250-450ms`)
   - spring for press/select responses when needed
7. Keep one dominant accent per screen; avoid mixing unrelated highlight colors that reduce visual hierarchy.

## Good UI Quality Bar

1. Build for task clarity:
   - one primary action per pane
   - keep secondary actions grouped and visually quieter
2. Build for scanability in landscape:
   - split-pane composition for dense workflows
   - avoid long single-column forms when side-by-side grouping is possible
3. Build for resilient states:
   - loading, empty, and error states must exist for each data-driven screen
   - destructive actions require clear confirmation
4. Build for usability:
   - touch targets should remain comfortable in landscape (`~48dp` minimum)
   - long labels should degrade gracefully (ellipsis/wrapping rules)
5. Build for accessibility:
   - meaningful `contentDescription` for icons/images
   - sufficient contrast for text and controls over video/image backgrounds
6. Build for performance:
   - keep expensive operations out of Composables
   - use stable keys in lazy lists/grids
   - avoid heavy recomposition loops in animated screens

## Workflow

1. Identify target screen type:
   - Main tab (`NavDestination`)
   - Sub-screen (`Screen` with back stack)
   - Dialog/overlay in current screen
2. Reuse existing composables/components before creating new ones (`AppNavigationRail`, `GameListContent`, `SettingsScreen`, `FileBrowserScreen`).
3. Add/extend `UiState`, `UiEvent`, `UiEffect` contracts before wiring UI interactions.
4. Apply MD3 token decisions (color/type/shape/surface hierarchy) before finalizing visuals.
5. Keep platform-specific code in wrappers/activities/platform packages and reusable UI/state in existing `core` or `feature` packages inside `app`.
6. Place new files by role: Compose code in `.../ui`, ViewModels in `.../vm`, models in `.../model`, contracts in `.../contract`, and concrete implementations in `.../service` unless the surrounding feature already has a stricter established layout.
7. Validate behavior:
   - route transitions
   - back behavior
   - state restoration after resume/config changes
   - landscape usability
   - interaction feedback (pressed/selected/disabled/loading/error)
   - contrast/readability on themed backgrounds

## Output Contract

When using this skill for implementation guidance, provide:

1. Target files and package boundaries (`core` vs `feature`, plus any wrapper/activity/platform splits).
2. Routing impact (`Screen`/`NavDestination`/`PageContent` changes).
3. MVVM contract impact (`UiState`/`UiEvent`/`UiEffect` + ViewModel updates).
4. Landscape layout impact (pane structure, width ratios, overflow handling).
5. MD3 design decisions (tokens/components/motion/surface layering).
6. UI quality checks (state coverage, usability, accessibility, performance).
7. Test/validation checklist (manual + automated where practical).
