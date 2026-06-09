# Subcomponent Implementation Details (RotatingartLauncher)

Load this file before editing any subcomponent.

## App Core (`app/src/main/java/com/app/ralaunch/core`)

### `core/common`

- Responsibility:
  app-wide managers and utilities used across features.
- Key files:
  `SettingsAccess.kt`, `GameLaunchManager.kt`, `ErrorHandler.kt`, `MessageHelper.kt`, `util/PatchExtractor.kt`, `util/AppLogger.kt`.
- Implementation details:
  convenience layer for settings reads, launch helpers, logging, dialogs, and utility functions that many features call directly.
- Change coupling:
  changes often propagate into `feature/main`, `feature/game`, settings flows, and startup/bootstrap logic.

### `core/di`

- Responsibility:
  initialize Koin and define repository/service/viewmodel ownership.
- Key files:
  `KoinInitializer.kt`, `AppModule.kt`, `contract/GameRepositoryV2.kt`, `contract/SettingsRepositoryV2.kt`, `service/GameRepositoryImpl.kt`, `service/SettingsRepositoryImpl.kt`.
- Implementation details:
  this is the canonical dependency graph for the app process; repository contracts and concrete services now live here.
- Change coupling:
  high; bad bindings break startup and feature construction across the launcher.

### `core/navigation`

- Responsibility:
  route model, nav state, destination mapping, and shared back-navigation helpers.
- Key files:
  `NavRoutes.kt`, `AppNavHost.kt`, `NavigationExtensions.kt`.
- Implementation details:
  `Screen`, `NavDestination`, and `NavState` drive the main Compose shell, settings/file-browser wrappers, and back behavior.
- Change coupling:
  high; route or helper changes affect `feature/main`, wrapper composition, and tab/sub-screen behavior.

### `core/platform/android`

- Responsibility:
  Android service and provider integration.
- Key files:
  `ProcessLauncherService.kt`, `provider/RaLaunchDocumentsProvider.kt`, `provider/RaLaunchFileProvider.kt`, `provider/RaLaunchWakeUpActivity.kt`.
- Implementation details:
  bridges launcher UI flows to platform services, file/document providers, and the `:launcher` process.
- Change coupling:
  service/authority changes require matching manifest and caller updates.

### `core/platform/network/easytier`

- Responsibility:
  EasyTier multiplayer, VPN/TUN lifecycle, diagnostics, and JNI coordination.
- Key files:
  `EasyTierVpnService.kt`, `EasyTierManager.kt`, `EasyTierConfigBuilder.kt`, `EasyTierJNI.kt`.
- Implementation details:
  handles foreground VPN service setup in the `:game` process and marshals configuration/state between Kotlin and native code.
- Change coupling:
  touches permissions, notifications, process boundaries, and interop.

### `core/platform/runtime`

- Responsibility:
  runtime preparation and .NET game launch execution.
- Key files:
  `GameLauncher.kt`, `AssemblyPatcher.kt`, `EnvVarsManager.kt`, `RendererLoader.kt`, `RendererRegistry.kt`, `ThreadAffinityManager.kt`, `dotnet/DotNetLauncher.kt`.
- Implementation details:
  central launch path that assembles env vars, renderer/runtime config, patch hooks, and native host startup.
- Change coupling:
  highest-risk area; ordering or config regressions can break all game launch paths.

### `core/theme` and `core/ui`

- Responsibility:
  app-wide Compose theme, haze/glass primitives, base activities, presenters, and dialogs.
- Key files:
  `theme/Theme.kt`, `theme/Color.kt`, `theme/Shape.kt`, `theme/Typography.kt`, `ui/BaseActivity.kt`, `ui/component/GlassComponents.kt`, `ui/dialog/SettingsDialogs.kt`.
- Implementation details:
  provides the launcher’s MD3 theme tokens, Haze state, fullscreen base activity behavior, and reusable dialog/component primitives.
- Change coupling:
  cross-feature visual, accessibility, and lifecycle impact.

## App Features (`app/src/main/java/com/app/ralaunch/feature`)

### `feature/init`

- Responsibility:
  first-run initialization and extraction flow.
- Key files:
  `ui/InitializationActivity.kt`, `ui/InitializationScreen.kt`.
- Implementation details:
  validates runtime readiness, requests permissions, extracts packaged assets, and routes into the main launcher.
- Change coupling:
  affects first-launch reliability and app bootstrap.

### `feature/main`

- Responsibility:
  launcher home orchestration, primary navigation shell, and main-state composition.
- Key files:
  `ui/MainActivityCompose.kt`, `ui/MainApp.kt`, `vm/MainViewModel.kt`, `contracts/MainContracts.kt`, `MainUseCases.kt`, `ui/AppNavigationRail.kt`, `ui/GameListContent.kt`.
- Implementation details:
  central state holder for game list, selection, launch/import/delete flows, background handling, and screen wrapper wiring.
- Change coupling:
  high; navigation, import, settings, downloads, and announcements converge here.

### `feature/game`

- Responsibility:
  in-game activity integration with SDL runtime and input bridges.
