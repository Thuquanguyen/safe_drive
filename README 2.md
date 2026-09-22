# SafeDrive — Driver Drowsiness Detection (Android · Kotlin · Jetpack Compose)

Spec: `docs/driver_drowsiness_detection_spec.md`, `docs/walkthrough.md`
Design: Stitch export `~/Downloads/safe_drive/stitch_remix_of_remix_of_safedrive_alert/*_exact` (15 screens, light / blue-600 system).

## Build
- Open in Android Studio (AGP 8.6.1, Kotlin 2.0.21, JDK 17, compileSdk 35, minSdk 26).
- First build downloads the MediaPipe model `face_landmarker.task` into `app/src/main/assets/` (Gradle task `downloadFaceLandmarkerModel`).

## Structure (spec §40)
```
motion/      MotionDetector (Activity Recognition), SpeedTracker (Fused GPS), AutoDrivingController (confirm + grace)
service/     DrowsinessMonitoringService (FGS camera|location), MonitoringNotificationManager, MonitoringStore
power/       PowerAdaptiveManager (6 / 3 / 8 FPS), WakeLockManager (PARTIAL)
camera/      CameraController (640x480, headless), FrameAnalyzer (FPS gate, RGBA buffer → MPImage)
vision/      FaceLandmarkAnalyzer (MediaPipe), EyeAnalyzer (EAR), MouthAnalyzer (MAR), HeadPoseAnalyzer
drowsiness/  DrowsinessEngine (fusion + state machine + hysteresis), TemporalAnalyzer (PERCLOS), CalibrationManager, DrowsinessConfig
alert/       AlertManager, AudioAlert (STREAM_ALARM), VibrationAlert, VoiceAlert (TTS)
session/     DrivingSession, SessionRepository (AES-GCM Keystore-encrypted local history)
settings/    SettingsRepository (DataStore)
ui/          theme tokens (Tailwind palette from design), components, 17 screens, navigation
```

## Screens
Design (15): Welcome, Your Privacy, Camera Permissions, Setup Guide, Position Camera, Home Dashboard, Monitoring,
Stay Alert (ATTENTION), Drowsiness Detected (DROWSY), Critical Warning (CRITICAL + lock-screen full-screen intent),
Eyes Not Visible, Low Light Warning, Trip Summary, Driving History, Tips.
Spec-only, built with the same design system: Calibration (spec Screen 2), Settings (spec §49).

## Known platform limits
- Android 11+ blocks camera access for a foreground service started from the background. When auto-detect confirms
  driving while the app is closed, SafeDrive posts "Driving detected — tap to start monitoring" instead.
- Auto-detect needs "Allow all the time" location (asked when enabling it in Settings).
