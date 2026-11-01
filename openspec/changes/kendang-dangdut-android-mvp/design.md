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
- Build CI yang andal memakai Oboe via Prefab Maven, tanpa git submodule, dan tanpa task build yang membuat file WAV.
- Alat penyusun kit di Mode Developer agar suara WAV dapat dipasangkan langsung dari penyimpanan HP ke tiap zona dan dapat langsung dibunyikan seketika tanpa coding dan tanpa build ulang.

**Non-Goals:**
- Tanda tangan rilis dan publikasi ke Play Store (VR Kendang adalah instrumen pengujian internal pada fase ini).
- Penyertaan aset audio bawaan di dalam APK (APK dibangun bersih tanpa audio bawaan; pengujian memakai sampel suara mandiri dari HP).
- Dukungan iOS, Flutter, atau lintas platform.
- Sinkronisasi kit lewat jaringan (OTA), dan berbagi kit online antar pemain.
- Integrasi ad SDK (ruang header disiapkan, tidak diintegrasikan).
- Cadangan otomatis (`.bak`) saat developer menimpa file kit (keputusan pemilik produk).
- Menambah atau menghapus kendang/zona di Mode Developer pada MVP; struktur kit tetap 4 kendang dari template dan perubahan struktur dicatat sebagai pekerjaan lanjutan.

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

### D3: Voice Pool pre-alokasi, Voice Stealing, master limiter, dan audio callback aman real-time

**Keputusan:** Mesin audio C++ memakai Voice Pool hingga 32 suara simultan yang dialokasikan saat inisialisasi tanpa ada suara yang saling memotong. Ketika melebihi 32 suara aktif, berlaku Voice Stealing di mana slot tertua diambil alih dengan micro-fade-out 1 ms untuk mencegah bunyi pop.

**Audio callback aman real-time:** Callback tidak boleh melakukan alokasi memori dinamis (`malloc`/`new`), tidak boleh memakai mutex/locking, dan tidak memanggil JNI. Trigger dari thread UI masuk lewat antrean lock-free single-producer/single-consumer (SPSC ring buffer berukuran tetap). Callback hanya membaca antrean dan memulai voice tanpa lock atau alokasi.

**Gain per zona dan master limiter:** Tiap voice dikalikan dengan gain zona masing-masing (rentang 0.0–2.0). Hasil penjumlahan seluruh voice aktif melewati master limiter sederhana (soft clip) sebelum ditulis ke buffer keluaran agar tidak menghasilkan distorsi clipping keras meskipun banyak voice aktif dengan gain tinggi.

**Siklus hidup (Pause):** Saat stream dijeda (pause) ketika aplikasi ke background, semua voice aktif dihentikan agar tidak ada bunyi menggantung saat kembali (resume).

**Penanganan zona tanpa suara:** Bila suatu zona kendang belum dipasangi file suara (`sound == null` / unassigned), sentuhan pada zona tersebut diabaikan secara aman (*graceful silence*) tanpa memicu alokasi atau playback, tanpa suara, dan dijamin zero-crash.

**Alasan:** Menjamin eksekusi callback stabil dan deterministik di thread real-time prioritas tinggi, aman dari dead-lock atau audio glitch, serta aman saat instrumen baru pertama kali dibuka sebelum kit dikonfigurasi.

---

### D4: Pembacaan header WAV di `:kit-core` (`WavParser`), DirectByteBuffer, dan C++ menyalin serta memiliki data

**Keputusan:** Sebuah `WavParser` (Kotlin murni) di modul `:kit-core` memeriksa header RIFF/WAVE, mengharuskan format PCM 16-bit mono, lalu mengirimkan byte PCM beserta sample rate aslinya ke C++ lewat JNI. C++ tidak mengurai file WAV.

**Kepemilikan data sample:** C++ menyalin data PCM ke memorinya sendiri saat pemanggilan load berlangsung. Setelah pemanggilan load kembali ke Kotlin, Kotlin bebas melepas buffer, dan C++ tidak pernah menyimpan pointer ke buffer milik Kotlin.

**Resampling native & reconnect:** Stream Oboe dibuka tanpa menetapkan sample rate tertentu, lalu sample rate native yang diberikan stream dibaca dan dipakai sebagai target resampling. C++ menyimpan PCM asli, lalu membuat salinan yang di-resample ke rate stream memakai interpolasi linier. Bila stream dibuka ulang (misalnya saat headphone dicolok/dicabut) dan rate berubah, sample di-resample ulang dari PCM asli dan pitch tetap benar.

