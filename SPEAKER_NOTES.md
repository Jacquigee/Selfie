# Speaker Notes: Building a Real-Time Emotion Detector with CameraX

**Talk Title**: Building a Real-Time Emotion Detector with CameraX  
**App Demo**: *Selfie* (Jetpack CameraX + MediaPipe LiteRT + Jetpack Compose)  
**Target Duration**: 30 – 45 Minutes  

---

## Presentation Roadmap & Timing Guide

| Section | Topic | Duration | Key Code / Asset |
|---|---|---|---|
| **Intro** | The Reality of On-Device Computer Vision | 3 mins | Problem Statement |
| **Architecture** | End-to-End Pipeline Overview | 4 mins | Architecture Diagram |
| **Takeaway 1** | Lifecycle-Safe Camera Setup | 6 mins | `CameraSessionManager.kt` |
| **Takeaway 2** | Buffer Hygiene & Backpressure | 6 mins | `EmotionImageAnalyzer.kt` |
| **Takeaway 3** | Blendshape Emotion Logic | 7 mins | `BlendshapeEmotionClassifier.kt` |
| **Takeaway 4** | Accurate Screen Transforms | 6 mins | `CoordinateMapper.kt` |
| **Takeaway 5** | Production Performance & Telemetry | 4 mins | `SpeakerTelemetryHud.kt` |
| **Live Demo** | Stage Demonstration & Edge Cases | 5 mins | App running on device |
| **Wrap-up** | Key Takeaways & Q&A | 5 mins | Summary slide |

---

## Slide 1: Title & The On-Device CV Dilemma

### Slide Goal
Set the stage: Building computer vision on mobile is notoriously tricky because Android developers face frame drops, coordinate mismatch, orientation bugs, and memory leaks.

### Script / Talking Points
> *"Good morning/afternoon everyone! Welcome to 'Building a Real-Time Emotion Detector with CameraX'.*
>
> *If you’ve ever tried to integrate a camera feed with on-device machine learning in Android, you’ve probably experienced that dreaded sensation: the phone starts heating up, the UI stutters at 12 frames per second, the face overlay is upside down or mirrored on the wrong cheek, and after two minutes of demoing to your team, the camera freezes because of an `OutOfMemoryError` or buffer exhaustion.*
>
> *Today, we’re going to dissect a production-ready application called **Selfie**. We will trace how a live camera frame travels from the hardware sensor, through Jetpack CameraX, into MediaPipe's LiteRT runtime, gets classified into discrete emotions via facial muscle blendshapes, and renders on a 60/120Hz Jetpack Compose Canvas—without blocking the main thread for even a single millisecond."*

### Presenter Cue
- Show the app running briefly on screen or phone holder so the audience immediately sees the end goal: instant face mesh + emotion badge reacting in real time.

---

## Slide 2: Pipeline Architecture Overview

### Slide Goal
Show the complete unidirectional flow across three distinct layers: Camera Hardware, Background ML Inference, and Compose UI.

### Script / Talking Points
> *"Let’s look at the pipeline from 30,000 feet.*
>
> *Our architecture cleanly decouples concerns:*
> 1. *The **Camera Layer** (`CameraSessionManager`): Manages CameraX use cases (`Preview` and `ImageAnalysis`). It produces a reactive `SurfaceRequest` for the screen and an `ImageProxy` stream for vision analysis.*
> 2. *The **ML & Analysis Layer** (`EmotionImageAnalyzer` + `BlendshapeEmotionClassifier`): Ingests RGBA frames on a dedicated single-thread background executor. MediaPipe extracts 478 3D landmarks and 52 ARKit-compatible facial blendshape scores. Our pure Kotlin classifier translates those scores into discrete emotions using Exponential Moving Average (EMA) smoothing.*
> 3. *The **Presentation Layer** (`EmotionDetectorScreen`): Powered by Jetpack Compose. `CameraXViewfinder` renders the viewfinder, `CoordinateMapper` projects landmarks onto a Compose `Canvas`, and the Speaker Telemetry HUD displays live FPS and latency."*

### Key Slide Visual
Point to the mermaid architecture diagram:
`CameraX Preview` ➔ `CameraXViewfinder (UI)`  
`CameraX ImageAnalysis` ➔ `Background Executor` ➔ `MediaPipe LiveStream` ➔ `Domain Classifier` ➔ `StateFlow` ➔ `Compose UI`

---

## Slide 3: Takeaway #1 — Lifecycle-Safe Camera Setup

### Slide Goal
Explain why modern CameraX eliminates camera boilerplate and how to integrate it seamlessly with Jetpack Compose.

