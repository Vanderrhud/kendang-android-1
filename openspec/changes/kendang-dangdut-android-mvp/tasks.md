# Tasks

## 1. Setup Proyek Android & Struktur Gradle

- [ ] 1.1 Inisialisasi proyek Android baru bernama **VR Kendang** dengan Kotlin DSL (`build.gradle.kts`) menggunakan struktur Gradle multi-modul: modul `:app` (Android, NDK/CMake) dan modul `:kit-core` (Kotlin/JVM murni, tidak ada dependensi `android.*`) — verifikasi dengan `./gradlew :kit-core:compileKotlin` berhasil, dan `./gradlew app:preBuild` berhasil tanpa error sync.
- [ ] 1.2 Konfigurasi NDK dan CMake di `app/build.gradle.kts` (`externalNativeBuild`, `ndkVersion`) — verifikasi dengan `./gradlew app:externalNativeBuildDebug` tanpa error konfigurasi NDK.
- [ ] 1.3 Tambahkan dependensi Oboe via Prefab Maven (`com.google.oboe:oboe`) di `app/build.gradle.kts` dan aktifkan `buildFeatures { prefab = true }` — verifikasi dengan `./gradlew dependencies` yang menunjukkan Oboe resolved.
- [ ] 1.4 Kunci orientasi landscape di `AndroidManifest.xml` (`screenOrientation="landscape"`, `configChanges`) — verifikasi dengan menjalankan APK di emulator dan memutar perangkat: orientasi tidak berubah.
- [ ] 1.5 Buat file `CMakeLists.txt` dengan `find_package(oboe REQUIRED CONFIG)` dan target library `kendang_audio` — verifikasi bahwa `./gradlew assembleDebug` berhasil menemukan Oboe tanpa error linker.
- [ ] 1.6 Tambahkan dependensi `kotlinx.serialization-json` ke modul `:kit-core` dan pastikan plugin `kotlinx-serialization` aktif di Kotlin DSL — verifikasi dengan class dummy `@Serializable` di `:kit-core` yang dapat dikompilasi tanpa error.

## 2. Infrastruktur CI (GitHub Actions)

- [ ] 2.1 Buat file `.github/workflows/build-apk.yml` dengan trigger `push: branches: [main]` dan `workflow_dispatch`, menggunakan `ubuntu-latest`, JDK 17 Temurin, dan `cache: gradle` — verifikasi dengan push ke `main` dan workflow muncul di tab Actions GitHub.
- [ ] 2.2 Konfigurasi workflow: step `./gradlew test` (unit test `:kit-core`) dijalankan SEBELUM `assembleDebug`; kegagalan test menghentikan build APK — verifikasi: buat satu unit test yang sengaja gagal, push, dan pastikan workflow merah sebelum sampai ke step assembleDebug.
- [ ] 2.3 Tambahkan step `./gradlew assembleDebug --stacktrace` dan step `upload-artifact` untuk file `app/build/outputs/apk/debug/app-debug.apk` (retensi 7 hari) — verifikasi: setelah push, artifact `VRKendang-Debug-APK` muncul di halaman run workflow dan bisa diunduh; pastikan APK tidak mengandung file `.wav` apapun di direktori `assets/`.

## 3. Model Data Kit & Modul `:kit-core`

