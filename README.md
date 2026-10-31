# VR Kendang (Android)

Aplikasi kendang dangdut virtual untuk Android dengan latensi rendah menggunakan Oboe C++ NDK dan antarmuka sentuh multi-touch kanvas kustom.

## Struktur Modul

Proyek menggunakan arsitektur modular Gradle:
- `:kit-core` — Modul Kotlin/JVM murni (tanpa dependensi `android.*`). Berisi model data kit (`KitData`, `DrumData`, `ZoneData`), parsing JSON (`KitJsonParser`), validasi integritas kit (`KitValidator`), template bawaan (`KitFactory`), I/O berkas `.vrkendang` (`KitPackageReader`, `KitPackageWriter`), dan penggabungan tata letak (`LayoutMerger`).
- `:app` — Modul Android aplikasi utama:
  - Audio Engine: C++ NDK (`AudioEngine`, `Resampler`, `LockFreeQueue`, `SoundSlot`, `Voice`) dengan Oboe Prefab library.
  - Komponen View: `KendangBoardView`, `CanvasCoordinateMapper`, `ZoneHighlightDrawer`, `HeaderView`, `CalibrationOverlayView`.
  - Mode Developer (khusus build `debug`): `DevKitAuthoringBridge`, `DevKitAuthoringSession`, `DevSoundInstaller`.
  - Build `release`: No-op bridge stub (`ReleaseDevBridge`), tanpa simbol developer mode di dalam DEX.

## Prasyarat Lingkungan

- Android Studio / Android SDK (Platform 34, Min SDK 24, Target SDK 34)
- JDK 17
- Android NDK 26.1.10909125
- CMake 3.22.1

## Perintah Build & Pengujian

- Menjalankan unit test modul data dan validasi kit bawaan:
  ```bash
  ./gradlew :kit-core:test
  ```
- Menjalankan unit test modul aplikasi:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- Membangun APK Debug:
  ```bash
  ./gradlew assembleDebug
  ```
- Membangun APK Release:
  ```bash
  ./gradlew assembleRelease
  ```

## Rilis Kit Bawaan

Untuk memasukkan kit baru hasil Mode Developer sebagai kit bawaan ke dalam APK, ikuti panduan lengkap di:
[docs/rilis-kit.md](docs/rilis-kit.md)
