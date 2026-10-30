# Tasks

## 1. Setup Proyek Android & Struktur Gradle

- [ ] 1.1 Inisialisasi proyek Android baru dengan Kotlin DSL (`build.gradle.kts`) — pastikan `settings.gradle.kts`, `app/build.gradle.kts`, dan `gradle/libs.versions.toml` terbuat dengan benar dan project dapat di-sync Gradle tanpa error.
- [ ] 1.2 Konfigurasi NDK dan CMake di `app/build.gradle.kts` (`externalNativeBuild`, `ndkVersion`) — verifikasi dengan menjalankan `./gradlew app:externalNativeBuildDebug` dan tidak ada error konfigurasi NDK.
- [ ] 1.3 Tambahkan dependensi Oboe via Prefab Maven (`google.oboe:oboe`) di `app/build.gradle.kts` dan aktifkan `buildFeatures { prefab = true }` — verifikasi dengan `./gradlew dependencies` yang menunjukkan Oboe resolved.
- [ ] 1.4 Kunci orientasi landscape di `AndroidManifest.xml` (`screenOrientation="landscape"`, `configChanges`) — verifikasi dengan menjalankan APK di emulator dan memutar perangkat: orientasi tidak berubah.
- [ ] 1.5 Buat file `CMakeLists.txt` kosong dengan `find_package(oboe REQUIRED CONFIG)` dan target library `kendang_audio` — verifikasi bahwa `./gradlew assembleDebug` berhasil menemukan Oboe tanpa error linker.

## 2. Infrastruktur CI (GitHub Actions)

- [ ] 2.1 Buat file `.github/workflows/build-apk.yml` dengan trigger `push: branches: [main]` dan `workflow_dispatch`, menggunakan `ubuntu-latest`, JDK 17 Temurin, dan `cache: gradle` — verifikasi dengan push kosong ke `main` dan workflow muncul di tab Actions GitHub.
- [ ] 2.2 Tambahkan step `./gradlew test assembleDebug --stacktrace` dan step `upload-artifact` untuk file `app/build/outputs/apk/debug/app-debug.apk` (retensi 7 hari) — verifikasi: setelah push, artifact `Kendang-Debug-APK` muncul di halaman run workflow dan bisa diunduh.
- [ ] 2.3 Buat Gradle task `generateDummyWav` yang menghasilkan 10 file WAV sintetik (nada sinus dengan decay, format 48 kHz 16-bit Mono) di `app/src/main/assets/kits/new_metro/sounds/` dengan frekuensi: Bass-1=65Hz, Bass-2=90Hz, Bass-3=130Hz, Bass-4=180Hz, Bass-5=230Hz, Sedang-1=320Hz, Sedang-2=460Hz, Kecil1-1=620Hz, Kecil2-1=780Hz, Kecil2-2=980Hz — verifikasi: task berjalan tanpa error dan 10 file `.wav` tersedia di path assets.
- [ ] 2.4 Pastikan `generateDummyWav` dipanggil sebelum `preBuild` sehingga berjalan otomatis di setiap build CI — verifikasi: `./gradlew assembleDebug` menghasilkan APK yang mengandung 10 file WAV dummy (gunakan `aapt dump badging` atau unzip untuk memverifikasi).

## 3. Model Data Kit & Pembacaan JSON

- [ ] 3.1 Definisikan skema JSON kit (file `docs/kit-schema.md` atau komentar kode) dan buat file `app/src/main/assets/kits/new_metro/kit.json` untuk kit New Metro dengan 4 kendang, koordinat relatif, dan referensi ke 10 file suara — verifikasi dengan membaca file JSON dan memastikan semua field wajib ada.
- [ ] 3.2 Buat data class Kotlin: `KitData`, `DrumData`, `DrumAreaData` (di modul/package tanpa import `android.*`) — verifikasi: data class dapat di-instantiate dalam unit test JVM biasa.
- [ ] 3.3 Implementasikan `KitJsonParser` (Kotlin murni, gunakan `kotlinx.serialization` atau `org.json`) yang mem-parsing `kit.json` menjadi `KitData` dan melempar exception bermakna untuk JSON tidak valid — verifikasi: unit test `KitJsonParserTest` mencakup kasus valid, JSON rusak, dan field wajib yang hilang, semua lulus di `./gradlew test`.
- [ ] 3.4 Implementasikan `KitRepository` yang menerapkan logika Dual-Source: baca kit dari `Context.getExternalFilesDir("kits")` terlebih dahulu; jika tidak ada, baca dari `assets/kits/` — verifikasi: unit test (dengan mock/fake untuk `AssetManager` dan `File`) membuktikan prioritas storage-over-assets berjalan benar.

