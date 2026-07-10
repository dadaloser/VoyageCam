# VoyageCam

VoyageCam is an Android dual-camera dashcam prototype focused on turning an idle phone into a rear-first driving recorder, with optional concurrent front + rear capture on supported devices.

## Current Product Snapshot

### Recording Core

1. Rear-only, front-only, and supported-device dual-camera recording modes.
2. Camera capability detection, persisted dual-camera availability, and in-app redetection.
3. Foreground-service recording with notification controls and configurable 1/3/5 minute segment rotation.
4. Dual-camera startup fallback to rear-only recording when concurrent recording cannot be sustained.

### Storage And Evidence

1. Configurable loop-recording capacity with normal-clip cleanup and locked-clip protection.
2. Manual emergency lock plus accelerometer-triggered collision locking for previous/current/next segments.
3. Historical unlock, delete, manual cleanup, and emergency-event repair flows.
4. Emergency evidence ZIP export with linked clips, metadata, GPS track CSV, optional SRT subtitles, and optional burned-in watermark transcodes.

### Playback And Review

1. Timeline-style history grouped by recording segment set.
2. In-app playback for single clips and paired front/rear playback.
3. Shared paired-playback controls with manual resync and automatic drift correction.
4. Rear-only, front-only, and dual-package export from history, plus system-player and share-sheet fallback.

### Auto Start, Privacy, And Safeguards

1. Optional charger auto-start and trusted Bluetooth auto-start.
2. Optional ambient audio and optional GPS metadata, each gated by runtime permission flow.
3. Thermal, low-battery, and slow-segment performance guards with user-visible toggles.
4. Settings reset without deleting recordings or exported evidence.

### Diagnostics

1. Persisted dual-camera diagnostics and session telemetry for device repro runs.
2. Runtime logs, crash records, and archived dual-camera failure snapshots in settings.
3. Dual-camera preview/recording telemetry surfaced directly in the recording panel.

## Milestone Status

- Stage 0 `技术验证`: complete. Dual-camera capability detection, state persistence, redetection, and first concurrent preview/recording path are in place.
- Stage 1 `MVP`: complete. Core recording, settings, dual-camera gating, fallback behavior, notification flow, and basic history are all implemented.
- Stage 2 `行车记录仪能力完善`: complete in feature scope. Loop cleanup, emergency lock, collision detection, storage controls, and fuller settings are implemented.
- Stage 3 `回放与导出`: mostly complete. Timeline history, paired playback, clip export, evidence ZIP export, GPS track export, and watermark export are implemented.
- Stage 4 `兼容性与发布`: in progress. Diagnostics and performance protections are present, but real-device soak testing, compatibility matrix work, and release hardening are still open.

## Architecture Snapshot

- Single-module Android app for now, with package boundaries already split into `core`, `data`, `feature`, and `ui`.
- Kotlin + Jetpack Compose UI.
- Camera2-based capability inspection plus CameraX preview/recording pipelines.
- `SharedPreferences` for settings and last-known capability snapshot.
- Room-backed local stores for segment indexing, emergency events, dual-camera diagnostics/session telemetry, and runtime telemetry.
- Recording files stored under the app-specific `Movies/Dashcam/` directory and shared through `FileProvider`.

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Validation Snapshot

1. `./gradlew :app:testDebugUnitTest` currently passes.
2. The project already contains `androidTest` coverage for route assembly, settings, history, playback-related flows, and dual-camera telemetry UI.
3. Real-device 30 minute / 2 hour / 4 hour soak tests and multi-brand compatibility reports are still missing and remain the main delivery risk.

## Dual-Camera Device Checklist

Use this flow on a real device when checking whether the front-camera inset stays visible during recording:

1. Open Settings, turn on dual-camera mode, then clear both `双摄诊断` and `双摄会话状态`.
2. Return to the recording panel and confirm the front-camera inset appears before recording starts.
3. Start recording and watch the live telemetry summary:
   - `并发预览已绑定` or `并发预览/录制已绑定` means the concurrent CameraX session is alive.
   - `已回落到后摄预览` means the app has already dropped to rear-only preview for the current session token.
4. If the front inset disappears, note the live telemetry summary and any fallback diagnostic shown in red.
5. Stop recording, reopen Settings, and compare the latest persisted dual-camera diagnostic and session telemetry entries.

## Next Development Plan

1. Harden continuous recording: add stronger pre-recording storage checks, tighten segment-gap handling, and improve incomplete-segment recovery after abnormal interruption.
2. Build a real-device compatibility matrix across Pixel, Samsung, Xiaomi, OPPO, vivo, Honor, and OnePlus, then derive device-class defaults and dual-camera allow/deny guidance.
3. Improve dual-camera UX with main/sub preview switching, a draggable auto-snapping front inset, and clearer downgrade messaging.
4. Complete playback/export scope by adding picture-in-picture composite video export and a richer map-backed route viewer.
5. Finish release hardening: soak-test reports, release build/proguard review, privacy copy, regression checklist, and optional module extraction once package boundaries stabilize.
