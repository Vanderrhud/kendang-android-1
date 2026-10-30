# Design

## Context

VR Kendang adalah aplikasi Android baru dari nol yang difokuskan sebagai instrumen musik tahap awal untuk pengujian dan penyempurnaan internal langsung di perangkat fisik oleh developer/pemilik produk (bukan untuk rilis publik langsung pada tahap ini). Kendala dan karakteristik utama yang membentuk keputusan desain:

1. **Pengembangan "blind" di VPS**: kode ditulis di VPS tanpa Android SDK/NDK lokal; kompilasi dan pengujian otomatis hanya terjadi di GitHub Actions. Kesalahan kompilasi baru terlihat setelah push dan workflow selesai.
2. **Zero-rebuild untuk konten kit**: perubahan posisi, ukuran, busur Bass, suara (WAV), atau volume tidak boleh memerlukan build ulang APK (3-5 menit). Konten kit sepenuhnya berupa data, dan suara dimasukkan langsung dari HP oleh developer.
3. **Latensi audio adalah fitur inti**: ini instrumen musik interaktif. Target: keluaran audio ≤ 20 ms dan sentuh-ke-bunyi total ≤ 40 ms pada perangkat dengan jalur low-latency. Ini target pengukuran manual di perangkat fisik, bukan syarat lulus CI.
4. **Layar landscape sempit**: HP modern rasio 20:9 di landscape memiliki tinggi terbatas; ruang vertikal untuk UI non-bermain harus diminimalkan.
5. **Mode Developer sangat krusial**: karena aplikasi belum dirilis ke publik, Mode Developer adalah instrumen kerja paling vital bagi pengembang/pemilik produk untuk merakit kit, memasang rekaman WAV kendang asli dari HP, menguji responsivitas pukulan, dan menyimpan kit yang sudah matang sebagai paket `.vrkendang`.

## Goals / Non-Goals

**Goals:**
- Arsitektur tiga lapisan dengan ketergantungan searah: Data Kit (JSON) → Tampilan & Touch (Kotlin) → Audio (C++/Oboe).
- Semua logika hitung dan data (hit-testing, parsing, validasi, paket kit, penggabungan override) berada di modul `:kit-core` (Kotlin/JVM murni) agar bisa diuji JVM unit test di CI, dan larangan `android.*` ditegakkan compiler.
- Build CI yang andal memakai Oboe via Prefab Maven, tanpa git submodule, dan tanpa ketergantungan pada generator audio sintetis.
- Alat penyusun kit di Mode Developer agar suara WAV dapat dipasangkan langsung dari penyimpanan HP ke tiap zona dan dapat langsung dibunyikan seketika tanpa coding dan tanpa build ulang.

**Non-Goals:**
- Tanda tangan rilis dan publikasi ke Play Store (VR Kendang adalah instrumen pengujian internal pada fase ini).
- Penyertaan aset audio bawaan atau generator nada dummy di dalam APK (APK dibangun bersih tanpa audio bawaan; pengujian memakai sampel suara mandiri dari HP).
- Dukungan iOS, Flutter, atau lintas platform.
- Sinkronisasi kit lewat jaringan (OTA), dan berbagi kit online antar pemain.
- Integrasi ad SDK (ruang header disiapkan, tidak diintegrasikan).
- Cadangan otomatis (`.bak`) saat developer menimpa file kit (keputusan pemilik produk).

## Decisions

### D1: Satu Custom View tunggal (`KendangBoardView`) untuk rendering dan touch

**Keputusan:** Seluruh area bermain dirender dan ditangani touch-nya oleh satu `KendangBoardView`, bukan beberapa View per kendang.

**Alasan:** Android mendistribusikan touch berdasarkan *bounding box* persegi panjang tiap View. Bounding box kendang oval/lingkaran yang berdekatan bisa bertubrukan, sehingga View terpisah per kendang membutuhkan koordinasi `dispatchTouchEvent` yang rumit dan rawan *dropped pointer* pada multi-touch intensif. Satu View menerima seluruh `MotionEvent` (semua pointer) dan melakukan hit-testing secara matematis, bukan lewat bounding box.

**Alternatif ditolak:** 4 Custom View terpisah (konflik bounding box, koordinasi multi-touch rumit).

---

### D2: Oboe via Maven Prefab (bukan git submodule)

**Keputusan:** Oboe ditarik lewat Prefab dari Maven Central (`com.google.oboe:oboe`) dan dikonfigurasi di CMakeLists.txt dengan `find_package(oboe REQUIRED CONFIG)`.

