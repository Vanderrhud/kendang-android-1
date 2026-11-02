# Spec Delta

## Purpose

Menyediakan pipeline build otomatis yang menghasilkan APK debug siap-install setiap kali kode baru di-push ke repositori, dengan unit test otomatis untuk logika inti yang tidak memerlukan perangkat fisik. Build CI tidak menghasilkan aset audio: tidak ada task build yang membuat file WAV.

## ADDED Requirements

### Requirement: Build APK debug otomatis setiap push ke main
Sistem CI SHALL secara otomatis memulai proses build APK debug setiap kali ada push ke branch `main`, kecuali push yang hanya mengubah dokumen (`openspec/**`, `**/*.md`); perubahan di `assets/kits/**` tetap memicu build. Build juga SHALL dapat dipicu manual lewat `workflow_dispatch`. Bila push baru masuk saat run sebelumnya masih berjalan, run lama SHALL dibatalkan.

#### Scenario: Push ke main memicu build
- **WHEN** kode baru di-push ke branch `main` di GitHub
- **THEN** GitHub Actions workflow otomatis berjalan dan mencoba membangun APK debug

#### Scenario: Build dapat dipicu manual
- **WHEN** pengguna memilih opsi "Run workflow" di halaman GitHub Actions
- **THEN** workflow berjalan dan membangun APK debug

#### Scenario: Push dokumen saja tidak memicu build
- **WHEN** push ke `main` hanya mengubah file di `openspec/**` atau file `**/*.md`
- **THEN** workflow build APK tidak dijalankan

#### Scenario: Perubahan kit bawaan tetap memicu build
- **WHEN** push ke `main` mengubah file di `assets/kits/**`
- **THEN** workflow build APK tetap dijalankan

#### Scenario: Push baru membatalkan run lama
- **WHEN** push baru masuk ke `main` saat run workflow sebelumnya masih berjalan
- **THEN** run lama dibatalkan dan run baru dijalankan

### Requirement: APK debug tersedia sebagai artifact yang bisa diunduh
Setiap build yang berhasil SHALL mengunggah file APK debug sebagai GitHub Actions artifact yang dapat diunduh langsung dari halaman Actions di browser atau HP. Nama artifact SHALL memuat nama aplikasi dan SHA commit pendek (misalnya `VRKendang-Debug-<sha7>`), dengan retensi 7 hari.

#### Scenario: APK tersedia setelah build berhasil
- **WHEN** workflow berjalan dan build berhasil
- **THEN** file APK debug tersedia di tab Artifacts pada halaman run workflow untuk diunduh

#### Scenario: Nama artifact dan retensi sesuai
- **WHEN** workflow berhasil membangun APK debug
- **THEN** artifact diberi nama yang memuat nama aplikasi dan SHA commit pendek serta disimpan dengan retensi 7 hari

### Requirement: Build tidak menghasilkan aset audio
Build SHALL tidak menjalankan task pembuat nada atau task Gradle apa pun yang membuat file WAV. File WAV pada kit bawaan hanya berasal dari file yang di-commit developer di `assets/kits/<kitId>/sounds/` dan dikemas apa adanya.

#### Scenario: Tidak ada task Gradle yang menghasilkan file .wav selama build
- **WHEN** workflow membangun APK debug
- **THEN** tidak ada task Gradle yang membuat file `.wav`, dan setiap file WAV di dalam APK hanya berasal dari file yang sudah di-commit di `assets/kits/`

#### Scenario: Kit bawaan New Metro berisi kit.json dan sampel suara rekaman asli di sounds/
- **WHEN** APK debug dibangun
- **THEN** `assets/kits/new-metro/` berisi `kit.json` dan seluruh file WAV yang dirujuk di folder `sounds/`

### Requirement: Unit test `:kit-core` berjalan di CI sebelum build APK
Workflow SHALL menjalankan seluruh unit test modul `:kit-core` sebelum proses `assembleDebug`. Jika ada test yang gagal, build dihentikan dan workflow ditandai gagal. Lokasi `assets/kits` SHALL diberikan ke tes lewat konfigurasi Gradle (misalnya `systemProperty`), bukan path relatif yang rapuh. Tes kit bawaan SHALL GAGAL bila tidak menemukan minimal satu kit.

#### Scenario: Unit test lulus sebelum APK dibangun
- **WHEN** workflow berjalan pada push ke main
- **THEN** step `./gradlew test` berhasil (seluruh unit test `:kit-core` lulus) sebelum step `./gradlew assembleDebug`

#### Scenario: Kegagalan unit test menghentikan build APK
- **WHEN** ada unit test `:kit-core` yang gagal
- **THEN** workflow berhenti sebelum membangun APK dan ditandai gagal

#### Scenario: Lokasi assets/kits diberikan lewat konfigurasi Gradle
- **WHEN** workflow menjalankan suite unit test di CI
- **THEN** tes menerima lokasi `assets/kits` dari `systemProperty` yang dikonfigurasi Gradle, bukan dari path relatif, lalu memvalidasi setiap file `kit.json` di lokasi tersebut

#### Scenario: Unit test memvalidasi seluruh kit bawaan sebelum APK dibangun
- **WHEN** workflow menjalankan suite unit test di CI
- **THEN** tes memvalidasi setiap file `kit.json` di lokasi `assets/kits` yang diberikan Gradle (struktur, rentang nilai, serta memastikan file WAV yang direferensikan ada dan valid bila sound tidak null), sehingga kit bawaan yang rusak tidak ikut terdistribusi