- [ ] 3.1 Definisikan skema `kit.json` sesuai design.md D11 dan buat file `app/src/main/assets/kits/new-metro/kit.json` untuk kit New Metro dengan 4 kendang, koordinat relatif, dan semua zona berstatus `"sound": null` (unassigned) — verifikasi: file JSON valid dan bisa di-parse oleh `kotlinx.serialization`.
- [ ] 3.2 Buat data class Kotlin di `:kit-core`: `KitData`, `DrumData`, `ZoneData`, `LayoutOverride`, dll. menggunakan `@Serializable` — verifikasi: data class dapat di-instantiate dalam unit test JVM tanpa `android.*`.
- [ ] 3.3 Implementasikan `KitJsonParser` di `:kit-core` yang mem-parsing `kit.json` menjadi `KitData` menggunakan `kotlinx.serialization`, mendukung `sound: null` untuk zona unassigned, dan melempar exception bermakna untuk JSON tidak valid — verifikasi: unit test `KitJsonParserTest` mencakup kasus: JSON valid (semua sound null), JSON valid (sebagian sound terisi), JSON rusak, dan field wajib hilang; semua lulus di `./gradlew :kit-core:test`.
- [ ] 3.4 Implementasikan `KitValidator` di `:kit-core` yang memvalidasi struktur, rentang nilai (`canvasAspect > 0`, `center.x/y` di 0–1, `rx/ry > 0 ≤ 1`, `gain` di 0–2.0, region sesuai model), dan memastikan field `sound` hanya null atau path relatif tanpa `..` — verifikasi: unit test validasi mencakup kasus edge: nilai gain 2.1 ditolak, path `../evil.wav` ditolak, dll.
- [ ] 3.5 Implementasikan `KitPackageReader` di `:kit-core` yang membaca paket `.vrkendang` (ZIP): ekstrak `kit.json` dan `sounds/*.wav`, cek path traversal (tolak entri mengandung `..` atau path absolut), batasi jumlah entri dan ukuran total — verifikasi: unit test `KitPackageReaderTest` mencakup paket valid, paket path traversal, dan paket ZIP bomb; semua skenario ditangani dengan benar.
- [ ] 3.6 Implementasikan `LayoutMerger` di `:kit-core` yang menerapkan `LayoutOverride` ke `KitData`: ganti `center`, kalikan `rx`/`ry` dengan `scale`, jepit ke batas valid, abaikan id yang tidak dikenal — verifikasi: unit test `LayoutMergerTest` mencakup override sebagian, override id tidak dikenal (diabaikan), dan nilai scale di luar batas (dijepit).

## 4. Hit-Testing di `:kit-core`

- [ ] 4.1 Implementasikan `HitTester` di `:kit-core` (Kotlin murni, tanpa `android.*`) dengan fungsi `findZone(kit: KitData, canvasX: Float, canvasY: Float): ZoneData?` yang mengimplementasikan model `bass`, `split`, dan `single` sesuai design.md D7 — verifikasi: `./gradlew :kit-core:compileKotlin` berhasil.
- [ ] 4.2 Tulis unit test `HitTesterTest` untuk model `bass` (9 kasus: 5 zona + 4 batas), model `split` (4 kasus: atas, bawah, dalam tapi tepat di garis, di luar oval), model `single` (2 kasus: dalam, di luar) — verifikasi: semua test lulus di `./gradlew :kit-core:test`.
- [ ] 4.3 Verifikasi di unit test bahwa `findZone()` mengembalikan `null` (bukan exception) untuk titik di luar semua kendang, dan bahwa urutan evaluasi mengikuti urutan array `drums` di `kit.json` — verifikasi: skenario overlap bounding box antar kendang menghasilkan kendang pertama di array yang menang.

## 5. Mesin Audio C++ (Oboe)