**Alasan:** Git submodule memaksa CI meng-compile seluruh source Oboe dari nol setiap build (+5-10 menit) dan rentan masalah versi NDK. Prefab mengirim Oboe sebagai biner yang sudah dikompilasi untuk semua ABI, sehingga build CI lebih cepat (~2-3 menit) dan deterministik.

**Catatan API:** Oboe memakai AAudio mulai Android 8.1 (API 27) dan OpenSL ES di bawahnya. Dengan minSdk 26, perangkat Android 8.0 memakai OpenSL ES secara otomatis. Mode `PerformanceMode::LowLatency` selalu diminta.

---

### D3: Voice Pool pre-alokasi, Voice Stealing, master limiter, dan penanganan zona tanpa suara

**Keputusan:** Mesin audio C++ memakai pool 32 slot suara yang dialokasikan saat inisialisasi. Audio callback tidak pernah memanggil `malloc`/`new`. Saat semua slot terisi, slot tertua diambil alih (voice stealing) dengan micro-fade-out 1 ms untuk mencegah bunyi pop. Hasil penjumlahan semua voice melewati master gain dan limiter sederhana (soft clip) sebelum ditulis ke buffer keluaran.

**Penanganan zona tanpa suara:** Bila suatu zona kendang belum dipasangi file suara (unassigned / null), event sentuhan pada zona tersebut diabaikan secara aman (*graceful silence*) tanpa memicu alokasi atau playback, sehingga aplikasi dijamin zero-crash saat disentuh sebelum suara dipasangkan.

**Alasan:** Menjamin eksekusi callback stabil dan deterministik di thread real-time prioritas tinggi serta aman saat instrumen baru pertama kali dibuka sebelum kit dikonfigurasi.

---

### D4: Kotlin membaca WAV → DirectByteBuffer → C++ menyalin dan memiliki data

**Keputusan:** Kotlin membaca WAV (dari direktori kerja cache developer atau paket kit) menjadi `DirectByteBuffer` dan menyerahkan pointer-nya ke C++ lewat JNI. **C++ menyalin PCM ke memori miliknya sendiri selama pemanggilan load berlangsung**, sehingga setelah pemanggilan kembali Kotlin bebas melepas buffer. C++ tidak pernah menyimpan pointer ke buffer milik Kotlin.

**Resampling:** stream Oboe dibuka dengan sample rate native perangkat. C++ menyimpan PCM asli, lalu membuat salinan yang di-resample ke rate stream satu kali saat stream dibuka memakai interpolasi berkualitas baik (offline process). Bila stream dibuka ulang (misalnya saat headphone dicolok/dicabut) dan rate berubah, salinan dibuat ulang dari PCM asli.

**Alasan:** Pemisahan kepemilikan yang bersih: Kotlin berurusan dengan file I/O dan Storage Access Framework, sedangkan C++ murni mengelola memori audio dan playback real-time tanpa risiko dangling pointer.

---

### D5: Kanvas relatif 0.0-1.0 dengan `canvasAspect` dan aspect-fit

**Keputusan:** `kit.json` mendeklarasikan `canvasAspect` (lebar : tinggi kanvas, misalnya 2.0).
- Origin di kiri atas kanvas. Posisi `x` dan `y` berada di rentang 0.0-1.0 terhadap lebar dan tinggi kanvas.
- Radius (`rx`, `ry`) adalah pecahan dari **tinggi** kanvas, sehingga lingkaran tetap lingkaran.
- Untuk perhitungan geometri dipakai *satuan kanvas* di mana tinggi kanvas = 1.0 dan lebar = `canvasAspect`: `X_u = x * canvasAspect`, `Y_u = y`.
- Saat render, kanvas di-fit ke area `KendangBoardView` berdasarkan tinggi, dipusatkan, dan sisa ruang horizontal menjadi margin.

**Alasan:** Menjaga rasio visual kendang tetap konsisten di berbagai ukuran dan rasio layar HP tanpa distorsi.

---

### D6: Tata letak utama, ConstraintLayout dua zona

**Keputusan:** Layout Activity memakai `ConstraintLayout`: `HeaderView` di atas (tinggi tetap ~48dp) dan `KendangBoardView` di bawahnya mengisi sisa tinggi.

