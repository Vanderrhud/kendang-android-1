# Proposal

## Why

Belum ada aplikasi kendang dangdut Android yang membagi satu kulit kendang menjadi beberapa zona akustik realistis (seperti karakter pukulan pada instrumen fisik), dengan latensi audio serendah mungkin dan arsitektur kit yang sepenuhnya berbasis data sehingga posisi, ukuran, suara, dan volume dapat diubah tanpa build ulang. **VR Kendang** adalah aplikasi instrumen musik tahap awal yang difokuskan untuk pengujian dan penyempurnaan internal secara langsung di perangkat fisik oleh developer/pemilik produk (bukan rilis publik langsung). Fondasi aplikasi dibangun dari nol dengan arsitektur kit data-driven dan alat penyusun kit (Mode Developer) yang menjadi instrumen paling krusial: developer dapat langsung memasang file sampel rekaman kendang asli (WAV) dari penyimpanan HP ke tiap zona, menguji responsivitas instrumen, dan menyempurnakan tata letak tanpa terhambat siklus build ulang.

## What Changes

**Aplikasi dan platform**
- Membuat proyek Android baru dari nol (Kotlin untuk Android) bernama **VR Kendang** dengan Gradle multi-modul: `:app` (UI, JNI, kode C++ via NDK/CMake) dan `:kit-core` (Kotlin/JVM murni tanpa `android.*`: model kit, parser, validator, hit-testing, pembaca paket kit, penggabungan tata letak).
- Aplikasi difokuskan sebagai fondasi tahap awal untuk pengujian internal mandiri di HP fisik; tidak ada target rilis Play Store di MVP ini.

**Audio**
- Mengimplementasikan mesin audio berbasis **Oboe (C++ NDK)** dengan Voice Pool 32 suara simultan, Voice Stealing, master gain/limiter untuk mencegah clipping, dan Fast Audio Path otomatis sesuai hardware.
- Stream Oboe dibuka pada sample rate native perangkat; sample WAV di-resample satu kali di sisi C++ saat stream dibuka (dan dibuat ulang bila stream dibuka ulang).
- Jalur trigger dari thread UI ke audio callback memakai antrean lock-free.
- Menetapkan target latensi: keluaran audio ≤ 20 ms dan sentuh-ke-bunyi total ≤ 40 ms pada perangkat dengan jalur low-latency (target pengukuran manual, bukan syarat lulus CI).
- Penanganan zona tanpa suara: zona yang belum memiliki file suara (unassigned) diperlakukan secara aman (*graceful silence*, sentuhan tidak memicu playback dan zero crash).
- Siklus hidup audio: pause/resume stream saat aplikasi ke background, reconnect otomatis saat headphone dicolok/dicabut.

**Tampilan dan interaksi**
- `KendangBoardView`: satu Custom View yang merender seluruh kendang dan menangani semua multi-touch secara terpusat.
- Hit-testing berbasis matematika murni (aljabar), dapat diuji unit test di CI.
- Daftar kit di aplikasi: pemain memilih dari kit yang tersedia (MVP: satu kit awal New Metro; arsitektur sudah mendukung banyak kit).
- `FLAG_KEEP_SCREEN_ON`, Immersive Sticky Mode, orientasi landscape terkunci.

**Data kit**
- Format `kit.json` modular dan data-driven (skema pada design.md D11): kanvas relatif, posisi dan ukuran tiap kendang, parameter busur pembatas Bass, nama file suara (boleh null bila belum dipasang), dan `gain` (volume) per zona.
- Kit awal bawaan (`new-metro`) membawa konfigurasi tata letak dan zona di `assets/kits/new-metro/kit.json` tanpa menyertakan aset WAV bawaan di dalam APK.
- Format paket kit satu file berekstensi **`.vrkendang`** (zip berisi `kit.json` dan `sounds/*.wav`) untuk ekspor/impor oleh developer.