- [ ] 5.1 Buat `AudioEngine.cpp` / `AudioEngine.h` dengan class `AudioEngine` yang membuka Oboe stream dengan `PerformanceMode::LowLatency`, `SharingMode::Exclusive`, dan `SampleRate` dari native rate perangkat yang di-pass dari Kotlin via JNI — verifikasi: `./gradlew assembleDebug` berhasil compile C++ tanpa warning.
- [ ] 5.2 Implementasikan Voice Pool: struct `Voice` (pointer ke PCM asli, PCM yang di-resample, posisi playback, aktif/tidak) sebagai array statis 32 elemen yang dialokasikan di konstruktor; tidak ada `malloc`/`new` di dalam audio callback — verifikasi: inspeksi kode bahwa callback bebas alokasi dinamis.
- [ ] 5.3 Implementasikan resampling PCM di sisi C++: saat stream dibuka atau dibuka ulang, buat salinan PCM yang di-resample ke `streamSampleRate` dari PCM asli menggunakan interpolasi linier; simpan PCM asli agar bisa di-resample ulang jika rate berubah — verifikasi manual: suara WAV dengan sample rate berbeda diputar dengan pitch benar di HP.
- [ ] 5.4 Implementasikan Oboe audio callback (`onAudioReady`) yang: (1) baca event dari SPSC ring buffer, (2) mix semua Voice aktif ke output buffer, (3) terapkan master gain + soft-clip limiter — verifikasi: kompilasi bersih; pengujian output dilakukan manual di HP.
- [ ] 5.5 Implementasikan SPSC ring buffer (ukuran tetap, lock-free) di C++ untuk jalur trigger dari thread UI ke audio callback; audio callback hanya membaca ring buffer dan tidak pernah memanggil JNI — verifikasi: review kode bahwa tidak ada mutex atau alokasi di dalam audio callback.
- [ ] 5.6 Implementasikan Voice Stealing: `findSlot()` mengembalikan slot kosong atau slot tertua jika semua terisi, dengan micro-fade-out 1ms pada slot yang dicuri — verifikasi manual: rolling sangat cepat di HP tidak menimbulkan bunyi pop.
- [ ] 5.7 Implementasikan JNI bridge:
    - `loadSound(ByteBuffer pcmData, int frameCount, int sampleRate, int slotIndex)`: Kotlin menyerahkan DirectByteBuffer; C++ menyalin PCM ke memori miliknya lalu melepas kebutuhan pada buffer Kotlin
    - `triggerSound(int slotIndex, float gain)`: memasukkan event ke ring buffer
    - `unloadSound(int slotIndex)`: membebaskan memori PCM slot
    - `releaseAll()`: unload semua slot
    - Verifikasi: unit test instrumentasi ringan di CI (atau verifikasi manual di HP) memastikan tidak ada crash saat load/unload/trigger berulang
- [ ] 5.8 Implementasikan siklus hidup audio: `pauseStream()` saat `onStop()`, `resumeStream()` saat `onStart()`, dan `AudioDeviceCallback` / `AudioManager.registerAudioDeviceCallback` untuk reconnect otomatis saat perangkat audio berganti — verifikasi manual: colok/cabut headphone tidak membuat stream mati permanen.

## 6. Integrasi Audio ↔ Kit Data (Kotlin, di `:app`)

- [ ] 6.1 Buat `SoundLoader` di `:app` yang membaca file WAV dari sumber yang diberikan (`InputStream`) menjadi `DirectByteBuffer` dan memeriksa header RIFF/WAVE PCM 16-bit sebelum menyerahkan ke JNI — verifikasi: unit test atau instrumentasi kecil membuktikan file non-WAV dan WAV non-PCM 16-bit ditolak dengan pesan galat yang jelas.
- [ ] 6.2 Buat `KitAudioLoader` di `:app` yang mengiterasi semua zona di `KitData`, memanggil `SoundLoader` untuk setiap zona yang `sound != null`, dan meneruskan PCM ke `AudioEngine.loadSound()`. Zona unassigned (`sound == null`) tidak memicu pemuatan apapun dan tidak menyebabkan error — verifikasi: log menunjukkan slot audio yang terisi vs yang dibiarkan kosong.
- [ ] 6.3 Implementasikan `KitRepository` di `:app` yang membaca daftar kit dari `assets/kits/` via `AssetManager` dan menggabungkan `KitData` dengan `LayoutOverride` dari `files/layouts/<kitId>.json` menggunakan `LayoutMerger` — verifikasi: unit test dengan fake `AssetManager` dan fake file system membuktikan penggabungan override benar.

## 7. Papan Kendang & Tampilan (`:app`)

- [ ] 7.1 Implementasikan `KendangBoardView` sebagai Custom View yang menerima `KitData` dan merender semua kendang (oval/lingkaran dengan `canvas.drawOval`) pada posisi dan ukuran sesuai `kit.json` dalam sistem koordinat relatif (D5) — verifikasi: di emulator, semua kendang tampil di posisi yang sesuai `kit.json`.
- [ ] 7.2 Hubungkan `KendangBoardView` ke `HitTester.findZone()`: di `onTouchEvent`, untuk setiap `ACTION_DOWN`/`ACTION_POINTER_DOWN`, konversi koordinat piksel ke koordinat kanvas lalu panggil `findZone()`. Jika zona ditemukan DAN zona memiliki sound (sound != null), kirim trigger ke `AudioEngine`. Jika zona ditemukan tapi sound null, tidak ada audio trigger — verifikasi dengan `adb logcat`: zona terdeteksi dicatat dengan statusnya.
- [ ] 7.3 Implementasikan visual feedback zona dipukul: saat zona terdeteksi (dengan atau tanpa suara), gambar overlay transparan (alpha ~0.4, warna putih) di atas area zona yang memudar dalam 100ms menggunakan `ValueAnimator` — verifikasi manual: ketukan menghasilkan kilatan cahaya pada zona yang dipukul, termasuk zona yang belum bersuara.
- [ ] 7.4 Aktifkan `FLAG_KEEP_SCREEN_ON` di Activity dan konfigurasi Immersive Sticky Mode — verifikasi manual: biarkan idle 30 detik → layar tidak mati; rolling di tepi layar → nav bar tidak muncul.

