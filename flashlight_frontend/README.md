# Flashlight Frontend

A minimal Android app that provides a single button to toggle the device flashlight (torch). It requests camera permission only when required by the device/OS and gracefully handles devices without flash.

## Modules
- app: Android application with a single-activity UI and no Compose.
- list, utilities: Sample library modules used by the template.

## Build
From the flashlight_frontend folder:
- Build all modules:
  ./gradlew build

- Assemble debug APK:
  ./gradlew :app:assembleDebug

## Install & Run
- Install on a connected device or emulator (emulators usually have no flash):
  ./gradlew :app:installDebug

- Then launch “Flashlight” on the device.

## Permissions
- CAMERA permission is declared.
- The app requests permission only if a SecurityException occurs when toggling the torch on devices/OS versions that require CAMERA permission for torch control.
- If permission is denied, the UI will immediately reflect the unavailability and remain disabled on unsupported devices.

## Expected UI States
- Initial: Centered button labeled “Turn On”.
- When torch is on: Button shows “Turn Off”.
- If flash is unavailable or temporarily uncontrollable: Button is disabled and shows “Flash not available”.

## Lifecycle & Stability
- The app re-scans the back camera with flash in onStart to adapt to runtime changes.
- UI text updates immediately after permission is granted/denied.
- TorchCallback keeps UI in sync with actual torch state.
- Minimal dependencies are used (Material Components for classic Views only).

## Notes
- Theme: Theme.OceanProfessional (MaterialComponents DayNight NoActionBar).
- Min SDK 30, Compile SDK 34 as set by the project defaults.
