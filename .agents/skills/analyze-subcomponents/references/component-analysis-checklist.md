# RotatingartLauncher Subcomponent Analysis Checklist

Use this reference after running the skill's `scripts/component_profile.sh`.

## Component Classes in This Repo

- `app/src/main/java/com/app/ralaunch/feature/*`:
  feature verticals, screen wrappers, viewmodels, import/download flows, and game-facing UX.
- `app/src/main/java/com/app/ralaunch/core/*`:
  app-wide infrastructure such as DI, repositories, navigation, theme, common managers, and platform/runtime integrations.
- `app/src/main/cpp/*`:
  first-party native bridge code plus vendored third-party source trees.
- `patches/*` and `app/src/main/assets/*`:
  patch assets, bundled data, and runtime support files consumed by launcher flows.

## Package Placement Convention

- `.../ui`:
  Compose screens, wrappers, and other UI-facing code.
- `.../vm`:
  ViewModels, reducers, and state orchestration classes.
- `.../model`:
  feature-owned models, DTOs, and state/data shapes.
- `.../contract`:
  contracts, interfaces, and abstraction boundaries.
- `.../service`:
  concrete services, managers, repositories, and other implementation classes.

## Coupling Checkpoints

1. DI registration and repository ownership:
   - `app/src/main/java/com/app/ralaunch/core/di/AppModule.kt`
   - `app/src/main/java/com/app/ralaunch/core/di/contract`
   - `app/src/main/java/com/app/ralaunch/core/di/service`
2. App entry/runtime path:
   - `app/src/main/java/com/app/ralaunch/RaLaunchApp.kt`
   - `app/src/main/AndroidManifest.xml`
3. Navigation and screen composition:
   - `app/src/main/java/com/app/ralaunch/core/navigation`
   - `app/src/main/java/com/app/ralaunch/feature/main/ui/MainApp.kt`
   - `app/src/main/java/com/app/ralaunch/feature/main/ui/MainActivityCompose.kt`
4. Storage and persistence:
   - `app/src/main/java/com/app/ralaunch/core/di/contract/GameRepositoryV2.kt`
   - `app/src/main/java/com/app/ralaunch/core/di/contract/SettingsRepositoryV2.kt`
   - `app/src/main/java/com/app/ralaunch/core/di/service/GameRepositoryImpl.kt`
   - `app/src/main/java/com/app/ralaunch/core/di/service/SettingsRepositoryImpl.kt`

## Change-Impact Questions

- Which symbols in this component are referenced outside its own folder?
- Does this component own repository contracts, storage keys, or serialized data?
- Does this component participate in process boundaries (`:game`, `:launcher`)?
- Does this component require manifest updates (activity/service/provider)?
- Does this component depend on native/JNI or external SDK APIs?
- Does this component affect route contracts, wrapper slots, or back-stack behavior?

## High-Risk Areas

- Runtime/bootstrap:
  `app/src/main/java/com/app/ralaunch/core/platform/runtime`
- Process and provider integration:
  `app/src/main/java/com/app/ralaunch/core/platform/android`
  and
  `app/src/main/java/com/app/ralaunch/core/platform/network/easytier`
- Main navigation orchestration:
  `app/src/main/java/com/app/ralaunch/core/navigation`
  and
  `app/src/main/java/com/app/ralaunch/feature/main`
- Virtual controls stack:
  `app/src/main/java/com/app/ralaunch/feature/controls`
- Patch flow:
  `app/src/main/java/com/app/ralaunch/feature/patch`
  and
  `patches`
- Game launch path:
  `app/src/main/java/com/app/ralaunch/feature/game`
  plus runtime loader classes under `core/platform/runtime`

## Reporting Format

Produce component findings in this order:

1. Responsibility summary (1-2 lines).
2. Key files and declarations.
3. Dependency profile (internal vs external imports).
4. Outward references and likely blast radius.
5. Required companion updates (DI, navigation, manifest, storage, assets, tests).
