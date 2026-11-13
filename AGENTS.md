# AGENTS.md — Selfie: Real-Time Emotion Detector with CameraX

## 1. Project Overview & Presentation Context

**Selfie** is a modern Android application built for the technical talk **"Building a Real-Time Emotion Detector with CameraX"**.
It demonstrates how to ingest live front/back camera frames via **Jetpack CameraX (`camera-compose`)**, run low-latency on-device machine learning inference using **MediaPipe Face Landmarker (`tasks-vision` / LiteRT)**, classify 52 facial blendshape scores into discrete emotions in real time, and project 478 normalized 3D facial landmarks onto a dynamic **Jetpack Compose `Canvas`** overlay without dropping frames or leaking memory.

---

## 2. Core Engineering Invariants (The 5 Presentation Takeaways)

Every architectural and implementation change in this repository **must** uphold and clearly document these five core presentation takeaways:

1. **Lifecycle-Safe Camera Setup (`CameraSessionManager`)**
   - Always bind CameraX use cases (`Preview` and `ImageAnalysis`) to a `LifecycleOwner` via `ProcessCameraProvider`.
   - Expose `SurfaceRequest` as a reactive `StateFlow<SurfaceRequest?>` rendered by `CameraXViewfinder` from `androidx.camera:camera-compose`.
   - Never hold static references to `Activity`, `Context`, or `LifecycleOwner` inside long-lived camera components.

2. **Buffer Hygiene (`EmotionImageAnalyzer`)**
   - Configure `ImageAnalysis` with `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to provide non-blocking backpressure.
   - Use `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888` to avoid expensive manual YUV_420_888-to-RGB conversion loops in Kotlin.
   - **Mandatory Rule**: Always wrap `ImageProxy` processing in a Kotlin `.use { proxy -> ... }` block so `ImageProxy.close()` is guaranteed to run on every code path, preventing CameraX buffer starvation and UI lockups.

3. **On-Device Blendshape Emotion Logic (`BlendshapeEmotionClassifier`)**
   - Keep emotion classification logic in a pure, deterministic Kotlin domain class with zero Android framework dependencies so it can be unit-tested on the JVM.
   - Map MediaPipe's 52 ARKit-compatible facial blendshape scores (FACS action units such as `mouthSmileLeft/Right`, `jawOpen`, `browDownLeft/Right`, `eyeWideLeft/Right`, `mouthFrownLeft/Right`, `noseSneerLeft/Right`, `eyeBlinkLeft/Right`) into discrete `Emotion` states.
   - Apply **Exponential Moving Average (EMA)** temporal smoothing and hysteresis thresholds to eliminate frame-to-frame UI flicker during live demos.

4. **Accurate Screen Transforms (`CoordinateMapper`)**
   - MediaPipe returns landmarks in normalized `[0f..1f]` sensor/image coordinates.
   - Always transform normalized coordinates to Compose `Canvas` pixel coordinates by accounting for:
     1. **Sensor Orientation**: Upright rotated frame dimensions (`sourceWidth`, `sourceHeight`).
     2. **Front-Camera Mirroring**: Horizontal flip (`1f - normalizedX`) when streaming from the front lens.
     3. **Center-Crop Scaling (`FILL_CENTER`)**: Uniform scale factor `max(canvasWidth / sourceWidth, canvasHeight / sourceHeight)` and centering translation offsets `(offsetX, offsetY)`.

5. **Production Performance & Telemetry (`SpeakerTelemetryHud`)**
   - Run all `ImageAnalysis` frame ingestion and MediaPipe `LIVE_STREAM` inference on a dedicated single-thread background `Executor` (`Executors.newSingleThreadExecutor()`), never on `Dispatchers.Main`.
   - Support switching between `Delegate.CPU` and `Delegate.GPU` with graceful fallback to CPU if GPU initialization is unsupported on the target device/emulator.
   - Surface real-time telemetry (FPS, inference latency in `ms`, active delegate, frame resolution, and top blendshape activation bars) in the toggleable Speaker HUD.

---

## 3. Architecture & Tech Stack

- **Language**: Kotlin 2.2+
- **UI Toolkit**: Jetpack Compose (Material 3, Edge-to-Edge, `CameraXViewfinder`, Compose `Canvas`)
- **Architecture Pattern**: Clean MVVM + Unidirectional Data Flow (UDF) with `StateFlow` and `collectAsStateWithLifecycle()`
- **Dependency Injection**: Koin (`koin-android`, `koin-androidx-compose`)
- **Camera Pipeline**: Jetpack CameraX 1.5 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose`)
- **On-Device ML**: MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) using `face_landmarker.task` (LiteRT runtime with 478 3D landmarks + 52 blendshape scores)

### Package Structure

```text
com.jacqui.selfie
├── SelfieApplication.kt              # Koin DI bootstrap
├── MainActivity.kt                   # Edge-to-edge host activity
├── di/
│   └── AppModule.kt                  # Koin module definitions
├── domain/
│   ├── model/
│   │   ├── Emotion.kt                # Discrete emotions, emojis, FACS descriptions, colors
│   │   └── FaceAnalysisResult.kt     # Immutable frame analysis & telemetry state
│   └── classifier/
│       └── BlendshapeEmotionClassifier.kt # Pure FACS blendshape -> Emotion classifier + EMA smoothing
├── data/
│   ├── analyzer/
│   │   └── EmotionImageAnalyzer.kt   # ImageAnalysis.Analyzer + MediaPipe FaceLandmarker LIVE_STREAM
│   └── camera/
│       └── CameraSessionManager.kt   # Lifecycle-safe CameraX Preview + ImageAnalysis manager
└── ui/
    ├── mapper/
    │   └── CoordinateMapper.kt       # Normalized [0..1] -> Canvas pixel coordinate math
    ├── components/
    │   ├── EmotionBadgeCard.kt       # Expressive bottom card showing active emotion & confidence
    │   ├── FaceMeshOverlay.kt        # Real-time Compose Canvas landmark & contour overlay
    │   └── SpeakerTelemetryHud.kt    # Live presentation HUD (FPS, ms, blendshape bars, toggles)
    ├── screen/
    │   └── EmotionDetectorScreen.kt  # Main screen with permission gate & camera binding
    ├── viewmodel/
    │   ├── EmotionUiState.kt         # Immutable UI state
    │   └── EmotionViewModel.kt       # State holder & user intent handler
    └── theme/                        # Material 3 theme, typography, emotion color tokens
```

---

## 4. Code Documentation Standards

Because this codebase is dissected live during a conference/session presentation:
- **Every file, class, and public function must include clear KDoc comments.**
- Reference which of the **5 Key Takeaways** a class or method demonstrates (e.g., `@see Takeaway 2: Buffer Hygiene`) so presenters and attendees can navigate directly from slides to code.
- Explain mathematical formulas (EMA smoothing, FACS blendshape weights, `FILL_CENTER` coordinate projection) inline with ASCII diagrams or step-by-step comments.

---

## 5. Build, Test, and Verification Commands

- **Run JVM Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Assemble Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Run Lint Checks**:
  ```bash
  ./gradlew lintDebug
  ```