**Penanganan kegagalan memuat WAV:** Bila satu file WAV gagal dimuat, zona tersebut diperlakukan sebagai zona tanpa suara (unassigned), galat dicatat ke log, dan aplikasi tidak crash.

**Alasan:** Pemisahan tanggung jawab yang bersih: Kotlin berurusan dengan file I/O, parsing WAV, dan validasi format, sedangkan C++ murni mengelola memori audio dan playback real-time tanpa risiko dangling pointer atau crash akibat format file rusak.

---

### D5: Kanvas relatif 0.0-1.0 dengan `canvasAspect` dan aspect-fit

**Keputusan:** `kit.json` mendeklarasikan `canvasAspect` (lebar : tinggi kanvas, misalnya 2.0).
- Origin di kiri atas kanvas. Posisi `x` dan `y` berada di rentang 0.0-1.0 terhadap lebar dan tinggi kanvas.
- Radius (`rx`, `ry`) adalah pecahan dari **tinggi** kanvas, sehingga lingkaran tetap lingkaran.
- Untuk perhitungan geometri dipakai *satuan kanvas* di mana tinggi kanvas = 1.0 dan lebar = `canvasAspect`: `X_u = x * canvasAspect`, `Y_u = y`.
- Saat render, kanvas di-fit ke area `KendangBoardView` berdasarkan tinggi, dipusatkan, dan sisa ruang horizontal menjadi margin.

**Alasan:** Menjaga rasio visual kendang tetap konsisten di berbagai ukuran dan rasio layar HP tanpa distorsi.

---

### D6: Tata letak utama & navigasi halaman penuh (Parent-Child Menu)

**Keputusan:** Layout utama menggunakan layar penuh landscape (`ConstraintLayout` / `LinearLayout`): `HeaderView` ramping di bagian atas (~44–48dp) dan `KendangBoardView` di bawahnya mengisi seluruh lebar dan sisa tinggi layar tanpa kotak batas sempit di tengah. Seluruh fitur dibuka sebagai halaman penuh mandiri (tanpa dialog *popup*).

```
MainActivity (landscape-locked, Immersive Sticky, Full Screen)
├── HeaderView (top bar)
│   ├── MenuButton (start, Parent: "☰ Menu") + Label Kit Aktif
│   │   ├── Child 1: "Pilih Kit"  -> membuka Halaman Pilih Kit (Full Layar: Nama Kit + Status "Aktif" / Tombol "Pakai")
│   │   └── Child 2: "Sesuaikan"  -> membuka Halaman Sesuaikan (Full Layar: Geser 1 jari & Cubit 2 jari)
│   ├── AdSlotContainer (center, GONE di MVP)
│   └── DevModeButton (end, hanya build debug: "Mode Developer") -> membuka Halaman Mode Developer (Full Layar)
└── KendangBoardView (mengisi penuh area bermain di bawah header)
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
- **Model `split` (Sedang, Kecil 2, dan kendang baru):**
  - Jika 2 zona default tanpa rotasi: `upper` bila `Y < cy`, `lower` bila sebaliknya (`Y >= cy`, termasuk `Y == cy`). Pemetaan zona: Sedang-1 = `lower`, Sedang-2 = `upper`; Kecil2-1 = `lower`, Kecil2-2 = `upper`.
  - Jika dirotasi atau memiliki 3–4 zona: pembagian juring radial ($180^\circ$ untuk 2 zona, $120^\circ$ untuk 3 zona, $90^\circ$ untuk 4 zona) dengan parameter rotasi sudut $\phi$ (`divider.angle`, $0^\circ - 360^\circ$):
    - $\theta = (\text{atan2}((Y - cy)/ry, (X - cx)/rx) \times \frac{180}{\pi} + 360^\circ) \pmod{360^\circ}$
    - $\theta_{\text{rel}} = (\theta - \phi + 360^\circ) \pmod{360^\circ}$
    - $\text{zoneIndex} = \lfloor \theta_{\text{rel}} / (360^\circ / N) \rfloor$
- **Model `single` (Kecil 1):** dalam oval, langsung zona tunggal (Kecil1-1).

**Urutan evaluasi dan Z-Order Overlap:** mengikuti urutan elemen array `drums` di `kit.json` (indeks 0 dicek lebih dulu dan berada di lapisan teratas / Top Layer). Saat merender, kendang digambar dari indeks belakang ke depan (`drums.asReversed()`), sehingga kendang di indeks 0 tampak di paling atas secara visual dan menangkap sentuhan saat tumpang tindih. Pada Mode Developer, kendang yang disentuh atau diedit otomatis dipindahkan ke indeks 0 (*Bring to Front*). Bila tidak ada yang cocok, sentuhan diabaikan.

---

### D8: Penyimpanan

**Keputusan:**

| Data | Lokasi | Akses |
|---|---|---|
| Kit bawaan | `assets/kits/<kitId>/` di APK (`kit.json` dan `sounds/` bila ada) | hanya baca (konfigurasi tata letak awal) |
| Kit tersimpan internal | `filesDir/kits/<kitId>/` di penyimpanan internal aplikasi (`kit.json` dan `sounds/`) | baca oleh daftar kit pemain; tulis oleh Mode Developer (`Simpan` / `+ Kit Baru`) |
| Layout override pemain | penyimpanan internal aplikasi, `files/layouts/<kitId>.json` | baca/tulis oleh Halaman Sesuaikan pemain |
| Salinan kerja developer | `filesDir/dev-kit/` (direktori kerja kit yang sedang disunting) | hanya build debug |
| File suara WAV & `.vrkendang` developer | dipilih lewat Storage Access Framework (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`) | hanya build debug (tombol `Pilih WAV`, `Impor`, dan `Ekspor`) |

