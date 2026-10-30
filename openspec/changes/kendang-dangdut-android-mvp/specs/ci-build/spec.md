# Spec Delta

## Purpose

Menyediakan pipeline build otomatis yang menghasilkan APK debug siap-install setiap kali kode baru di-push ke repositori, dengan unit test otomatis untuk logika inti yang tidak memerlukan perangkat fisik. Build CI bersih tanpa aset audio bawaan.

## ADDED Requirements

### Requirement: Build APK debug otomatis setiap push ke main
Sistem CI SHALL secara otomatis memulai proses build APK debug setiap kali ada push ke branch `main`, tanpa memerlukan tindakan manual selain `git push`.

#### Scenario: Push ke main memicu build
- **WHEN** kode baru di-push ke branch `main` di GitHub
- **THEN** GitHub Actions workflow otomatis berjalan dan mencoba membangun APK debug

#### Scenario: Build dapat dipicu manual
- **WHEN** pengguna memilih opsi "Run workflow" di halaman GitHub Actions
- **THEN** workflow berjalan dan membangun APK debug

### Requirement: APK debug tersedia sebagai artifact yang bisa diunduh
Setiap build yang berhasil SHALL mengunggah file APK debug sebagai GitHub Actions artifact yang dapat diunduh langsung dari halaman Actions di browser atau HP.

#### Scenario: APK tersedia setelah build berhasil
- **WHEN** workflow berjalan dan build berhasil
- **THEN** file APK debug tersedia di tab Artifacts pada halaman run workflow untuk diunduh

### Requirement: APK dibangun tanpa aset audio bawaan
Build SHALL menghasilkan APK bersih tanpa file WAV bawaan dan tanpa menjalankan generator nada sintetis apapun. Semua file audio dimasukkan oleh developer langsung dari penyimpanan HP via Mode Developer.

#### Scenario: APK tidak mengandung file WAV di direktori assets
- **WHEN** workflow berhasil membangun APK debug
- **THEN** direktori `assets/kits/` di dalam APK tidak mengandung file `.wav` apapun

### Requirement: Unit test `:kit-core` berjalan di CI sebelum build APK
Workflow SHALL menjalankan seluruh unit test modul `:kit-core` sebelum proses `assembleDebug`. Jika ada test yang gagal, build dihentikan dan workflow ditandai gagal.

#### Scenario: Unit test lulus sebelum APK dibangun
- **WHEN** workflow berjalan pada push ke main
- **THEN** step `./gradlew test` berhasil (seluruh unit test `:kit-core` lulus) sebelum step `./gradlew assembleDebug`

#### Scenario: Kegagalan unit test menghentikan build APK
- **WHEN** ada unit test `:kit-core` yang gagal
- **THEN** workflow berhenti sebelum membangun APK dan ditandai gagal

### Requirement: Build melaporkan kegagalan kompilasi dengan jelas
Jika proses kompilasi (Kotlin atau C++ NDK) gagal, workflow SHALL ditandai sebagai gagal (exit code non-zero) dengan log yang membantu developer mengidentifikasi baris dan file yang menjadi penyebab kegagalan.

#### Scenario: Kegagalan compile C++ dilaporkan di log CI
- **WHEN** kode C++ NDK mengandung kesalahan sintaks atau error linker
- **THEN** log GitHub Actions menampilkan pesan error dengan nama file dan nomor baris yang menyebabkan kegagalan

### Requirement: Dependensi Oboe ditarik via Maven Prefab, bukan git submodule
CI SHALL menarik dependensi Oboe dari Maven Central menggunakan Prefab, bukan melalui git submodule, agar build deterministik dan tidak memerlukan kompilasi source Oboe dari nol di setiap run.

#### Scenario: Build pertama CI berhasil resolve Oboe dari Maven
- **WHEN** workflow berjalan untuk pertama kali pada repositori baru
- **THEN** Oboe berhasil diunduh dari Maven Central dan tidak ada step kompilasi source Oboe

### Requirement: Cache Gradle mempercepat build berikutnya
Workflow SHALL mengaktifkan cache Gradle (`cache: gradle` pada action `setup-java`) agar dependensi tidak diunduh ulang pada setiap run dan waktu build berkurang secara signifikan pada push berikutnya.

#### Scenario: Build kedua lebih cepat dari build pertama
- **WHEN** workflow berjalan untuk kedua kalinya dengan dependensi yang sama
- **THEN** cache Gradle digunakan dan build selesai lebih cepat dibanding run pertama