```
Activity (ConstraintLayout, landscape-locked, Immersive Sticky)
├── HeaderView (height: 48dp, top: 0)
│   ├── KitSelectorButton (start)  -> membuka daftar kit
│   ├── AdSlotContainer (center, GONE di MVP)
│   └── SettingsButton (end)       -> Edit Tata Letak; di build debug: Mode Developer (Alat Penyusun Kit)
└── KendangBoardView (top: bottom_of_header, bottom: 0, match_parent width)
```

---

### D7: Geometri hit-testing, aljabar murni di `:kit-core`

**Keputusan:** Seluruh hit-testing dalam kelas Kotlin murni (`HitTester`, `DrumShape`, `DrumArea`) di modul `:kit-core`, tanpa dependensi `android.*`. Semua perhitungan dalam satuan kanvas (D5).

Rumus:
- **Kulit oval/lingkaran:** `((X-cx)/rx)² + ((Y-cy)/ry)² ≤ 1.0`.
- **Model `bass`:**
  - Area utama (Bass-1): di dalam oval Bass DAN di dalam lingkaran pembatas `C_div`.
  - Area bulan sabit (Bass-2 hingga Bass-5): di dalam oval Bass DAN di luar `C_div`; posisi `Y` membagi rentang vertikal oval Bass menjadi 4 bilah sama tinggi (`band` 0-3, dari atas ke bawah).
  - `C_div` didefinisikan **relatif terhadap radius Bass**: pusat `(cx + dx·rx, cy + dy·ry)` dan radius `r·rx`. Dengan begitu busur ikut berskala saat Bass diperbesar atau diperkecil.
- **Model `split` (Sedang, Kecil 2):** dalam oval, bandingkan `Y` dengan `cy`: `upper` bila `Y < cy`, `lower` bila sebaliknya.
- **Model `single` (Kecil 1):** dalam oval, langsung zona tunggal.

**Urutan evaluasi:** mengikuti urutan elemen array `drums` di `kit.json` (indeks kecil dicek lebih dulu). Untuk New Metro: Bass → Kecil 1 → Sedang → Kecil 2. Bila tidak ada yang cocok, sentuhan diabaikan.

---

### D8: Penyimpanan, empat lokasi dengan peran berbeda

**Keputusan:**