**Alasan:** Memisahkan penyimpanan kerja internal aplikasi (`Simpan` dan `+ Kit Baru` di dalam aplikasi ke `filesDir/kits/<kitId>/`) dari aksi distribusi file ZIP `.vrkendang` ke memori HP (`Impor` dan `Ekspor` via SAF), bebas izin runtime penyimpanan kompleks (`READ/WRITE_EXTERNAL_STORAGE` atau `MANAGE_EXTERNAL_STORAGE`), serta memberikan alur penyuntingan yang cepat tanpa berulang kali membuka *file picker* sistem.

---

### D9: GitHub Actions, Ubuntu latest, JDK 17 Temurin, cache Gradle

**Keputusan:** `ubuntu-latest`, `actions/setup-java@v4` (distribusi `temurin`, JDK 17), `cache: gradle`. Workflow juga menyediakan `workflow_dispatch` agar build dapat dipicu manual dari halaman GitHub Actions.

**Pemicu, izin, dan concurrency:** Push ke `main` memicu build, kecuali push yang hanya mengubah dokumen (`openspec/**`, `**/*.md`); perubahan di `assets/kits/**` tetap memicu build. Workflow memakai `permissions: contents: read`, dan `concurrency` membatalkan run lama saat push baru masuk.

**Versi dipatok:** Versi Oboe eksak (bukan rentang atau `latest`), `ndkVersion`, versi CMake, dan versi Android Gradle Plugin dipatok eksak di konfigurasi Gradle. Action GitHub dipatok ke mayor: `actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`.

**ABI:** Build menghasilkan `arm64-v8a` dan `armeabi-v7a`; `x86_64` tidak dibangun. Alasannya, pengujian dilakukan hanya di HP atau tablet fisik berbasis ARM dan membatasi ABI mengurangi ukuran APK serta waktu build; pengujian tidak menggunakan emulator.

**Keystore debug tetap:** APK debug ditandatangani dengan `app/debug.keystore` melalui `signingConfigs.debug`, sehingga APK dari build berbeda dapat dipasang sebagai pembaruan tanpa uninstall. Ini kunci debug yang tidak rahasia; penandatanganan rilis tetap non-goal.

**Langkah workflow:**
1. Checkout repository
2. Setup Java 17 (Temurin) dengan cache Gradle
3. `chmod +x gradlew`
4. `./gradlew test` (unit test `:kit-core`: hit-testing, parser, validator, pembaca dan penyusun paket termasuk uji path traversal dan batas keamanan ZIP, pembuat kit dari template, penggabungan override, WavParser, serta validasi integritas seluruh `kit.json` bawaan; lokasi `assets/kits` diberikan lewat `systemProperty` Gradle dan tes gagal bila tidak menemukan kit)
5. `./gradlew assembleRelease --stacktrace` (tanpa penandatanganan), lalu periksa dex APK release untuk memastikan tidak ada kelas atau paket Mode Developer (misalnya `com/vrkendang/kitauthoring`) menggunakan `apkanalyzer dex packages` atau `unzip -p` + `grep`
6. Bila langkah test gagal, unggah `**/build/reports/tests/**` sebagai artifact dengan `if: failure()`
7. `./gradlew assembleDebug --stacktrace`
8. Unggah `app-debug.apk` sebagai artifact `VRKendang-Debug-<sha7>` dengan retensi 7 hari

