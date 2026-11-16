# VR Kendang — Low-Latency Android Percussion Instrument

[![Build APK](https://github.com/Vanderrhud/kendang-android-1/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Vanderrhud/kendang-android-1/actions/workflows/build-apk.yml)
![Platform](https://img.shields.io/badge/Platform-Android%20(API%2026%E2%80%9334)-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![Native Audio](https://img.shields.io/badge/C%2B%2B17-Google%20Oboe%201.9.3-00599C?logo=cplusplus&logoColor=white)
![License](https://img.shields.io/badge/License-Proprietary%20%2F%20Source--Available-red)

**VR Kendang** is a native Android multi-touch virtual percussion application engineered for ultra-low-latency real-time performance. It combines a **C++17 native audio engine** powered by Google Oboe with a custom **multi-touch canvas rendering & geometry hit-testing pipeline** and a pure JVM domain core.

---

## Key Engineering Highlights

### 1. Real-Time Lock-Free C++17 Audio Engine (`app/src/main/cpp`)
- **Zero-Allocation Audio Callback**: Built on **Google Oboe (`1.9.3`)** in `PerformanceMode::LowLatency` and `SharingMode::Exclusive` to minimize output latency on supported hardware.
- **Lock-Free SPSC Event Dispatch**: Touch triggers on the Android UI thread cross JNI into a lock-free single-producer/single-consumer ring buffer (`LockFreeQueue<TriggerEvent, 64>`) using C++11 acquire/release atomics—eliminating mutex contention or priority inversion on the real-time audio thread.
- **Pre-Decoded PCM & Sample-Rate Adaptation**: WAV assets are parsed and resampled ahead of time into 32-bit float PCM buffers (`SoundSlot` & `Resampler`) matching the hardware output sample rate, enabling deterministic polyphonic mixing (`Voice` pool with oldest-voice stealing and choke group cancellation).
- **Android 15+ 16 KB Page-Size Ready**: Compiled with NDK `r27c` (`27.3.13750724`), `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, and `-Wl,-z,max-page-size=16384` ELF alignment for full compatibility with 16 KB page-size arm64 devices.

### 2. Pure JVM Domain & Geometry Core (`:kit-core`)
- **Framework-Free Architecture**: `:kit-core` is a pure Kotlin/JVM module with zero `android.*` dependencies, enabling fast, deterministic JUnit 5 tests without emulator overhead.
- **Mathematical Hit-Testing Engine (`HitTester`)**: Evaluates multi-touch coordinates against elliptical and circular drum bounds (`RECT_OVAL`, `CIRCLE`), angled zone dividers, concentric inner zones, and priority ordering.
- **Self-Contained Kit Packaging (`.vrkendang`)**: Implements binary/ZIP package serialization (`KitPackageReader`, `KitPackageWriter`), strict schema/asset validation (`KitValidator`, `WavParser`, `PngValidator`), and user layout override merging (`LayoutMerger`).

### 3. Custom Multi-Touch Canvas & Layered Skin Rendering (`:app`)
- **Responsive Multi-Pointer Tracking (`KendangBoardView`)**: Handles simultaneous `ACTION_DOWN` / `ACTION_POINTER_DOWN` touch events with normalized `[0.0, 1.0]` canvas-to-screen coordinate mapping (`CanvasCoordinateMapper`).
- **Layered Visual Rendering**: Supports procedural vector rendering alongside custom PNG skin & 3D body layers (`body`, `head`, `divider` overlays) loaded and validated via `KitImageLoader`.

### 4. Strict Debug / Release Variant Isolation
- **In-App Kit Authoring (Debug Only)**: The `debug` source set includes an interactive authoring suite (`DevAuthoringActivity`, `DevKitAuthoringSession`, `DevSoundInstaller`, `DevImageInstaller`) for designing custom drum layouts, importing WAV/PNG assets, and exporting `.vrkendang` kits directly on-device.
- **Zero-Footprint Release Builds**: The `release` source set binds to no-op stubs (`DevBridgeProvider`, `SettingsMenuProviderImpl`, `ZoneGuideOverlayProvider`). Continuous Integration automatically inspects compiled `classes*.dex` bytecode in the release APK to guarantee zero leakage of developer-mode symbols.

---

## System Architecture

```mermaid
flowchart LR
    subgraph UI["Android UI Thread (:app)"]
        Touch["KendangBoardView\n(Multi-Touch Event)"]
        Mapper["CanvasCoordinateMapper"]
        Bridge["AudioEngine (JNI Bridge)"]
    end

    subgraph Core["Pure JVM Domain (:kit-core)"]
        HitTest["HitTester\n(Ellipse / Divider / Inner Zone)"]
        Model["KitData / DrumData / ZoneData"]
        PkgIO["KitPackageReader & Writer\n(.vrkendang / WavParser / PngValidator)"]
    end

    subgraph Native["Native Audio Thread (C++17 / Oboe)"]
        Queue["LockFreeQueue<TriggerEvent, 64>\n(Acquire / Release Atomics)"]
        Callback["AudioEngine::onAudioReady()"]
        Voices["Polyphonic Voice Pool (16 Voices)\n+ Choke Group Cancellation"]
        Slots["Pre-Loaded Float PCM SoundSlots"]
    end

    Touch --> Mapper
    Mapper --> HitTest
    Model --> HitTest
    HitTest -- "Zone Match (slotIndex)" --> Bridge
    Bridge -- "JNI triggerSound()" --> Queue
    Queue --> Callback
    Slots --> Voices
    Voices --> Callback
```

---

## Repository Structure

```text
├── kit-core/                  # Pure Kotlin/JVM module (zero Android dependencies)
│   └── src/
│       ├── main/kotlin/       # Domain models, HitTester, WavParser, PngValidator,
│       │                      # KitValidator, LayoutMerger, .vrkendang Package I/O
│       └── test/kotlin/       # JUnit 5 unit tests & built-in kit asset validation
├── app/                       # Android application module
│   └── src/
│       ├── main/
│       │   ├── cpp/           # C++17 Oboe AudioEngine, LockFreeQueue, Resampler, Voice
│       │   ├── kotlin/        # Custom Views, Audio/Image loaders, Repository, Activities
│       │   └── assets/kits/   # Bundled percussion kit definitions & WAV samples
│       ├── debug/kotlin/      # In-app Developer Kit Authoring toolset (Debug-only)
│       ├── release/kotlin/    # Zero-overhead no-op release implementations
│       ├── test/kotlin/       # JVM & Robolectric unit tests for UI, Audio & Repository
│       └── testDebug/kotlin/  # Integration & authoring session unit tests
├── openspec/                  # Spec-Driven Development artifacts (specs, design, tasks)
├── docs/                      # Technical documentation & kit release guides
└── .github/workflows/         # Automated CI pipeline (tests, DEX audit, APK artifacts)
```

---

## Spec-Driven AI Engineering Workflow

This project is developed using a structured **Spec-Driven Development (SDD)** workflow via **OpenSpec** ([`openspec/`](openspec/)) and strict repository engineering guardrails ([`AGENTS.md`](AGENTS.md)):
1. **Formal Specifications First**: Every capability (`audio-engine`, `kendang-board`, `kit-data`, `kit-authoring`, `calibration-mode`, `ci-build`) is specified with explicit requirements and scenarios under [`openspec/specs/`](openspec/specs/) prior to implementation.
2. **Scoped Change Proposals & Design Decisions**: Feature additions are tracked through isolated change proposals, architectural trade-off records, and verifiable task checklists under [`openspec/changes/`](openspec/changes/).
3. **Automated Verification**: Changes are validated against unit tests (`:kit-core:test`, `:app:testDebugUnitTest`) and CI bytecode assertions before archival.

---

## Build & Verification

### Prerequisites
- **JDK**: 17
- **Android SDK**: Compile SDK 34, Min SDK 26, Target SDK 34
- **Android NDK**: `27.3.13750724`
- **CMake**: `3.22.1`

### Gradle Commands

Run pure JVM domain unit tests and built-in kit integrity validation:
```bash
./gradlew :kit-core:test
```

Run Android unit & Robolectric tests (including debug authoring & integration suites):
```bash
./gradlew :app:testDebugUnitTest
```

Build the Debug APK (includes Developer Kit Authoring mode):
```bash
./gradlew assembleDebug
```

Build the Release APK (production variant, stripped of developer authoring classes):
```bash
./gradlew assembleRelease
```

---

## License & Copyright

**Copyright © 2026 Rudianto Eko Prasetyo (@Vanderrhud). All Rights Reserved.**

This repository is made publicly visible under a **Proprietary / Source-Available License** strictly for **technical evaluation, code review, and portfolio assessment**.

- You **may** view, inspect, and clone this repository locally for evaluation purposes.
- You **may not** copy, redistribute, modify, sublicense, extract bundled audio/visual assets, or publish derivative works or compiled packages (APK/AAB) for commercial or non-commercial distribution without prior written permission from the copyright holder.

See the [LICENSE](LICENSE) file for the full legal terms.