## 8. Header & Daftar Kit

- [ ] 8.1 Buat layout utama Activity dengan `ConstraintLayout`: `HeaderView` (tinggi 48dp, pinned atas) dan `KendangBoardView` (mengisi sisa layar) — verifikasi di HP/emulator: header ramping di atas, area bermain mengisi sisa layar.
- [ ] 8.2 Implementasikan tombol Kit Selector di header yang menampilkan nama kit aktif dan membuka dialog daftar kit yang dibaca dari `assets/kits/` — verifikasi: klik tombol → dialog daftar kit tampil (satu kit: New Metro); memilihnya memuat kit tersebut.
- [ ] 8.3 Implementasikan tombol Settings (⚙) di header yang membuka menu berisi: "Edit Tata Letak" (semua build) dan "Mode Developer" (hanya build debug) — verifikasi: di build debug, kedua opsi tampil; di build release, hanya "Edit Tata Letak" yang tampil.

## 9. Mode Kalibrasi Tata Letak (Pemain)

- [ ] 9.1 Buat `CalibrationOverlayView` yang menampilkan garis panduan zona akustik (oval kendang, busur Bass, garis horizontal split) di atas papan sebagai overlay transparan saat mode kalibrasi aktif — verifikasi: aktifkan mode kalibrasi → garis panduan tampil; tutup → garis menghilang.
- [ ] 9.2 Implementasikan drag-and-drop di mode kalibrasi: deteksi jari menyentuh kendang mana dan perbarui `center` di `KitData` efektif secara real-time — verifikasi: geser Bass → Bass mengikuti jari real-time.
- [ ] 9.3 Implementasikan slider skala ukuran per kendang yang mengubah `rx`/`ry` di `KitData` efektif secara real-time dalam batas 0.5x–1.5x — verifikasi: slider → ukuran kendang berubah di layar.
- [ ] 9.4 Implementasikan tombol "Simpan" yang menulis `LayoutOverride` (hanya `center` dan `scale` per kendang) ke `files/layouts/<kitId>.json` dan langsung me-reload board — verifikasi: simpan → board diperbarui; buka aplikasi kembali → posisi tersimpan masih terapkan.
- [ ] 9.5 Implementasikan tombol "Reset ke Bawaan" yang menghapus `files/layouts/<kitId>.json` dan me-reload board ke posisi bawaan `kit.json` — verifikasi: setelah menggeser posisi dan menyimpan, tekan Reset → semua kendang kembali ke posisi default.

## 10. Mode Developer — Alat Penyusun Kit (Hanya Build Debug)