---

### D10: Skema `kit.json` dan paket `.vrkendang`

**Paket `.vrkendang`:** file zip biasa (tanpa enkripsi atau pengacakan) berisi:
```
kit.json
sounds/
  bass-1.wav ... kecil2-2.wav
```
Paket hanya memuat file WAV yang dirujuk oleh `kit.json`, dengan nama `sounds/<zoneId>.wav`; file WAV yatim (tidak dirujuk) tidak disertakan.

**Batas keamanan paket:**
- Maksimum 100 entri dalam ZIP.
- Total ukuran hasil ekstraksi maksimum 100 MB.
- Ukuran maksimum tiap file WAV adalah 5 MB (karena seluruh WAV aktif dimuat ke RAM).
- Seluruh entri ZIP diperiksa dan divalidasi strukturnya (termasuk pemeriksaan path traversal `..` dan path absolut) sebelum file apapun diekstrak atau ditulis ke penyimpanan.

**Contoh `kit.json` bawaan MVP (seluruh zona berstatus unassigned / `"sound": null`):**

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
        { "id": "bass-1", "region": "main",     "sound": null, "gain": 1.0 },
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
        { "id": "sedang-1", "region": "lower", "sound": null, "gain": 1.0 },
        { "id": "sedang-2", "region": "upper", "sound": null, "gain": 1.0 }
      ]
    },
    {
      "id": "kecil2",
      "model": "split",
      "center": { "x": 0.82, "y": 0.38 },
      "radius": { "rx": 0.17, "ry": 0.17 },
      "zones": [
        { "id": "kecil2-1", "region": "lower", "sound": null, "gain": 1.0 },
        { "id": "kecil2-2", "region": "upper", "sound": null, "gain": 1.0 }
      ]
    }
  ]
}
```

**Aturan validasi (`KitValidator` di `:kit-core`):**
- `formatVersion` dikenali; `kitId` hanya huruf kecil, angka, dan tanda hubung; `drums` tidak kosong; semua `id` unik.
- `canvasAspect` > 0; `center.x`/`center.y` di rentang -3.0 sampai 3.0 (mengizinkan kendang digeser sebagian keluar kanvas); `rx`, `ry` > 0 dan ≤ 5.0 (mengizinkan perbesaran skala hingga 5x lipat).
- `gain` di rentang 0.0-2.0.
- Parameter `divider` model `bass`: `dx` dan `dy` di rentang -3.0 sampai 3.0 (relatif terhadap radius Bass), serta `r` > 0 dan ≤ 3.0. Rentang ini ditetapkan agar busur tetap wajar dan dapat diuji.
- Region sesuai model (`bass`: `main` dan `crescent` dengan `band` 0-3 dan `divider` wajib; `split`: `upper` dan `lower`; `single`: `full`).
- Kolom `sound` boleh bernilai `null` (zona tanpa suara). Bila `sound` tidak null: file harus ada di paket/direktori, bukan path absolut, tidak mengandung `..`, dan berformat WAV PCM 16-bit mono valid.
- Perlindungan path traversal: menolak entri zip yang mengandung `..` atau path absolut.

---

### D11: Layout override pemain di Halaman Sesuaikan (geser 1 jari dan cubit 2 jari)

**Keputusan:** Halaman Sesuaikan (`Sesuaikan` dari menu `☰ Menu`) dikhususkan murni untuk pemain mengatur posisi dan ukuran 4 kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`) secara langsung di atas kanvas tanpa bilah bawah atau slider yang menutupi kendang:
- **Geser 1 jari** memindahkan posisi kendang terpilih, dapat digeser sebagian melebihi kanvas dengan batas retensi aman (safety retention: mempertahankan setidaknya 20% radius di dalam batas kanvas agar kendang tidak hilang total).
- **Cubit 2 jari (`ScaleGestureDetector`)** mengubah skala ukuran kendang terpilih secara proporsional di rentang `0.5x–5.0x` (hingga 5 kali lipat).
- Header bagian atas hanya memuat `✕ Batal` (kiri), petunjuk ringkas di tengah (`"Sesuaikan: Geser kendang untuk pindah posisi • Cubit 2 jari untuk ubah ukuran"`), serta `↺ Reset` dan `✓ Simpan` (kanan).

Edit tata letak pemain tidak menyalin file audio. Aplikasi menyimpan file kecil per kit di `files/layouts/<kitId>.json`:

