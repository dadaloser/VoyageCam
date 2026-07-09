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
28. Unlock historical locked clips from the recording list and remove their emergency-event references.
29. Delete historical recording files and emergency-event records through in-app confirmation flows.
30. Manually run loop-capacity cleanup for normal clips and repair stale emergency-event clip references.
31. Capture recent GPS track points during recording and include `gps_track.csv` in emergency evidence packages.
32. Show an in-app route preview for emergency events with GPS tracks, including distance, duration, speed, and start/end coordinates.
33. Let users disable GPS location and route metadata capture independently from core video recording.
34. Generate optional SRT watermark sidecar subtitles during evidence export with time, speed, and coordinates while preserving original video files.
35. Capture optional GPS bearing/heading data and surface it in event summaries, route previews, CSV exports, and watermark subtitles.
36. Restore default settings through a confirmation flow without deleting recordings, emergency events, or exported evidence packages.
37. Bind rear and front CameraX previews together before and during recording when dual-camera mode is enabled, with rear-preview fallback if concurrent preview startup fails.
38. Attempt concurrent front/rear CameraX recording in dual-camera mode, writing paired `_rear` and `_front` MP4 files and falling back to rear-only recording if startup fails.
39. Group front/rear clips in history and open paired clips in a two-pane in-app playback view when both files are available.
40. Provide shared in-app controls for paired playback, including play/pause, restart, and manual front/rear resync.
41. Monitor paired playback drift and automatically realign the muted secondary view when the offset exceeds the sync threshold.
42. Apply a recording performance guard that watches thermal severity, low battery, and slow segment transitions, then disables front-camera recording while keeping rear recording alive.
43. Keep thermal, low-battery, and slow-segment performance guards enabled by default while still allowing users to force each protection on or off from settings.

## Current Status

- Single-module Android project.
- Package-level architecture is split into `core`, `data`, `feature`, and `ui` areas so future Gradle module extraction can happen incrementally.
- Kotlin + Jetpack Compose UI.
- Local settings persisted with `Room`.
- Camera concurrency capability detection via CameraX `CameraManager`.
- Runtime permission flow for camera, Android 13+ notifications, optional microphone audio, and optional event location/route metadata.
- Foreground service backed by CameraX `Recorder` pipelines for rear-camera single recording and first-pass dual-camera recording.
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
- Locked clips can be returned to normal loop-recording management from the history list, and related emergency-event metadata is updated.
- Recording rows can delete managed local clip files after confirmation, while emergency events can delete only the event metadata and keep linked clips available in history.
- Settings can manually trigger normal-clip cleanup using the current capacity limit, and emergency events can repair missing linked-clip references.
- Emergency events can store recent GPS track points when location permission is available, and exported evidence ZIP files include a route-ready `gps_track.csv`.
- Emergency rows summarize GPS routes directly in-app, with calculated distance, duration, average speed, max speed, and start/end coordinates.
- Settings include a GPS metadata privacy switch; disabling it stops route sampling, clears the in-memory GPS buffer, and keeps future emergency events free of location and track metadata.
- Evidence export can include `watermark/*.srt` sidecar subtitles derived from event GPS data, so time/speed/location overlays can be previewed without modifying source clips.
- Evidence export can also generate transcoded clip copies with burned-in time/speed/location overlays while keeping original recordings untouched.
- GPS metadata now preserves device-provided bearing when available, while old event records without bearing remain readable.
- Settings can be restored to defaults from an in-app confirmation panel; this resets configuration only and leaves recorded/evidence data intact.
- Dual-camera mode now keeps the front-camera inset visible before and during recording on capable devices by sharing CameraX concurrent preview/recording coordination.
- Dual-camera recording now has a first CameraX concurrent-recording path: successful sessions create paired rear/front clips in the same segment group, and emergency locking protects both when available.
- Dual-camera preview/recording now surfaces live session telemetry in the recording panel, including the active session token, concurrent-binding state, preview attachment state, and latest fallback diagnostic.
- Settings now persist and display both the latest dual-camera fallback diagnostic and the latest dual-camera session telemetry snapshot, with clear actions for fresh device repro runs.
- Recording history now identifies front/rear clips from the same segment group, and in-app playback can load both files together for a basic synchronized review surface.
- Paired playback now uses shared controls so the front/rear views can pause, restart, and manually realign without relying on separate native video controls.
- Paired playback now polls front/rear player positions, reports the current offset, and auto-corrects the secondary muted view when drift exceeds the configured threshold.
- Recording now has a first performance-protection policy: severe thermal state, unplugged low battery, or slow segment rotation can disable dual-camera recording for the current session and continue rear-camera recording with a visible notification reason.
- Settings now expose independent thermal, low-battery, and slow-segment protection switches; all three default to enabled and can be force-disabled for device-specific testing.

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## Dual-Camera Device Checklist

Use this flow on a real device when checking whether the front-camera inset stays visible during recording:

1. Open Settings, turn on dual-camera mode, then tap `清空` in both `双摄诊断` and `双摄会话状态`.
2. Return to the main recording panel and confirm the front-camera inset appears before recording starts.
3. Start recording and watch the live telemetry under the status text:
   - `并发预览已绑定` or `并发预览/录制已绑定` means the concurrent CameraX session is alive.
   - `已回落到后摄预览` means the app has already dropped to rear-only preview for the current session token.
4. If the front inset disappears, note the live telemetry summary and any diagnostic line shown in red.
5. Stop recording, open Settings again, and compare:
   - `双摄诊断` for the latest persisted fallback reason.
   - `双摄会话状态` for the latest persisted session summary, preview attachment state, and diagnostic.
6. Before the next repro attempt, clear both panels again so the next run is not mixed with stale telemetry.

## Next Development Steps

1. Add `androidTest` coverage for the dual-camera flow: preview visible before recording, inset retained during recording, and persisted telemetry available after stop.
2. Collect real-device thermal, battery, and segment-gap data to tune performance-guard thresholds per device class.
3. Run the burned-watermark export path on target devices and collect performance/codec compatibility notes for longer dual-camera evidence packages.
4. Extract `core`, `data`, `feature`, and `ui` into Gradle modules once the package boundaries stabilize.
5. Add a richer map-backed route viewer when map dependencies are introduced.
6. Evaluate a lower-level continuous recording path if measured segment gaps remain unacceptable on target devices.