- [ ] 10.1 Implementasikan source set `app/src/debug/` untuk kode Mode Developer; source set `release` hanya berisi stub kosong `DevModeEntryPoint` yang tidak melakukan apa pun — verifikasi: `./gradlew assembleRelease` menghasilkan APK yang tidak mengandung kode Mode Developer.
- [ ] 10.2 Implementasikan UI Mode Developer: tombol "Buka Kit (.vrkendang)", tombol "Buka Kit Bawaan sebagai Salinan Kerja", dan tampilan papan kendang yang dapat diedit (geser posisi, slider ukuran, slider gain, slider divider Bass).
- [ ] 10.3 Implementasikan "Buka Kit (.vrkendang)" via `ACTION_OPEN_DOCUMENT` dengan MIME type `*/*` (filter ekstensi `.vrkendang`): ekstrak via `KitPackageReader`, validasi via `KitValidator`, salin ke `cacheDir/dev-kit/`, load ke audio engine — verifikasi: buka file `.vrkendang` yang valid → kit dan suaranya langsung aktif; buka file dengan path traversal → ditolak dengan pesan galat jelas.
- [ ] 10.4 Implementasikan "Pilih Suara (WAV)" per zona via `ACTION_OPEN_DOCUMENT` dengan MIME type `audio/*`: `SoundLoader` memeriksa header RIFF/WAVE PCM 16-bit, salin ke `cacheDir/dev-kit/sounds/<zoneId>.wav`, muat ke audio engine via slot zone — verifikasi: pilih file WAV asli dari HP → zona langsung bersuara saat diketuk; pilih file MP3 → ditolak dengan pesan galat "Format harus WAV PCM 16-bit".
- [ ] 10.5 Implementasikan kontrol edit per zona: slider gain (0.0–2.0), dan status suara (nama file / "Unassigned") dengan tombol "Ganti Suara" — verifikasi: perubahan gain langsung terdengar saat zona diketuk.
- [ ] 10.6 Implementasikan kontrol parameter divider Bass (`dx`, `dy`, `r`) dengan slider atau input numerik; garis panduan busur pembatas diperbarui di papan secara real-time — verifikasi: ubah `r` divider → kurva busur di layar berubah real-time.
- [ ] 10.7 Implementasikan "Simpan / Ekspor (.vrkendang)" via `ACTION_CREATE_DOCUMENT`: zip ulang seluruh isi `cacheDir/dev-kit/` (kit.json + semua WAV yang dipasang) menjadi file `.vrkendang` di lokasi yang dipilih developer — verifikasi: simpan → file `.vrkendang` ada di penyimpanan HP; buka kembali file tersebut di Mode Developer → kit dan suara termuat dengan benar.
- [ ] 10.8 Implementasikan "Simpan ke file yang sama" (timpa): jika URI file asal sudah ada, tulis ke output stream URI yang sama tanpa membuat file baru atau file `.bak` — verifikasi: simpan dua kali ke file yang sama → hanya satu file yang ada di HP.

## 11. Verifikasi Integrasi & Kriteria Keberhasilan

- [ ] 11.1 Alur Mode Developer end-to-end: buka Mode Developer → buka salinan kit New Metro → pasangkan file WAV asli untuk zona Bass-1 → ketuk zona Bass-1 → suara berbunyi seketika.
- [ ] 11.2 Pasangkan WAV ke semua 10 zona satu per satu → setiap zona bersuara berbeda sesuai rekaman yang dipasang.
- [ ] 11.3 Verifikasi multi-touch: sentuh 2–5 zona berbeda (yang sudah bersuara) bersamaan → semua suara berbunyi bersamaan tanpa ada yang terlewat.
- [ ] 11.4 Verifikasi rolling/stacking: ketuk satu zona berulang cepat → suara lama tidak terpotong; suara baru menumpuk di atas (polyphony).
- [ ] 11.5 Verifikasi zona unassigned: ketuk zona yang masih null → tidak ada suara, tidak ada crash; visual feedback (highlight) tetap tampil.
- [ ] 11.6 Ekspor kit yang sudah dikonfigurasi sebagai `.vrkendang` → file berhasil tersimpan di HP; impor kembali file tersebut → kit dan suara termuat dengan benar.
- [ ] 11.7 Verifikasi CI: push ke `main` → workflow Actions berjalan, unit test `:kit-core` lulus, APK debug berhasil dibuat dan tersedia di Artifacts; APK tidak mengandung file `.wav` di `assets/`.

## Workflow Follow-up

- Setelah semua 10 zona dikonfigurasi dengan rekaman kendang asli dan kit terasa meyakinkan, ekspor kit ke `.vrkendang` → ekstrak ke `assets/kits/new-metro/` di repo → commit, push → CI membuat APK dengan kit New Metro sebagai kit bawaan pertama.
- Putuskan dan sempurnakan detail animasi visual feedback (berdasarkan hasil uji manual di HP).
- Bila kit sudah matang dan keputusan rilis sudah siap, archive change ini dengan `/openspec-archive-change`.