| Data | Lokasi | Akses |
|---|---|---|
| Kit bawaan | `assets/kits/<kitId>/kit.json` di APK | hanya baca (konfigurasi tata letak awal, tanpa file WAV) |
| Layout override pemain | penyimpanan internal aplikasi, `files/layouts/<kitId>.json` | baca/tulis oleh aplikasi |
| Salinan kerja developer | `cacheDir/dev-kit/` (direktori kerja kit yang sedang disunting) | hanya build debug |
| File suara WAV & `.vrkendang` developer | dipilih lewat Storage Access Framework (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`) | hanya build debug |

**Alasan:** Bebas izin runtime penyimpanan kompleks (`READ/WRITE_EXTERNAL_STORAGE` atau `MANAGE_EXTERNAL_STORAGE`), mematuhi arsitektur modern Android, dan memberikan akses langsung ke file audio yang dimiliki pengguna di memori HP.

---

### D9: Pengelolaan suara mandiri dari penyimpanan HP (tanpa audio bawaan di APK)

**Keputusan:** APK dibangun **tanpa membawa file audio WAV bawaan dan tanpa generator nada sintetis di Gradle**.
1. Semua sampel suara dimasukkan oleh developer langsung dari penyimpanan HP melalui Android System File Picker (`ACTION_OPEN_DOCUMENT`).
2. Format yang didukung: **WAV PCM (16-bit, mono/stereo)**.
3. Saat file WAV dipilih untuk suatu zona, aplikasi menyalin stream audio ke direktori kerja developer (`cacheDir/dev-kit/sounds/<zoneId>.wav`), lalu memicu audio engine C++ untuk memuatnya ke memori. Zona tersebut langsung aktif dan bisa dimainkan seketika.
4. Zona yang belum dipasangi file suara tetap aman dimainkan (silent playback, tanpa crash).

**Alasan:** Menghilangkan kompleksitas generator nada sintetis di CI, mempercepat build, dan memungkinkan pengujian menggunakan sampel kendang asli di tangan developer secara instan.

---

### D10: GitHub Actions, Ubuntu latest, JDK 17 Temurin, cache Gradle

**Keputusan:** `ubuntu-latest`, `actions/setup-java@v4` (distribusi `temurin`, JDK 17), `cache: gradle`.

**Langkah workflow:**
1. Checkout repository
2. Setup Java 17 (Temurin)
3. `chmod +x gradlew`
4. `./gradlew test` (unit test `:kit-core`: hit-testing, parser, validator, pembaca paket termasuk uji path traversal, penggabungan override)
5. `./gradlew assembleDebug --stacktrace`
6. Upload `app-debug.apk` sebagai artifact (retensi 7 hari)

---

### D11: Skema `kit.json` dan paket `.vrkendang`

**Paket `.vrkendang`:** file zip biasa (tanpa enkripsi atau pengacakan) berisi:
```
kit.json
sounds/
  bass-1.wav ... kecil2-2.wav
```

**Contoh `kit.json` (mendukung `sound` bernilai null bila belum dipasangi suara):**

```json
{
  "formatVersion": 1,
  "kitId": "new-metro",
  "name": "New Metro",
  "version": 1,
  "canvasAspect": 2.0,
  "drums": [
    {
      "id": "bass",
      "model": "bass",
      "center": { "x": 0.25, "y": 0.55 },
      "radius": { "rx": 0.30, "ry": 0.30 },
      "divider": { "dx": -0.30, "dy": 0.0, "r": 0.80 },
      "zones": [
        { "id": "bass-1", "region": "main",     "sound": "sounds/bass-1.wav", "gain": 1.0 },
        { "id": "bass-2", "region": "crescent", "band": 0, "sound": null, "gain": 1.0 },
        { "id": "bass-3", "region": "crescent", "band": 1, "sound": null, "gain": 1.0 },
        { "id": "bass-4", "region": "crescent", "band": 2, "sound": null, "gain": 1.0 },
        { "id": "bass-5", "region": "crescent", "band": 3, "sound": null, "gain": 1.0 }
      ]
    },
    {
      "id": "kecil1",
      "model": "single",
      "center": { "x": 0.52, "y": 0.35 },
      "radius": { "rx": 0.17, "ry": 0.17 },
      "zones": [
        { "id": "kecil1-1", "region": "full", "sound": null, "gain": 1.0 }
      ]
    },
    {
      "id": "sedang",
      "model": "split",
      "center": { "x": 0.66, "y": 0.60 },
      "radius": { "rx": 0.20, "ry": 0.20 },
      "zones": [
        { "id": "sedang-1", "region": "upper", "sound": null, "gain": 1.0 },
        { "id": "sedang-2", "region": "lower", "sound": null, "gain": 1.0 }
      ]
    },
    {
      "id": "kecil2",
      "model": "split",
      "center": { "x": 0.82, "y": 0.38 },
      "radius": { "rx": 0.17, "ry": 0.17 },
      "zones": [
        { "id": "kecil2-1", "region": "upper", "sound": null, "gain": 1.0 },
        { "id": "kecil2-2", "region": "lower", "sound": null, "gain": 1.0 }
      ]
    }
  ]
}
```

**Aturan validasi (`KitValidator` di `:kit-core`):**
- `formatVersion` dikenali; `kitId` hanya huruf kecil, angka, dan tanda hubung; `drums` tidak kosong; semua `id` unik.
- `canvasAspect` > 0; `center.x`/`center.y` di 0.0-1.0; `rx`, `ry` > 0 dan wajar (≤ 1.0).
- `gain` di rentang 0.0-2.0.
- Region sesuai model (`bass`: `main` dan `crescent` dengan `band` 0-3 dan `divider` wajib; `split`: `upper` dan `lower`; `single`: `full`).
- Kolom `sound` dapat bernilai `null` (unassigned) atau string path relatif di dalam paket. Bila bernilai string, file WAV harus ada dan berformat PCM 16-bit (sample rate bebas, di-resample oleh engine).
- Perlindungan path traversal: menolak entri zip yang mengandung `..` atau path absolut.

---

### D12: Layout override pemain (posisi dan ukuran saja)

**Keputusan:** Edit tata letak pemain tidak menyalin file audio. Aplikasi menyimpan file kecil per kit di `files/layouts/<kitId>.json`:

```json
{
  "kitId": "new-metro",
  "baseVersion": 1,
  "drums": {
    "bass": { "center": { "x": 0.28, "y": 0.55 }, "scale": 1.1 }
  }
}
```

Saat kit dimuat, `LayoutOverride.apply(kit, override)` (di `:kit-core`) menghasilkan kit efektif: `center` diganti, `rx` dan `ry` dikalikan `scale`. Parameter `divider` relatif terhadap radius Bass sehingga ikut berskala otomatis. Tombol "Reset ke bawaan" menghapus file override.

---

### D13: Alat penyusun kit (Mode Developer) sangat krusial, hanya di build debug

**Keputusan:** UI dan logika Mode Developer berada di source set `app/src/debug/`; source set `release` hanya berisi stub kosong. Mode Developer diakses dari tombol Pengaturan di build debug dan menjadi instrumen utama developer untuk menguji aplikasi di perangkat fisik.

**Alur penyusunan kit:**
1. **Buka salinan kerja:** Developer membuka kit (misal kit awal `new-metro` atau membuka file `.vrkendang` lewat `ACTION_OPEN_DOCUMENT`). Kit dimuat ke `cacheDir/dev-kit/`.
2. **Pasang suara dari HP:** Developer mengetuk zona yang diinginkan, menekan tombol "Pilih Suara (WAV)", memilih file dari penyimpanan HP lewat pemilih file sistem. File disalin ke cache kerja dan langsung di-load ke C++ engine untuk diuji ketukannya seketika.
3. **Ubah parameter:** Posisi, ukuran, busur pembatas Bass, dan `gain` per zona dapat diatur secara interaktif di layar.
4. **Simpan/Ekspor:** Menyusun ulang seluruh paket dan menyimpan/mengekspor menjadi file `.vrkendang` (zip) via `ACTION_CREATE_DOCUMENT` atau menimpa file yang sedang dibuka.

---

### D14: Jalur trigger sentuhan ke audio

**Keputusan:** `KendangBoardView` menjalankan hit-testing di thread UI, lalu mengirim event `(sampleId, velocity/gain)` ke audio callback lewat antrean lock-free single-producer/single-consumer (ring buffer berukuran tetap) di C++. Audio callback hanya membaca antrean dan memulai voice; tidak ada mutex, alokasi, atau panggilan JNI di dalam callback. Bila `sampleId` bernilai unassigned/invalid, event dilewati tanpa aksi.

---

## Risks / Trade-offs

**[Risiko] File WAV dari HP memiliki format tidak standar (misal 24-bit atau non-PCM)** → *Mitigasi:* `KitValidator` memeriksa header RIFF/WAVE saat file dipilih. Jika format bukan PCM 16-bit, aplikasi menampilkan notifikasi galat yang jelas kepada developer ("Format harus WAV PCM 16-bit") dan tidak memuat file tersebut.

**[Risiko] Sentuhan pada zona yang belum memiliki suara menyebabkan crash** → *Mitigasi:* Penanganan unassigned sound secara aman di level hit-testing dan audio engine (D3 & D14): zona tanpa suara mengembalikan ID unassigned yang langsung diabaikan oleh audio callback.

**[Risiko] Latensi audio bervariasi antar perangkat** → *Mitigasi:* Oboe memilih AAudio atau OpenSL ES otomatis dan selalu meminta `LowLatency`; stream dibuka pada rate native. Estimasi latensi dari Oboe ditampilkan di layar debug Mode Developer.

**[Risiko] Clipping saat banyak voice aktif dan `gain` tinggi** → *Mitigasi:* Master gain dan limiter (soft-clip) di C++ (D3), rentang `gain` dibatasi 0.0-2.0.

**[Trade-off] Pengguna harus memasangkan file suara sendiri pada pengujian awal** → *Diterima:* Sesuai instruksi pemilik produk, aplikasi ini adalah instrumen pengujian internal tahap awal (bukan rilis publik), sehingga pemasangan suara langsung dari HP memberikan fleksibilitas tertinggi tanpa perlu build ulang APK.

---

## Open Questions

- **Bentuk dan lebar busur pembatas Bass:** Nilai awal `divider` (`dx`, `dy`, `r`) dikalibrasi langsung di perangkat fisik menggunakan Mode Developer.
- **Pemetaan `band` ke Bass-2...Bass-5:** Urutan `band` 0-3 dari atas ke bawah dipetakan ke zona Bass; dikonfirmasi developer saat memasangkan file rekaman suara dari HP.
- **Visual feedback sentuhan pada zona unassigned:** Apakah zona tanpa suara tetap memunculkan animasi tap highlight meskipun tidak bersuara? (Default: ya, visual tap highlight tetap aktif sebagai konfirmasi hit-testing bekerja).