#### Scenario: Tes gagal bila tidak menemukan kit bawaan
- **WHEN** lokasi `assets/kits` yang diberikan Gradle tidak berisi satu pun kit yang valid
- **THEN** tes kit bawaan gagal dan workflow ditandai gagal, bukan lulus kosong

### Requirement: Varian release tetap dapat dikompilasi tanpa source set debug
Workflow SHALL mengompilasi varian release tanpa penandatanganan dan memeriksa isi APK release agar tidak memuat kelas atau paket Mode Developer dari source set debug.

#### Scenario: Kompilasi release berhasil tanpa source set debug
- **WHEN** workflow berjalan
- **THEN** langkah kompilasi release (misalnya `./gradlew compileReleaseKotlin` atau `assembleRelease` tanpa penandatanganan) berhasil tanpa menyertakan source set debug

#### Scenario: Ketergantungan tak sengaja pada kode debug menggagalkan CI
- **WHEN** kode release bergantung pada kelas atau resource yang hanya ada di source set debug sehingga kompilasi release gagal
- **THEN** workflow ditandai gagal dan klaim "kode Mode Developer tidak ada di APK release" tidak lolos diam-diam

#### Scenario: Pemeriksaan isi APK release berhasil
- **WHEN** `assembleRelease` tanpa penandatanganan selesai
- **THEN** workflow memeriksa dex APK release menggunakan `apkanalyzer dex packages` atau `unzip -p` + `grep`, dan memastikan tidak ada kelas atau paket Mode Developer dari source set debug (misalnya `com/vrkendang/kitauthoring`)

#### Scenario: Pemeriksaan gagal bila kode Mode Developer ditemukan di APK release
- **WHEN** pemeriksaan dex menemukan nama paket atau kelas alat penyusun kit (misalnya `com/vrkendang/kitauthoring`) di APK release
- **THEN** workflow ditandai gagal

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

### Requirement: Cache Gradle dipulihkan pada run berikutnya
Workflow SHALL mengaktifkan cache Gradle (`cache: gradle` pada action `setup-java`) agar dependensi tidak diunduh ulang pada setiap run.

#### Scenario: Log setup-java menunjukkan cache dipulihkan pada run kedua
- **WHEN** workflow berjalan untuk kedua kalinya dengan dependensi yang sama
- **THEN** log step `setup-java` menunjukkan cache Gradle dipulihkan (cache hit/restored) sehingga dependensi tidak diunduh ulang

### Requirement: Tanda tangan debug yang tetap
APK debug SHALL ditandatangani dengan keystore debug tetap yang disimpan di repo (misalnya `app/debug.keystore`) dan dikonfigurasi sebagai `signingConfigs.debug`, sehingga APK dari build berbeda dapat dipasang sebagai pembaruan di atas versi yang sudah terpasang tanpa uninstall.

#### Scenario: Dua APK debug memiliki sertifikat penandatangan yang sama
- **WHEN** dua APK debug dibangun dari dua run workflow berbeda
- **THEN** keduanya memiliki sertifikat penandatangan yang sama dan APK yang lebih baru dapat dipasang sebagai pembaruan tanpa uninstall

### Requirement: Versi alat build dipatok
Versi Oboe SHALL eksak (bukan rentang atau `latest`), dan `ndkVersion`, versi CMake, serta versi Android Gradle Plugin SHALL dipatok eksak di konfigurasi Gradle. Action GitHub yang dipakai (`actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`) SHALL dipatok ke versi mayor yang tertulis.

#### Scenario: Mengubah versi alat membutuhkan perubahan eksplisit di repo
- **WHEN** workflow berjalan tanpa perubahan konfigurasi versi di repo
- **THEN** versi Oboe, NDK, CMake, Android Gradle Plugin, dan action GitHub tetap seperti yang tertulis di repo, tidak ikut berubah otomatis dari runner

### Requirement: Lingkungan CI dan urutan langkah
Workflow SHALL menggunakan runner `ubuntu-latest`, JDK 17 Temurin via `actions/setup-java`, `chmod +x gradlew`, dan izin workflow minimal `permissions: contents: read`. Urutan langkah SHALL: checkout, setup Java, `chmod`, `./gradlew test`, validasi kit bawaan (bagian dari test), kompilasi dan periksa varian release, `./gradlew assembleDebug --stacktrace`, lalu unggah artifact.

#### Scenario: Workflow berjalan dengan lingkungan dan urutan yang ditetapkan
- **WHEN** workflow dijalankan
- **THEN** runner menggunakan `ubuntu-latest`, JDK 17 Temurin, izin `contents: read`, dan langkah-langkah berjalan dalam urutan checkout → setup Java → chmod → test → validasi kit bawaan → kompilasi/pemeriksaan release → assembleDebug → unggah artifact

### Requirement: ABI yang dibangun
Build SHALL menghasilkan ABI `arm64-v8a` dan `armeabi-v7a`. ABI `x86_64` SHALL tidak dibangun.

#### Scenario: APK hanya berisi ABI yang didukung
- **WHEN** APK debug dibangun
- **THEN** library native tersedia untuk `arm64-v8a` dan `armeabi-v7a`, dan tidak ada library native `x86_64`

### Requirement: Laporan tes saat gagal
Bila langkah test gagal, workflow SHALL mengunggah laporan tes (`**/build/reports/tests/**`) sebagai artifact dengan kondisi `if: failure()` agar penyebab kegagalan dapat dibaca tanpa menjalankan ulang workflow.

#### Scenario: Laporan tes tersedia saat test gagal
- **WHEN** langkah `./gradlew test` gagal
- **THEN** workflow mengunggah laporan tes dari `**/build/reports/tests/**` sebagai artifact meskipun workflow berstatus gagal
