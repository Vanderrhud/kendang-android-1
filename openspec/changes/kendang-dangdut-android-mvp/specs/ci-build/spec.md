# Spec Delta

## Purpose

Menyediakan pipeline build otomatis yang menghasilkan APK debug siap-install setiap kali kode baru di-push ke repositori, dengan unit test otomatis untuk logika inti yang tidak memerlukan perangkat fisik.

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

### Requirement: Build melaporkan kegagalan dengan jelas
Jika proses kompilasi (Kotlin atau C++ NDK) gagal, workflow SHALL ditandai sebagai gagal (exit code non-zero) dengan log yang memuat pesan error yang dapat diidentifikasi penyebabnya.

#### Scenario: Kesalahan kompilasi terlihat di log CI
- **WHEN** ada kesalahan kompilasi Kotlin atau C++
- **THEN** workflow ditandai gagal dan log menampilkan pesan error spesifik

### Requirement: Unit test logika inti berjalan di CI
Workflow SHALL menjalankan JVM unit test untuk logika hit-testing zona akustik dan logika parsing `kit.json` sebagai bagian dari proses build.

#### Scenario: Unit test berjalan saat build
- **WHEN** workflow CI berjalan
- **THEN** unit test untuk hit-testing dan pembacaan JSON dieksekusi sebelum APK diproduksi

#### Scenario: Kegagalan unit test menghentikan build
- **WHEN** satu atau lebih unit test gagal
- **THEN** proses build berhenti dan workflow ditandai gagal

### Requirement: Build menyertakan suara dummy placeholder
Workflow build SHALL memastikan 10 file WAV dummy (nada sintetik berfrekuensi berbeda) tersedia di dalam APK sehingga aplikasi dapat langsung dibunyikan dan diuji saat pertama kali diinstal, sebelum aset rekaman kendang asli tersedia.

#### Scenario: APK hasil build langsung bisa berbunyi
- **WHEN** APK hasil build diinstal di perangkat Android dan pemain mengetuk zona kendang
- **THEN** suara terdengar (nada dummy) tanpa memerlukan aset tambahan dari luar APK
