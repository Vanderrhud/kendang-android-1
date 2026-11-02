# Proposal

## Why

Belum ada aplikasi kendang dangdut Android yang membagi satu kulit kendang menjadi beberapa zona akustik realistis (seperti karakter pukulan pada instrumen fisik), dengan latensi audio serendah mungkin dan arsitektur kit yang sepenuhnya berbasis data sehingga posisi, ukuran, suara, dan volume dapat diubah tanpa build ulang. **VR Kendang** adalah aplikasi instrumen musik tahap awal yang difokuskan untuk pengujian dan penyempurnaan internal secara langsung di perangkat fisik oleh developer/pemilik produk (bukan rilis publik langsung). Fondasi aplikasi dibangun dari nol dengan arsitektur kit data-driven dan alat penyusun kit (Mode Developer) yang menjadi instrumen paling krusial: kit bawaan MVP disertakan tanpa suara (semua zona `sound: null`); rekaman kendang asli dari pemilik produk dipasang kemudian langsung dari HP lewat Mode Developer untuk menguji responsivitas instrumen, latensi, dan menyempurnakan tata letak tanpa terhambat siklus build ulang.

## What Changes

**Aplikasi dan platform**
- Membuat proyek Android baru dari nol (Kotlin untuk Android) bernama **VR Kendang** dengan Gradle multi-modul: `:app` (UI, JNI, kode C++ via NDK/CMake) dan `:kit-core` (Kotlin/JVM murni tanpa `android.*`: model kit, parser, validator, hit-testing, pembaca paket kit, penggabungan tata letak).
- Aplikasi difokuskan sebagai fondasi tahap awal untuk pengujian internal mandiri di HP fisik; tidak ada target rilis Play Store di MVP ini.

**Audio**
- Mengimplementasikan mesin audio berbasis **Oboe (C++ NDK)** dengan Voice Pool hingga 32 suara simultan tanpa saling memotong, Voice Stealing saat > 32 suara (micro-fade-out 1 ms), perkalian gain zona (0.0–2.0), dan master limiter untuk mencegah clipping keras.
- Audio callback aman real-time: tidak melakukan alokasi memori dinamis, tidak menggunakan mutex, dan tidak memanggil JNI. Trigger dari thread UI masuk lewat antrean lock-free single-producer/single-consumer (SPSC).
- Kepemilikan data PCM disalin ke memori C++ sendiri saat load, sehingga Kotlin bebas melepas buffer setelah pemanggilan selesai. Kotlin mengurai header WAV menggunakan `WavParser` di `:kit-core` (WAV PCM 16-bit mono); C++ tidak mengurai format kontainer WAV.
- Stream Oboe dibuka tanpa menetapkan sample rate tetap; sample rate native yang diberikan stream dibaca dan dijadikan target resampling PCM dari PCM asli. Bila stream dibuka ulang dan rate berubah, sample di-resample ulang dari PCM asli dan pitch tetap benar.
- Menetapkan target latensi: keluaran audio ≤ 20 ms dan sentuh-ke-bunyi total ≤ 40 ms pada perangkat dengan jalur low-latency (target pengukuran manual, bukan syarat lulus CI). Pada Android 8.0 (API 26), stream secara otomatis memakai OpenSL ES dan tetap meminta mode LowLatency.
- Penanganan zona tanpa suara: zona yang belum memiliki file suara (unassigned/null) diperlakukan secara aman (*graceful silence*, sentuhan tidak memicu playback dan zero crash). Bila satu file WAV gagal dimuat, zona diperlakukan sebagai tanpa suara, galat dicatat, dan aplikasi tidak crash.
- Siklus hidup audio: saat stream dijeda (pause) ketika ke background, semua voice aktif dihentikan agar tidak ada bunyi menggantung saat resume; reconnect otomatis saat headphone dicolok/dicabut.

**Tampilan dan interaksi**
- `KendangBoardView`: satu Custom View yang merender seluruh kendang pada layar penuh landscape dan menangani semua multi-touch secara terpusat.
- Hit-testing berbasis matematika murni (aljabar), dapat diuji unit test di CI. Sentuhan zona (dengan atau tanpa suara) menampilkan visual tap highlight.
- Navigasi berbasis halaman penuh (tanpa dialog popup): Header di sisi kiri menyediakan satu tombol Parent (`☰ Menu`) yang membuka opsi Child **`Pilih Kit`** (membuka Halaman Pilih Kit full layar dengan nama kit dan tombol **`Pakai`** / status **`Aktif`**) dan **`Sesuaikan`** (membuka halaman/mode penyesuaian posisi & ukuran kendang); di sisi kanan Header tersedia tombol **`Mode Developer`** (khusus build debug).
- `FLAG_KEEP_SCREEN_ON`, Immersive Sticky Mode, orientasi landscape terkunci.