### Code Reference
[`app/src/main/java/com/jacqui/selfie/data/camera/CameraSessionManager.kt`](app/src/main/java/com/jacqui/selfie/data/camera/CameraSessionManager.kt#L43-L125)

### Script / Talking Points
> *"Takeaway Number 1: **Lifecycle-Safe Camera Setup**.*
>
> *Remember the days of Camera1 and Camera2? You had to register surface callbacks, handle orientation listeners, check camera IDs, and remember to release everything in `onPause` or risk crashing the app.*
>
> *In CameraX, we bind use cases directly to a `LifecycleOwner` via `ProcessCameraProvider`. When the user background the app or switches screens, CameraX automatically tears down the camera pipeline.*
>
> *In this app, we use the modern `androidx.camera:camera-compose` library. Look at how clean this is:*
> - *We configure `Preview.setSurfaceProvider { request -> _surfaceRequest.value = request }`.*
> - *We expose `surfaceRequest` as a `StateFlow<SurfaceRequest?>`.*
> - *In Compose, we simply declare: `CameraXViewfinder(surfaceRequest = request)`.*
>
> *No legacy `AndroidView` wrappers, no custom surface holders, and zero lifecycle leaks."*

### Slide Tip
Highlight that we intentionally set target resolution to 640x480 (`AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY`):
> *"Notice we request 640x480 for ML analysis. The face landmark network internally downscales images to 256x256 anyway. Streaming 4K frames into an ML model is a recipe for battery drain and thermal throttling."*

---

## Slide 4: Takeaway #2 — Buffer Hygiene & Backpressure

### Slide Goal
Explain the most frequent cause of camera freezes: leaking `ImageProxy` references and choosing the wrong image format.

### Code Reference
[`app/src/main/java/com/jacqui/selfie/data/analyzer/EmotionImageAnalyzer.kt`](app/src/main/java/com/jacqui/selfie/data/analyzer/EmotionImageAnalyzer.kt#L145-L215)

### Script / Talking Points
> *"Takeaway Number 2: **Buffer Hygiene**.*
>
> *If you take only one rule away from this talk, let it be this: **Always treat `ImageProxy` as an auto-closable resource, and never let it escape your analyzer scope.**
>
> *CameraX maintains a bounded internal buffer pool (typically 3 to 4 buffers). If your analyzer fails to call `imageProxy.close()`—for example, because an unhandled exception occurred or an early return was reached—that buffer never returns to the pool. Within 100 milliseconds, CameraX runs out of surfaces and silently stops delivering frames. The camera freezes.*
>
> *In Kotlin, the idiomatic fix is simple:*
> ```kotlin
> override fun analyze(imageProxy: ImageProxy) {
>     imageProxy.use { proxy ->
>         // Extract or copy pixels immediately
>     } // <-- ImageProxy.close() is ALWAYS guaranteed to execute here!
> }
> ```
>
> *Second: We set `setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)`. By default, CameraX delivers `YUV_420_888`. Converting 3 YUV planes to RGB in Kotlin on the CPU is agonizingly slow. By requesting `RGBA_8888`, CameraX uses hardware color conversion under the hood, handing us a single contiguous pixel buffer.*
>
> *Third: Notice `obtainReusableBitmap()`. We don't allocate a new `Bitmap` 30 times a second. We reuse one buffer, completely eliminating garbage collection stutter."*

---

## Slide 5: Takeaway #3 — Blendshape Emotion Logic

### Slide Goal
Explain why blendshapes are superior to raw 3D landmark Euclidean distances and how pure domain logic + EMA temporal smoothing creates rock-solid emotion detection.

### Code Reference
[`app/src/main/java/com/jacqui/selfie/domain/classifier/BlendshapeEmotionClassifier.kt`](app/src/main/java/com/jacqui/selfie/domain/classifier/BlendshapeEmotionClassifier.kt#L45-L185)

### Script / Talking Points
> *"Takeaway Number 3: **On-Device Blendshape Emotion Logic**.*
>
> *A common beginner mistake in face analysis is measuring Euclidean pixel distances between landmarks—like measuring the distance between lip corners to see if someone is smiling.*
> *Why does that break in production? Because if the user moves closer to the phone, turns their head, or tilts their chin, pixel distances change drastically.*
>
> *MediaPipe solves this by outputting **52 ARKit-compatible facial blendshape scores**. These represent Facial Action Coding System (FACS) muscle activations—like `mouthSmileLeft`, `jawOpen`, `browDownLeft`, and `eyeBlinkRight`—normalized between 0.0 and 1.0, invariant to distance or head rotation.*
>
> *In `BlendshapeEmotionClassifier.kt`, we keep all emotion mapping pure Kotlin—zero Android dependencies, fully unit-testable on the JVM in milliseconds.*
>
> *Let's look at the formulas:*
> - ***Happy***: *Bilateral average of `mouthSmile` (AU12) + `cheekSquint` (AU6).*
> - ***Surprised***: *`jawOpen` (AU26) + `eyeWide` (AU5) + `browInnerUp` (AU1).*
> - ***Wink***: *High differential: one eye blink > 0.50 while the other eye blink < 0.30.*
>
> *Crucial Detail: **Temporal Smoothing (EMA)**.*
> *Raw per-frame scores fluctuate rapidly near decision boundaries. If you map raw scores directly to UI, your badge flickers like crazy. We apply an Exponential Moving Average:*
> ```kotlin
> smoothed = alpha * rawScore + (1 - alpha) * previousSmoothed
> ```
> *With `alpha = 0.38`, it reacts in under 100ms but completely kills jitter."*

---

## Slide 6: Takeaway #4 — Accurate Screen Transforms

### Slide Goal
Solve the notorious camera coordinate mismatch: sensor rotation, front-camera mirroring, and aspect-ratio letterboxing/cropping.

### Code Reference
[`app/src/main/java/com/jacqui/selfie/ui/mapper/CoordinateMapper.kt`](app/src/main/java/com/jacqui/selfie/ui/mapper/CoordinateMapper.kt#L48-L115) & [`FaceMeshOverlay.kt`](app/src/main/java/com/jacqui/selfie/ui/components/FaceMeshOverlay.kt)

### Script / Talking Points
> *"Takeaway Number 4: **Accurate Screen Transforms**.*
>
> *How many times have you seen a camera overlay where the bounding box or sunglasses are floating 2 inches above the person's face or stretched horizontally?*
>
> *Here is why naive `landmark.x * screenWidth` fails:*
> 1. ***Sensor Aspect Ratio vs Screen Aspect Ratio***: *The camera sensor outputs 4:3 (e.g. 480x640 portrait). Modern phones have tall screens like 9:20 (e.g. 1080x2400). If the viewfinder is in `FILL_CENTER` (Center-Crop) mode, the camera preview crops the left and right sensor edges.*
> 2. ***Selfie Mirroring***: *A front-facing preview is mirrored horizontally so it behaves like a physical mirror. If you don't mirror the landmark's X coordinate, when you smile with the right corner of your mouth, the overlay draws on your left cheek!*
>
> *In `CoordinateMapper.kt`:*
> ```kotlin
> val effectiveX = if (isFrontCamera) (1f - normX) else normX
> val scaleFactor = max(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
> val scaledWidth = sourceWidth * scaleFactor
> val scaledHeight = sourceHeight * scaleFactor
> val offsetX = (canvasWidth - scaledWidth) * 0.5f
> val offsetY = (canvasHeight - scaledHeight) * 0.5f
>
> val pixelX = effectiveX * scaledWidth + offsetX
> val pixelY = normY * scaledHeight + offsetY
> ```
> *Notice: we center the scaled bounds with `offsetX` and `offsetY`. When the camera crops horizontally, `offsetX` is negative, correctly shifting landmarks to lock directly onto the user's eyes, nose, and mouth."*

---

## Slide 7: Takeaway #5 — Production Performance & Telemetry

### Slide Goal
Show how we protect the main thread and provide real-time observability during a live stage talk.

### Code Reference
[`app/src/main/java/com/jacqui/selfie/ui/components/SpeakerTelemetryHud.kt`](app/src/main/java/com/jacqui/selfie/ui/components/SpeakerTelemetryHud.kt)

### Script / Talking Points
> *"Takeaway Number 5: **Production Performance & Telemetry**.*
>
> *A smooth camera experience requires keeping the Main/UI thread pristine:*
> - *All frame analysis and MediaPipe calls run on a dedicated single-thread background executor (`Executors.newSingleThreadExecutor()`).*
> - *We support hardware delegate switching (`Delegate.CPU` via LiteRT XNNPACK vs `Delegate.GPU` via OpenGL/OpenCL) with automatic fallback if GPU shaders fail.*
> - *And because we are giving a live presentation today, we built the **Speaker Telemetry HUD** directly into the UI!*
>
> *Let's see it live on stage right now."*

---

## Slide 8: Live Demo Script (Step-by-Step on Stage)

### Stage Actions & Dialogue

1. **Launch App**:
   - *Action*: Open the *Selfie* app on the device.
   - *Dialogue*: *"Here is our live camera running edge-to-edge in Jetpack Compose. Notice the bottom card immediately identifies Neutral with 100% confidence."*

2. **Trigger Smile (Happy)**:
   - *Action*: Smile naturally at the camera.
   - *Dialogue*: *"Now I'll smile 😄. Instantly, the card updates to HAPPY, the accent tint shifts to green, and the face mesh tracks my mouth and eyes with zero lag."*

3. **Trigger Surprise**:
   - *Action*: Open your mouth and raise eyebrows wide.
   - *Dialogue*: *"Let's test SURPRISED 😲—jaw drops, eyes widen, and the classifier transitions seamlessly."*

4. **Trigger Wink & Angry**:
   - *Action*: Wink one eye deliberately 😉, then frown/lower brows 😠.
   - *Dialogue*: *"Here is an asymmetric WINK 😉, and here is ANGRY 😠 with brow lowerers active."*

5. **Open Speaker Telemetry HUD**:
   - *Action*: Tap the Analytics icon (`Analytics` icon in top bar).
   - *Dialogue*: *"Now let’s look under the hood. I’ll expand the Speaker Telemetry HUD.*
   - *Look at the live metrics:*
     - *We’re streaming at a rock-solid **30 FPS**.*
     - *Inference latency is just **18 to 25 milliseconds** on-device.*
     - *Down here, watch the top live blendshape bars in real time: as I smile, watch `mouthSmileLeft` and `mouthSmileRight` bars climb to 85%."*

6. **Toggle Hardware Delegate**:
   - *Action*: Tap the `Delegate: CPU` chip to toggle to GPU.
   - *Dialogue*: *"We can toggle between CPU and GPU delegates on the fly. Notice how latency drops even further on GPU-supported chipsets, with graceful fallback if unavailable."*

7. **Toggle Face Mesh**:
   - *Action*: Tap the Face icon in the top bar to toggle the 478-point mesh off and on.
   - *Dialogue*: *"We can cleanly toggle the 478-point landmark mesh without disturbing the underlying CameraX preview."*

---

## Slide 9: Summary & Key Takeaways

### Slide Goal
Reinforce the 5 pillars so attendees leave with clear architectural rules.

### Script / Talking Points
> *"To recap, when building responsive, real-time computer vision in Android:*
> 1. ***Lifecycle-Safe Camera Setup***: *Use CameraX use cases bound to `LifecycleOwner` and pair `SurfaceRequest` with `CameraXViewfinder` in Compose.*
> 2. ***Buffer Hygiene***: *Always use `.use {}` with `ImageProxy`, request `RGBA_8888`, and reuse Bitmaps.*
> 3. ***Blendshape Emotion Logic***: *Use normalized FACS blendshape activations instead of Euclidean distances, and apply EMA temporal smoothing.*
> 4. ***Accurate Screen Transforms***: *Mirror the front camera horizontally and apply `FILL_CENTER` center-crop math.*
> 5. ***Production Performance***: *Keep analysis on a background thread and monitor your FPS/latency metrics.*
>
> *The entire codebase is open-source and ready for you to clone and explore. Thank you!"*

---

## Slide 10: Anticipated Audience Q&A Guide

### Q1: "Why not use ML Kit Face Detection instead of MediaPipe Tasks Vision?"
> **Answer**: *"Great question. ML Kit Face Detection is great for basic landmark bounding boxes and rough smiling probabilities, but it does not provide the 52 ARKit-compatible FACS blendshapes. MediaPipe gives you fine-grained muscle activations (brow lowerers, nose sneer, lip corner depressor, asymmetric blink), which are necessary to distinguish between subtle emotions like Disgust, Anger, and a Wink."*

### Q2: "Why choose `RGBA_8888` over `YUV_420_888` if YUV is the camera's native format?"
> **Answer**: *"In native C++, decoding YUV using libyuv is fast. But in Kotlin or Java on Android, looping through three unaligned YUV planes with row and pixel strides causes massive CPU overhead and GC pressure. When you request `OUTPUT_IMAGE_FORMAT_RGBA_8888`, CameraX uses hardware acceleration to deliver a single contiguous byte buffer, drastically reducing CPU time."*

### Q3: "Does rendering 478 landmarks on a Compose Canvas drop frames?"
> **Answer**: *"No, because Compose `Canvas` uses hardware-accelerated Skia rendering under the hood. In `FaceMeshOverlay.kt`, we transform the 478 normalized points once per frame, and draw connected path segments using pre-compiled index arrays. The draw pass takes less than 2 milliseconds on modern devices."*

### Q4: "How does this pipeline behave on low-end devices or under thermal throttling?"
> **Answer**: *"Because we configure `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`, CameraX automatically drops intermediate frames if the ML model takes longer to compute. The preview remains responsive at 60 FPS while the analyzer gracefully runs at whatever rate the hardware can sustain (e.g. 15-20 FPS) without memory leaks or queue explosions."*