## 4. Mesin Audio C++ (Oboe)

- [ ] 4.1 Buat file `AudioEngine.cpp` dan `AudioEngine.h` dengan class `AudioEngine` yang membuka Oboe stream dengan `PerformanceMode::LowLatency`, `SharingMode::Exclusive`, dan sample rate dari query `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` yang di-pass dari Kotlin via JNI — verifikasi: `./gradlew assembleDebug` berhasil compile C++ tanpa warning.
- [ ] 4.2 Implementasikan Voice Pool: struct `Voice` (pointer ke data PCM, posisi playback, aktif/tidak) sebagai array statis 32 elemen — verifikasi: inisialisasi pool di konstruktor dan tidak ada `malloc`/`new` di dalam audio callback.
- [ ] 4.3 Implementasikan Oboe audio callback (`onAudioReady`) yang me-mix semua slot Voice aktif ke output buffer — verifikasi: kompilasi bersih; pengujian audio output dilakukan secara manual di HP.
- [ ] 4.4 Implementasikan Voice Stealing: fungsi `findSlot()` yang mengembalikan slot kosong atau slot tertua jika semua terisi, dengan micro-fade-out 1ms pada slot yang dicuri — verifikasi: unit test C++ (jika ada test runner CMake) atau verifikasi manual saat rolling intensif di HP.
- [ ] 4.5 Implementasikan `loadSound(const float* data, int frameCount, int soundIndex)` yang menyimpan pointer buffer ke tabel suara mesin audio — verifikasi: fungsi dapat dipanggil dari JNI tanpa crash (log `adb logcat` bersih).
- [ ] 4.6 Implementasikan `playSound(int soundIndex)` yang mengambil slot dari pool dan memulai pemutaran suara yang dimaksud — verifikasi: setelah diintegrasikan dengan UI, ketukan di zona menghasilkan suara yang terdengar di HP.
- [ ] 4.7 Implementasikan penanganan audio lifecycle di `AudioEngine`: metode `pause()` dan `resume()` yang menutup dan membuka ulang Oboe stream, dan callback `onErrorAfterClose` yang me-restart stream saat perangkat audio berganti (headphone colok/cabut) — verifikasi manual: minimize aplikasi → suara berhenti; buka kembali → suara jalan; cabut headphone → audio pindah ke speaker tanpa crash.

## 5. JNI Bridge (Kotlin ↔ C++)

- [ ] 5.1 Buat `AudioEngineJni.kt` dengan deklarasi fungsi `external` (`initEngine`, `loadSound`, `playSound`, `pauseEngine`, `resumeEngine`, `destroyEngine`) dan implementasi JNI-nya di `AudioEngineJni.cpp` — verifikasi: kompilasi bersih, tidak ada `UnsatisfiedLinkError` saat `System.loadLibrary`.
- [ ] 5.2 Implementasikan alur loading suara: Kotlin membaca WAV dari sumber yang benar (assets atau storage) menjadi `DirectByteBuffer`, mengurai header WAV untuk mendapatkan pointer ke data PCM, lalu menyerahkan ke C++ via `loadSound` JNI — verifikasi: setelah kit dimuat, 10 suara terdaftar di mesin audio tanpa exception atau log error.
- [ ] 5.3 Hubungkan lifecycle aplikasi (`onPause`/`onResume` di Activity) ke `pauseEngine`/`resumeEngine` JNI — verifikasi manual: tekan Home → `adb logcat` menunjukkan `pause()` dipanggil; buka kembali → `resume()` dipanggil.