**Data kit**
- Format `kit.json` modular dan data-driven (skema pada bagian "Skema `kit.json` dan paket `.vrkendang`" di design.md): kanvas relatif, posisi dan ukuran tiap kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`), parameter busur pembatas Bass, nama file suara (boleh null bila belum dipasang, seperti kit baru yang awalnya kosong), dan `gain` (volume) per zona (rentang 0.0–2.0).
- Validasi ketat di `:kit-core`: bila `sound` tidak null, file harus ada, bukan path absolut, tidak mengandung `..`, dan berformat WAV PCM 16-bit mono valid.
- Kit bawaan (`new-metro`) membawa konfigurasi tata letak dan rekaman suara kendang asli di `assets/kits/new-metro/`.
- Format paket kit satu file berekstensi **`.vrkendang`** (zip berisi `kit.json` dan `sounds/*.wav`) untuk ekspor/impor oleh developer.

**Mode pemain: sesuaikan tata letak (Mode Kalibrasi)**
- Pemain dapat menggeser posisi kendang dengan 1 jari dan mencubit dengan 2 jari (*pinch-to-scale*) langsung di kanvas penuh untuk memperbesar/memperkecil ukuran kendang (tanpa bar atau slider bawah yang menghalangi layar); suara tidak dapat diubah.
- Header mode Sesuaikan hanya menyediakan tombol **`✕ Batal`**, **`↺ Reset`**, dan **`✓ Simpan`**. Tombol Simpan menyimpan tata letak pemain sebagai *layout override* kecil per kit di penyimpanan internal aplikasi.

**Mode developer (krusial untuk pengujian internal, hanya di build debug)**
- Mode developer dibuka sebagai halaman penuh mandiri dan menggunakan pola pemilihan per kendang: developer mengetuk salah satu kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`) di peta kiri, lalu panel kanan menampilkan konfigurasi khusus kendang tersebut (daftar zona suara WAV, tes bunyi, volume/gain per zona, serta parameter busur pembatas khusus untuk `Bass`).
- Alur kerja suara dari HP: developer memilih file audio berformat **WAV PCM 16-bit mono** via Android System File Picker (`ACTION_OPEN_DOCUMENT`), disalin ke direktori kerja aplikasi, diurai oleh `WavParser`, langsung di-load ke C++ engine, dan bisa langsung dimainkan serta diuji seketika.
- Pemisahan tegas antara **Simpan Internal** vs **Ekspor/Impor (`.vrkendang`)**:
  - **Simpan (Internal)**: Menyimpan hasil edit langsung ke penyimpanan internal aplikasi (`files/kits/<kitId>/`) sehingga langsung muncul dan dapat diaktifkan di halaman **Pilih Kit** tanpa membuka File Manager HP. Tersedia 2 opsi simpan internal: **`✓ Simpan`** (menimpa kit yang sedang diedit saat ini) dan **`+ Kit Baru`** (menyimpan hasil edit menjadi kit baru dengan nama dan ID baru).
  - **Impor & Ekspor (`.vrkendang`)**: Tombol terpisah khusus untuk mengimpor paket `.vrkendang` dari penyimpanan HP atau mengekspor kit ke file `.vrkendang` di penyimpanan HP.
- Kode mode developer berada di source set `debug`; APK release tidak membawa kode ini. Tidak ada cadangan otomatis (`.bak`) saat menimpa file kit.
- Impor kit melakukan validasi ketat (struktur `kit.json`, keberadaan dan format WAV PCM 16-bit mono, rentang nilai) serta perlindungan path traversal saat mengekstrak zip.

**Aset dan CI**
- Kit bawaan MVP New Metro hanya berisi `kit.json` dengan semua zona `"sound": null`; tidak ada task build yang menghasilkan file WAV, dan file WAV kit bawaan hanya berasal dari file yang di-commit developer di `assets/kits/<kitId>/sounds/`.
- GitHub Actions membangun APK debug otomatis pada setiap push ke `main` dan dapat dipicu manual; memakai Oboe via Prefab (bukan git submodule) dengan versi alat yang dipatok, APK debug ditandatangani dengan keystore debug tetap, varian release dikompilasi dan diperiksa agar tidak memuat kode Mode Developer, ABI dibatasi ke `arm64-v8a` dan `armeabi-v7a`, serta unit test `:kit-core` (termasuk parser JSON, validasi skema, hit-testing, `WavParser`, dan validasi seluruh `kit.json` bawaan) berjalan di workflow.

## Capabilities

### New Capabilities

- `kendang-board`: Tampilan dan interaksi papan kendang: rendering seluruh kulit kendang sebagai kesatuan visual, multi-touch terpusat, hit-testing zona akustik per kendang, animasi umpan balik sentuhan (termasuk pada zona tanpa suara).
- `audio-engine`: Mesin audio Oboe C++ NDK: pemuatan data PCM ke memori (C++ memiliki memori sendiri), resampling ke rate stream native, Voice Pool 32 slot, Voice Stealing, gain per zona (0.0–2.0) + master limiter, antrean trigger SPSC lock-free (callback bebas alokasi, mutex, dan JNI), penanganan zona hening aman, Fast Audio Path (OpenSL ES otomatis di API 26), siklus hidup audio (voice dimatikan saat pause, reconnect audio).
- `kit-data`: Sistem data kit: skema `kit.json` (mendukung zona tanpa suara `sound: null`), `WavParser` (PCM 16-bit mono) di `:kit-core`, koordinat kanvas relatif, model kit, validasi, pembacaan kit bawaan dari `assets/kits/`, daftar kit, penggabungan layout override pemain, format paket `.vrkendang`.
- `calibration-mode`: Mode edit tata letak untuk pemain: geser posisi dan ubah ukuran kendang, simpan sebagai layout override, reset ke bawaan.
- `kit-authoring`: Alat penyusun kit khusus developer (sangat krusial untuk fase pengujian awal, hanya build debug): buat kit baru (awal kosong), mode papan UJI/EDIT, ubah posisi/ukuran/parameter Bass/volume, pasang file suara WAV PCM langsung dari penyimpanan HP, impor dan ekspor `.vrkendang` lewat pemilih file sistem, simpan ke file sumber atau Simpan Sebagai ke file baru, buka kit bawaan sebagai salinan kerja.
- `ci-build`: Infrastruktur build dan CI: GitHub Actions untuk APK debug otomatis dan build manual, keystore debug tetap, versi alat dipatok, ABI `arm64-v8a`/`armeabi-v7a`, pemeriksaan release tanpa kode Mode Developer, laporan tes saat gagal, Oboe via Prefab Maven, unit test `:kit-core` (hit-testing, parser, validator, pembaca paket termasuk uji path traversal, WavParser, penggabungan override) di workflow.

### Modified Capabilities

*(Tidak ada. Ini proyek baru dari nol, tidak ada spec existing yang dimodifikasi.)*

## Impact

- **Proyek baru dari nol:** struktur Android (Gradle multi-modul, Kotlin, C++ CMake), konfigurasi GitHub Actions, dan aset placeholder dibuat dari awal.
- **Nama aplikasi:** VR Kendang.
- **Fase rilis:** tahap awal pengujian internal; tidak ada publikasi ke Play Store atau tanda tangan rilis publik pada tahap ini.
- **Dependensi baru:** Oboe (`com.google.oboe:oboe` via Maven/Prefab), Android NDK, CMake.
- **Format data baru:** skema `kit.json` dan paket `.vrkendang` dirancang khusus untuk VR Kendang (bukan format standar industri).
- **Minimum API:** Android 8.0 (API 26). AAudio tersedia mulai Android 8.1 (API 27); pada API 26 Oboe otomatis memakai OpenSL ES.
- **Orientasi:** landscape saja, dikunci di level Activity.
- **Penyimpanan:** kit bawaan di APK `assets/`; layout override pemain di penyimpanan internal aplikasi; file suara WAV dan paket `.vrkendang` developer diakses lewat pemilih file sistem (Storage Access Framework), tanpa izin storage khusus.
- **Aset suara:** Kit bawaan MVP (`new-metro`) disertakan tanpa file suara (semua zona `sound: null`). Suara rekaman kendang asli dipasang kemudian oleh developer/pemilik produk langsung dari penyimpanan HP via Mode Developer dalam format WAV PCM 16-bit mono.
- **Cakupan dikembangkan bertahap:** inti papan kendang dan engine audio dibangun lebih dulu, diikuti fitur minimal pasang suara di Mode Developer agar audio engine dan latensi dapat diuji langsung di perangkat fisik.