**Mode pemain: edit tata letak (Mode Kalibrasi)**
- Pemain dapat menggeser posisi dan mengubah ukuran kendang langsung di layar; suara tidak dapat diubah.
- Tombol simpan menyimpan tata letak pemain sebagai *layout override* kecil per kit di penyimpanan internal aplikasi. Kit yang dimuat adalah kit bawaan yang digabung dengan override tersebut. Tersedia tombol "Reset ke bawaan".

**Mode developer (krusial untuk pengujian internal, hanya di build debug)**
- Mode developer memberi kendali penuh dan menjadi instrumen utama developer untuk menguji aplikasi: membuat kit baru, mengubah posisi, ukuran, parameter busur Bass, volume per zona, serta memasang dan mengganti file suara per zona langsung dari penyimpanan HP.
- Alur kerja suara dari HP: developer memilih file audio berformat **WAV PCM** (16-bit) via Android System File Picker (`ACTION_OPEN_DOCUMENT`), disalin ke direktori kerja aplikasi, langsung di-load ke C++ engine, dan bisa langsung dimainkan serta diuji seketika.
- Ekspor/impor: kit yang telah disempurnakan dapat disimpan/diekspor menjadi paket file tunggal `.vrkendang` ke penyimpanan HP.
- Kit bawaan dapat dibuka di mode developer sebagai salinan kerja; menyimpannya menghasilkan file `.vrkendang` (kit di dalam APK tidak pernah ditimpa).
- Kode mode developer berada di source set `debug`; APK release tidak membawa kode ini. Tidak ada cadangan otomatis (`.bak`) saat menimpa file kit.
- Impor kit melakukan validasi ketat (struktur `kit.json`, keberadaan dan format WAV PCM, rentang nilai) serta perlindungan path traversal saat mengekstrak zip.

**Aset dan CI**
- Tidak ada generator nada dummy sintetis di Gradle dan tidak ada file WAV bawaan di APK; build Gradle bersih, cepat, dan deterministik.
- GitHub Actions membangun APK debug otomatis pada setiap push ke `main`, memakai Oboe via Prefab (bukan git submodule); unit test `:kit-core` berjalan di workflow.

## Capabilities

### New Capabilities

- `kendang-board`: Tampilan dan interaksi papan kendang: rendering seluruh kulit kendang sebagai kesatuan visual, multi-touch terpusat, hit-testing zona akustik per kendang, animasi umpan balik sentuhan.
- `audio-engine`: Mesin audio Oboe C++ NDK: pemuatan WAV ke memori (kepemilikan data di C++), resampling ke rate stream, Voice Pool 32 slot, Voice Stealing, master gain/limiter, antrean trigger lock-free, penanganan zona hening aman, Fast Audio Path, siklus hidup audio.
- `kit-data`: Sistem data kit: skema `kit.json` (mendukung zona tanpa suara), koordinat kanvas relatif, model kit, validasi, pembacaan kit bawaan dari `assets/kits/`, daftar kit, penggabungan layout override pemain, format paket `.vrkendang`.
- `calibration-mode`: Mode edit tata letak untuk pemain: geser posisi dan ubah ukuran kendang, simpan sebagai layout override, reset ke bawaan.
- `kit-authoring`: Alat penyusun kit khusus developer (sangat krusial untuk fase pengujian awal, hanya build debug): buat kit baru, ubah posisi/ukuran/parameter Bass/volume, pasang file suara WAV PCM langsung dari penyimpanan HP, impor dan ekspor `.vrkendang` lewat pemilih file sistem, simpan dengan menimpa file kit, buka kit bawaan sebagai salinan kerja.
- `ci-build`: Infrastruktur build dan CI: GitHub Actions untuk APK debug otomatis, Oboe via Prefab Maven, unit test `:kit-core` (hit-testing, parser, validator, pembaca paket termasuk uji path traversal, penggabungan override) di workflow.

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
- **Aset suara:** APK tidak menyertakan file WAV bawaan. Semua suara dimasukkan langsung dari penyimpanan HP oleh developer dalam format WAV PCM via pemilih file sistem.
- **Cakupan dikembangkan bertahap:** inti yang bisa dimainkan (audio, board, kit bawaan, CI) dibangun lebih dulu; alat penyusun kit (`kit-authoring`) menyusul setelah inti stabil.