## 6. Logika Hit-Testing (Kotlin Murni)

- [ ] 6.1 Buat sealed class atau data class `DrumShape` (`Circle`, `Oval`) dan `DrumArea` yang merepresentasikan setiap zona akustik beserta metode geometrinya — verifikasi: class dapat di-instantiate dalam JVM unit test tanpa import `android.*`.
- [ ] 6.2 Implementasikan `HitTester.findHitArea(x: Float, y: Float, drums: List<DrumData>): DrumAreaData?` menggunakan rumus aljabar yang disepakati (ellipse equation untuk kulit, lingkaran C_div untuk pembatas bulan sabit Bass, perbandingan `y` untuk zona horizontal) — verifikasi: `HitTesterTest` dengan koordinat sampel mencakup semua zona Bass (1–5), Sedang (1–2), Kecil1 (1), Kecil2 (1–2), dan kasus di luar kulit; semua lulus di `./gradlew test`.
- [ ] 6.3 Verifikasi unit test hit-testing berjalan di GitHub Actions sebagai bagian dari workflow CI — verifikasi: lihat tab Actions setelah push, step `test` lulus sebelum `assembleDebug`.

## 7. `KendangBoardView` — Rendering & Touch

- [ ] 7.1 Buat `KendangBoardView : View` dengan override `onMeasure` yang menerapkan Aspect-Fit berbasis `height`: hitung faktor skala `scale = boardHeight / referenceHeight` dan pastikan rasio piksel X sama dengan Y — verifikasi: di emulator dengan rasio berbeda (16:9 vs 20:9), kulit lingkaran Bass tampil sebagai lingkaran sempurna (ukur dengan mata atau screenshot test).
- [ ] 7.2 Implementasikan `onDraw(canvas: Canvas)` yang menggambar setiap kendang menggunakan koordinat yang sudah di-scale: kulit oval/lingkaran diisi warna dasar, badan kendang ditarik ke bawah sebagai bentuk hiasan — verifikasi: menjalankan APK di HP menampilkan 4 kendang kit New Metro di posisi yang sesuai `kit.json`.
- [ ] 7.3 Implementasikan `onTouchEvent(event: MotionEvent)` yang menangkap `ACTION_DOWN` dan `ACTION_POINTER_DOWN` dari semua pointer, mengekstrak koordinat (x, y) setiap pointer, dan menyerahkan ke `HitTester.findHitArea()` — verifikasi: `adb logcat` menampilkan zona yang terdeteksi untuk setiap ketukan saat testing manual.
- [ ] 7.4 Hubungkan hasil hit-testing ke pemanggilan `AudioEngineJni.playSound(soundIndex)` — verifikasi manual di HP: ketukan di setiap zona membunyikan suara dummy dengan nada yang sesuai (frekuensi berbeda per zona).
- [ ] 7.5 Implementasikan visual feedback zona dipukul: saat zona terdeteksi, gambar overlay transparan (alpha ~0.4, warna putih) di atas area zona tersebut yang memudar dalam 100ms menggunakan `ValueAnimator` atau `postInvalidateDelayed` — verifikasi manual: ketukan menghasilkan kilatan cahaya sesaat yang terlihat di zona yang dipukul.
- [ ] 7.6 Aktifkan `FLAG_KEEP_SCREEN_ON` di Activity dan konfigurasi Immersive Sticky Mode (`WindowInsetsController` atau `systemUiVisibility` untuk API 26+) — verifikasi manual: biarkan aplikasi idle 30 detik → layar tidak mati; geser di tepi layar saat bermain → nav bar tidak muncul.

## 8. Header & Selector Kit

