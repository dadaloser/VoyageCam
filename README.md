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
21. Show the latest auto-start diagnostic result and ignored-trigger reason in settings.
22. Pick a trusted Bluetooth auto-start device from already paired Bluetooth devices.
23. Play selected recording clips inside the app with a native Android `VideoView` fallback to the system player.
24. Export emergency-event evidence packages as shareable ZIP files with metadata and linked clips.
25. Show cancellable evidence export progress and keep partial ZIP output isolated from original recordings.
26. Show recording storage usage, locked-clip footprint, and estimated remaining recording time in settings.
27. Ask for confirmation before lowering loop-recording capacity below current normal-clip usage, then clean only normal clips after confirmation.

## Current Status

- Single-module Android project.
- Package-level architecture is split into `core`, `data`, `feature`, and `ui` areas so future Gradle module extraction can happen incrementally.
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
- Auto-start diagnostics persist the latest trigger source, result, reason, detail, and timestamp for in-car testing.
- Trusted Bluetooth setup can read paired devices after Bluetooth permission is granted, while still allowing manual entry.
- Recording and emergency-event playback can happen in-app, while still offering system-player fallback and sharing.
- Emergency events can be exported into app-private ZIP evidence packages containing readable metadata and all available linked clips, with visible progress and cancellation for large packages.
- Evidence export writes to a temporary ZIP first, then saves a completed package name, so cancellation or export failure only removes partial output and leaves original clips untouched.
- Settings now summarize normal and locked recording space, configured loop-recording capacity, clip counts, and estimated remaining recording time based on current audio and camera mode settings.
- Reducing storage capacity below current normal recording usage now opens an in-app confirmation panel before applying the change and deleting old normal clips.

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## Next Development Steps

1. Keep preview visible during recording by sharing the Camera2 session or moving recording into an activity-bound preview pipeline.
2. Implement dual-camera recording for devices that pass the capability check, with rear-only fallback.
3. Add GPS route metadata and video watermarks for evidence exports.
4. Extract `core`, `data`, `feature`, and `ui` into Gradle modules once the package boundaries stabilize.
5. Add unlock/delete controls for historical recordings and emergency events.