```json
{
  "kitId": "new-metro",
  "baseVersion": 1,
  "drums": {
    "bass": { "center": { "x": -0.15, "y": 0.55 }, "scale": 2.5 }
  }
}
```

Saat kit dimuat, `LayoutOverride.apply(kit, override)` (di `:kit-core`) menghasilkan kit efektif:
- `scale` dibatasi di rentang 0.5–5.0, dan `center` dijepit dengan batas retensi aman sehingga setidaknya 20% radius kendang tetap menyentuh kanvas.
- ID kendang yang tidak dikenal dalam file override diabaikan.
- File override yang rusak atau tidak dapat dibaca diabaikan sehingga tata letak bawaan tetap dipakai tanpa crash.
- Penulisan override dilakukan secara atomik (tulis ke file sementara `.tmp` lalu ganti nama) untuk mencegah kerusakan berkas bila penulisan terputus.
- Kit efektif hasil penggabungan harus tetap lolos validasi `KitValidator`. Tombol `↺ Reset` meminta konfirmasi sebelum menghapus file override.
- Layout override pemain tidak diterapkan pada salinan kerja di Mode Developer (Mode Developer menyunting kit secara independen).

---

### D12: Alat penyusun kit (Mode Developer) hanya di build debug — Konfigurasi per Kendang

**Keputusan:** UI dan logika Mode Developer berada di source set `app/src/debug/`; source set `release` hanya berisi stub kosong. Mode Developer diakses dari tombol `Mode Developer` di sisi kanan Header pada build debug dan dibuka sebagai halaman penuh mandiri.

**Penyimpanan kit internal & salinan kerja:** Salinan kerja yang sedang diedit disimpan di `filesDir/dev-kit/`. Kit hasil simpan internal disimpan di `filesDir/kits/<kitId>/` sehingga langsung muncul di Halaman Pilih Kit pemain tanpa harus mengekspor file `.vrkendang` ke memori HP.