- [ ] 8.1 Buat layout utama Activity dengan `ConstraintLayout`: `HeaderView` (tinggi 48dp, pinned atas) dan `KendangBoardView` (mengisi sisa layar) — verifikasi: di HP dan emulator tablet, header tampak ramping di atas dan area bermain mengisi sisa layar.
- [ ] 8.2 Implementasikan tombol Kit Selector di sisi kiri header yang menampilkan nama kit aktif ("New Metro") dan, saat diklik, memunculkan dialog/bottom sheet daftar kit yang tersedia — verifikasi: klik tombol menampilkan dialog dengan "New Metro" sebagai satu-satunya pilihan; memilihnya menutup dialog (tidak ada perubahan karena sudah aktif).
- [ ] 8.3 Implementasikan tombol Settings (⚙) di sisi kanan header yang membuka menu pengaturan (dialog/bottom sheet kosong untuk MVP, berisi tombol "Kalibrasi Tata Letak") — verifikasi: klik tombol ⚙ menampilkan menu yang berisi opsi Kalibrasi.

## 9. Mode Kalibrasi

- [ ] 9.1 Buat `CalibrationOverlayView` yang, ketika aktif, menampilkan garis panduan zona akustik (lingkaran C_div Bass, garis horizontal pembagi) di atas setiap kendang sebagai overlay transparan — verifikasi: aktifkan mode kalibrasi dari menu → garis panduan terlihat; tutup mode kalibrasi → garis menghilang.
- [ ] 9.2 Implementasikan logika drag-and-drop di mode kalibrasi: deteksi jari yang menyentuh kendang mana, lalu perbarui posisi `DrumData` secara real-time mengikuti gerakan jari — verifikasi: geser Bass ke posisi baru → Bass mengikuti jari secara real-time.
- [ ] 9.3 Implementasikan slider atau kontrol ukuran per kendang yang mengubah radius/radius_x/radius_y di `DrumData` secara real-time — verifikasi: menggerakkan slider mengubah ukuran kendang yang terlihat di layar.
- [ ] 9.4 Implementasikan tombol "Simpan" yang menulis `KitData` yang sudah dimodifikasi menjadi file `kit.json` baru di `Context.getExternalFilesDir("kits")/new_metro/kit.json` dan langsung mereload board — verifikasi: simpan → board diperbarui sesuai posisi baru tanpa restart aplikasi.
- [ ] 9.5 Implementasikan tombol "Bagikan / Ekspor" yang membuka Android Share Sheet dengan file `kit.json` terkini sebagai lampiran — verifikasi: klik Bagikan → Share Sheet muncul dengan file JSON; kirim ke Telegram atau Google Drive berhasil.
- [ ] 9.6 Implementasikan tombol "Reset" yang mengembalikan posisi dan ukuran ke nilai default dari kit.json bawaan assets — verifikasi: setelah menggeser posisi, tekan Reset → semua kendang kembali ke posisi default.

## 10. Verifikasi Integrasi & Kriteria Keberhasilan

- [ ] 10.1 Verifikasi 10 zona akustik: instal APK di HP fisik, ketuk setiap zona satu per satu — pastikan 10 nada dummy yang berbeda terdengar dan sesuai zona yang diketuk.
- [ ] 10.2 Verifikasi multi-touch: sentuh 2–5 zona berbeda secara bersamaan — semua suara harus berbunyi bersamaan tanpa ada yang terlewat.
- [ ] 10.3 Verifikasi rolling/stacking: ketuk satu zona berulang kali cepat — suara lama tidak terpotong; suara baru menumpuk di atas.
- [ ] 10.4 Verifikasi sentuhan di badan kendang dan background — tidak ada suara yang muncul.
- [ ] 10.5 Verifikasi CI: lakukan push ke `main` dengan perubahan kecil → workflow Actions berhasil (centang hijau), APK tersedia di Artifacts, unit test lulus — verifikasi di halaman GitHub Actions.

## Workflow follow-up

- Rekam dan integrasikan aset suara WAV asli kendang New Metro (dilakukan oleh pemilik produk secara terpisah, menggantikan file dummy).
- Setelah aset asli tersedia dan fitur berjalan sesuai kriteria keberhasilan PRD, archive change ini dengan `/openspec-archive-change`.
- Putuskan dan implementasikan detail animasi efek denyut final (berdasarkan hasil tes manual di HP).
