# VoyageCam

VoyageCam is an Android dashcam app prototype built from the dual-camera dashcam requirements document. The current slice focuses on stable rear-camera recording first, then progressively adds evidence management around emergency events.

## Implemented

1. Detect whether the device reports support for opening front and rear cameras at the same time.
2. Keep dual-camera recording behind a settings switch that is disabled when unsupported.
3. Persist camera capability results for quick display on the next launch.
4. Provide custom video storage capacity configuration for loop recording cleanup.
5. Provide an ambient driving audio switch that only requests microphone permission when enabled.
6. Start rear-camera MP4 recording through a foreground service with persistent notification actions.
7. Roll rear-camera recording into configurable 1/3/5 minute segments.
8. Clean old normal clips when configured capacity is exceeded while preserving locked clips.
9. Manually lock emergency evidence clips so current, previous, and next segments are protected.
10. Detect high-acceleration events and trigger the same emergency locking flow.
11. Show a live rear-camera preview before recording starts.
12. List recent normal and locked recording segments from local storage.
13. Open recent segments in the system video player and share them through Android's share sheet.
14. Filter recording segments by day, camera direction, and locked state.
15. Persist emergency event metadata for manual and collision-triggered locks.
16. Open or share the locked clips linked to an emergency event.
17. Capture optional GPS location metadata for emergency events when location permission is granted.
18. Open emergency-event coordinates in a map app through a `geo:` deep link.
19. Automatically start foreground recording when the charger is connected, if enabled and permissions are already granted.
20. Automatically start foreground recording when a configured trusted Bluetooth device connects.

## Current Status

- Single-module Android project.
- Kotlin + Jetpack Compose UI.
- Local settings persisted with `SharedPreferences`.
- Camera concurrency capability detection via Camera2 `CameraManager`.
- Runtime permission flow for camera, Android 13+ notifications, optional microphone audio, and optional event location metadata.
- Foreground service backed by Camera2 + `MediaRecorder` for rear-camera single recording.
- Segment-style file naming under the app-specific `Movies/Dashcam/normal/yyyy-MM-dd/group_HHmmss/` directory.
- Manual and sensor-triggered emergency locking moves protected evidence into `Movies/Dashcam/locked/`.
- Emergency events are stored in app-private metadata with trigger type, timestamp, collision g-force details, optional GPS location, and linked locked segment paths.
- Segment and event rows use `FileProvider` for playback/share without broad storage permissions.
- Emergency event rows can open the first linked clip, share all available linked clips, or open recorded coordinates in a map app.
- Optional charger auto-start is backed by an `ACTION_POWER_CONNECTED` receiver and reuses the existing foreground recording service.
- Optional Bluetooth auto-start matches a configured trusted device name or MAC address from `ACTION_ACL_CONNECTED`.

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## Next Development Steps

1. Keep preview visible during recording by sharing the Camera2 session or moving recording into an activity-bound preview pipeline.
2. Implement dual-camera recording for devices that pass the capability check, with rear-only fallback.
3. Add in-app playback and export progress for longer evidence packages.
4. Add GPS metadata and watermarks for evidence exports.
5. Improve auto-start diagnostics and show the last ignored trigger reason in settings.