**Alur penyusunan kit per kendang:**
1. **Header Mode Developer yang rapi dan terpisah fungsinya:**
   - `← Tutup` (kiri) dan nama kit aktif (`Kit: <Nama>`).
   - `Impor` dan `Ekspor` (tengah): khusus untuk membuka atau mengekspor paket ZIP `.vrkendang` dari/ke penyimpanan HP via Storage Access Framework (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`).
   - `+ Kit Baru` (kanan): menyimpan konfigurasi saat ini sebagai **kit baru** di penyimpanan internal aplikasi (`filesDir/kits/<kitId>/`) dengan mengisi nama kit baru dan properti terkait tanpa membuka *file picker* HP.
   - `✓ Simpan` (kanan): menyimpan perubahan langsung menimpa kit yang sedang diedit di penyimpanan internal aplikasi (`filesDir/kits/<kitId>/` dan `filesDir/dev-kit/`) tanpa membuka *file picker* HP.
   - `▶ Uji Main` (kanan): beralih antara mode konfigurasi (`EDIT`) dan mode uji ketuk suara langsung (`UJI`).
2. **Pola Pemilihan per Kendang (Panel Kiri → Panel Kanan):**
   - **Panel Kiri (`PILIH KENDANG`)** menampilkan daftar 4 kendang sesuai `kit.json`: **`Bass`**, **`Kecil 1`**, **`Sedang`**, dan **`Kecil 2`** beserta pratinjau visual 4 kendang di bawahnya. Tombol pengubah urutan prioritas (`▲ Naik` / `▼ Turun`) dihapus dari UI karena urutan evaluasi mengikuti definisi tetap kit.
   - **Panel Kanan (`KONFIGURASI: <NAMA KENDANG>`)** menampilkan pengaturan khusus untuk kendang yang sedang dipilih di panel kiri:
     - **Pilihan Zona**: chip zona milik kendang tersebut (misal `bass-1` s/d `bass-5` saat memilih `Bass`; `kecil1-1` saat memilih `Kecil 1`; `sedang-1` & `sedang-2` saat memilih `Sedang`; `kecil2-1` & `kecil2-2` saat memilih `Kecil 2`).
     - **Sampel Suara Zona**: menampilkan nama file WAV aktif atau `"Belum ada suara"`, tombol `Pilih WAV` (membuka pemilih file audio HP dan memvalidasi WAV PCM 16-bit mono via `WavParser`), `▶ Tes` (membunyikan sampel zona saat ini), `Hapus` (mengosongkan suara zona menjadi `null`), serta slider `Volume Zona (Gain)` (`0.0–2.0`).
     - **Parameter Busur Bass (`dx`, `dy`, `r`)**: khusus ditampilkan saat kendang `Bass` dipilih, dengan slider interaktif (`dx` dan `dy` `-3.0..3.0`, `r` `0.1..3.0`) yang memperbarui kurva busur `C_div` secara real-time.
3. **Indikator perubahan belum disimpan:** Keluar dari Mode Developer atau mengganti kit saat ada perubahan belum disimpan meminta konfirmasi simpan atau buang.

**Komponen `:kit-core`:** `KitPackageWriter` menyusun paket `.vrkendang` dan `KitFactory` membuat kit baru dari template; keduanya Kotlin/JVM murni dan diuji unit test bersama `KitPackageReader`/`KitValidator`.

---

### D13: Jalur trigger sentuhan ke audio callback aman real-time

**Keputusan:** `KendangBoardView` menjalankan hit-testing di thread UI, lalu mengirim event `(slotIndex, gain)` ke audio callback lewat antrean lock-free single-producer/single-consumer (SPSC ring buffer berukuran tetap) di C++.
Audio callback aman real-time: hanya membaca antrean dan memulai voice; tidak boleh ada alokasi memori dinamis (`malloc`/`new`), tidak memakai mutex/locking, dan tidak memanggil JNI di dalam callback. Bila `slotIndex` bernilai unassigned/invalid (zona tanpa suara), event dilewati tanpa aksi.

---

## Risks / Trade-offs

**[Verifikasi hit-testing awal]** Verifikasi hit-testing di perangkat pada awal pengujian dilakukan lewat visual feedback (highlight overlay zona), bukan lewat nada suara, karena kit awal New Metro berstatus hening (semua sound null).

**[Pengukuran latensi audio]** Pengukuran latensi audio fisik (keluaran audio ≤ 20 ms, sentuh-ke-bunyi total ≤ 40 ms) baru dapat dilakukan setelah developer memasangkan minimal satu file WAV ke zona kendang via Mode Developer.

**[Risiko] File WAV dari HP memiliki format tidak standar (misal 24-bit atau non-PCM)** → *Mitigasi:* `WavParser` di `:kit-core` memeriksa header RIFF/WAVE saat file dipilih/dimuat. Jika format bukan PCM 16-bit mono, aplikasi menampilkan notifikasi galat yang jelas kepada developer ("Format harus WAV PCM 16-bit mono") dan tidak memuat file tersebut.

**[Risiko] Sentuhan pada zona yang belum memiliki suara menyebabkan crash** → *Mitigasi:* Penanganan unassigned sound secara aman di level hit-testing dan audio engine (D3 & D13): zona tanpa suara mengembalikan ID unassigned yang langsung diabaikan oleh audio callback.

**[Risiko] Latensi audio bervariasi antar perangkat** → *Mitigasi:* Oboe memilih AAudio atau OpenSL ES otomatis (di Android 8.0 API 26 otomatis OpenSL ES) dan selalu meminta `LowLatency`; stream dibuka tanpa menetapkan sample rate tetap, lalu rate native dibaca dari stream. Estimasi latensi dari Oboe ditampilkan di layar debug Mode Developer.

**[Risiko] Clipping saat banyak voice aktif dan `gain` tinggi** → *Mitigasi:* Master limiter (soft-clip) dan perkalian gain per voice di C++ (D3), rentang `gain` dibatasi 0.0-2.0.

**[Trade-off] Pengguna harus memasangkan file suara sendiri pada pengujian awal** → *Diterima:* Sesuai instruksi pemilik produk, aplikasi ini adalah instrumen pengujian internal tahap awal (bukan rilis publik), sehingga pemasangan suara langsung dari HP memberikan fleksibilitas tertinggi tanpa perlu build ulang APK.

---

## Open Questions

- **Bentuk dan lebar busur pembatas Bass:** Nilai awal `divider` (`dx`, `dy`, `r`) dikalibrasi langsung di perangkat fisik menggunakan Mode Developer.
- **Pemetaan `band` ke Bass-2...Bass-5:** Urutan `band` 0-3 dari atas ke bawah dipetakan ke zona Bass; dikonfirmasi developer saat memasangkan file rekaman suara dari HP.
- **Visual feedback sentuhan pada zona unassigned:** Apakah zona tanpa suara tetap memunculkan animasi tap highlight meskipun tidak bersuara? (Default: ya, visual tap highlight tetap aktif sebagai konfirmasi hit-testing bekerja).