- Key files:
  `ui/legacy/GameActivity.kt`, `legacy/GamePresenter.kt`, `GameVirtualControlsManager.kt`, `input/GameTouchBridge.kt`, `input/GameImeHelper.kt`.
- Implementation details:
  owns the `:game` process activity, overlay controls, IME handling, and SDL runtime integration.
- Change coupling:
  affects game lifecycle, input, overlays, and crash surfaces.

### `feature/controls`

- Responsibility:
  virtual control models, pack management, editor tooling, rendering, and runtime overlays.
- Key files:
  `packs/ControlPackManager.kt`, editor viewmodels/helpers/managers, `ui/ControlLayoutScreenWrapper.kt`, texture and input bridge files.
- Implementation details:
  large, high-churn feature spanning storage, editing UX, rendering assets, and game-time input behavior.
- Change coupling:
  broad; touches settings, gameplay overlays, imported packs, and asset loading.

### `feature/gog`

- Responsibility:
  GOG authentication, catalog browsing, and download/import workflow.
- Key files:
  `data/GogDownloader.kt`, API/model files under `data` and `model`, `ui/DownloadScreenWrapper.kt`, `ui/GogScreen.kt`.
- Implementation details:
  mixes account/session handling, downloads with progress/resume semantics, and launcher-side import entry points.
- Change coupling:
  affects network/auth behavior, file download correctness, and main-screen integrations.

### `feature/installer`

- Responsibility:
  game import/install pipeline and plugin selection.
- Key files:
  `GameInstaller.kt`, `InstallPluginRegistry.kt`, `GameDefinition.kt`, plugins under `plugins`, `ui/ImportScreenWrapper.kt`.
- Implementation details:
  detects game or loader type, coordinates extraction/installation, and produces launcher-readable game entries.
- Change coupling:
  impacts import UX, storage layout, and persisted metadata.

### `feature/patch`

- Responsibility:
  patch discovery, installation, configuration, and launch-time activation data.
- Key files:
  `data/PatchManager.kt`, `data/PatchManifest.kt`, `data/PatchManagerConfig.kt`, `ui/PatchManagementDialogCompose.kt`.
- Implementation details:
  stores patch configs by game path and prepares the patch metadata consumed by runtime launch.
- Change coupling:
  tightly coupled with `core/platform/runtime` and assets under `patches`.

### `feature/settings` and `feature/filebrowser`

- Responsibility:
  reusable settings and file-browser UI/state flows used by main-screen wrappers.
- Key files:
  `feature/settings/vm/SettingsViewModel.kt`, `feature/settings/ui/SettingsScreen.kt`, `feature/settings/ui/SettingsScreenWrapper.kt`, `feature/filebrowser/FileBrowserModels.kt`, `feature/filebrowser/ui/FileBrowserScreen.kt`, `feature/filebrowser/ui/FileBrowserScreenWrapper.kt`.
- Implementation details:
  settings and file-browser contracts now live entirely in `app`, with wrapper composables bridging Android pickers, permissions, and dialogs.
- Change coupling:
  affects repository contracts, navigation, wrapper side effects, and persisted settings keys.

### `feature/announcement`, `feature/crash`, `feature/sponsor`, and `feature/script`

- Responsibility:
  supporting product features for announcements, crash diagnostics, sponsor UI, and JavaScript tooling.
- Key files:
  `feature/announcement/AnnouncementRepositoryService.kt`, `feature/announcement/ui/AnnouncementScreenWrapper.kt`, `feature/crash/ui/CrashReportActivity.kt`, `feature/sponsor/ui/SponsorsActivity.kt`, `feature/script/JavaScriptExecutor.kt`.
- Implementation details:
  lower-volume features, but they still plug into the main shell, manifest, or diagnostics flows.
- Change coupling:
  usually local unless routing, manifest components, or shared utility contracts change.

## Native and Asset Surfaces

### `app/src/main/cpp`

- Responsibility:
  first-party native bridges plus vendored runtime/render/input libraries.
- Key files:
  first-party code under `main` and `dotnethost`; large external trees under `SDL`, `FNA3D`, `gl4es`, `FAudio`.
- Implementation details:
  keep first-party bridge changes isolated and avoid touching vendored trees unless native dependency work is intentional.
- Change coupling:
  very high when JNI signatures, runtime bootstrap, or renderer loading changes.

### `patches` and `app/src/main/assets`

- Responsibility:
  bundled patch assets, control defaults, and runtime support files shipped with the app.
- Key files:
  patch manifests/projects under `patches`, bundled control/patch assets under `app/src/main/assets`.
- Implementation details:
  these files are consumed by initialization, patch installation, and runtime setup flows.
- Change coupling:
  changes can silently break extraction, compatibility patches, or first-run setup if code and assets diverge.

## Mandatory Before-Edit Checklist

1. Confirm the target section in this file and list exact touched paths.
2. Run:
   `scripts/component_profile.sh <target> .`
3. Check DI registration files if dependencies are added or removed.
4. Check manifest for component, process, or permission implications.
5. Check repository/storage impacts if public types, keys, or persisted formats change.
6. For runtime or patch changes, verify launch-path coupling:
   `feature/main` -> `core/platform/runtime` -> `feature/game` -> `patches`.
