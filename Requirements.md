Title: Building a Real-Time Emotion Detector with CameraX

Description
Building responsive computer vision features often feels daunting due to frame drops, coordinate mismatch, orientation bugs, and memory leaks. In this session, we dissect a production-ready Android application that detects facial expressions and maps emotions in real time using Jetpack CameraX and on-device machine learning (leveraging MediaPipe Face Landmarker blendshapes and LiteRT). Through a live app demo and a deep dive into the pre-built codebase, we trace how live camera frames are ingested, processed, and translated into expressive UI feedback without blocking the main thread or relying on cloud inference.

What will be covered:
- CameraX Setup & Backpressure: Streaming frames smoothly via ImageAnalysis without UI lag.

- On-Device Emotion Extraction: Using facial blendshapes to classify emotions locally.

- Coordinate Mapping & Overlays: Projecting face landmarks onto dynamic UI layouts cleanly.

Key Takeaways:
- Lifecycle-Safe Camera Setup: Configure front-camera streaming in CameraX with minimal boilerplate.

- Buffer Hygiene: Prevent UI lockups and leaks by managing ImageProxy lifecycles properly.

- Blendshape Emotion Logic: Convert raw facial landmark scores into discrete emotions on-device.

- Accurate Screen Transforms: Map sensor coordinates to UI overlays across different aspect ratios.

- Production Performance: Avoid battery drain, memory churn, and thermal throttling in live pipelines.

 Here is the CameraX documentation for context: https://developer.android.com/media/camera/camerax, 