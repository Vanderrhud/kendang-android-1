# Design

## Context

Proyek ini adalah aplikasi Android baru dari nol. Tidak ada kode yang diwarisi. Kendala utama yang membentuk semua keputusan desain:

1. **Pengembangan "blind" di VPS** — kode ditulis di VPS tanpa Android SDK/NDK lokal; kompilasi dan pengujian hanya terjadi di GitHub Actions. Setiap kesalahan kompilasi baru terlihat setelah push dan workflow selesai.
2. **Zero-rebuild workflow** — setiap kali ada perubahan kecil pada data kit (posisi, ukuran, atau suara), proses build ulang APK (3–5 menit) sangat tidak efisien. Arsitektur harus memungkinkan perubahan konten kit tanpa build ulang.
3. **Latensi audio adalah fitur inti** — aplikasi ini adalah instrumen musik. Latensi yang terasa (>20ms) langsung merusak pengalaman bermain.
4. **Layar landscape sempit** — HP modern dengan rasio 20:9 di landscape memiliki tinggi fisik terbatas; ruang vertikal untuk UI non-bermain harus diminimalkan.

## Goals / Non-Goals

**Goals:**
- Arsitektur tiga lapisan yang bersih: Data Kit (JSON) → Tampilan & Touch (Kotlin) → Audio (C++/Oboe), dengan ketergantungan yang searah.
- Semua logika hitung (hit-testing, parsing JSON) dipisahkan dari framework Android agar bisa diuji dengan JVM unit test di CI.
- Build CI yang andal dan dapat diprediksi di GitHub Actions menggunakan Oboe via Prefab Maven, tanpa git submodule.
- Mode kalibrasi yang memungkinkan iterasi cepat pada tata letak kit langsung di perangkat fisik.

**Non-Goals:**
- Dukungan iOS, Flutter, atau lintas platform.
- Sistem sinkronisasi kit melalui jaringan (OTA sync) — dicatat sebagai potensi masa depan tetapi tidak diimplementasikan di MVP ini.
- Tanda tangan rilis dan penerbitan ke Play Store.
- Fitur iklan — ruang header disiapkan tetapi integrasi ad SDK tidak dilakukan.
- Pemilihan kit lebih dari satu (hanya New Metro di MVP); tombol pilih kit ada tetapi menampilkan satu pilihan saja.

## Decisions

### D1: Satu Custom View Tunggal (`KendangBoardView`) untuk rendering dan touch

**Keputusan:** Seluruh area bermain dirender dan ditangani touch-nya oleh satu `KendangBoardView`, bukan beberapa View terpisah per kendang.

**Alasan:** Android mendistribusikan event touch berdasarkan *bounding box* persegi panjang setiap View. Karena bounding box kendang (oval/lingkaran) yang berdekatan bisa bertubrukan (sudut transparan tumpang tindih), menggunakan View terpisah per kendang membutuhkan koordinasi `dispatchTouchEvent` yang rumit dan rentan terhadap *dropped pointer* saat multi-touch intensif. Satu View tunggal menerima seluruh `MotionEvent` (semua pointer sekaligus) dan melakukan hit-testing secara internal berdasarkan koordinat matematis, bukan bounding box.

**Alternatif yang ditolak:** 4 Custom View terpisah (satu per kendang) — ditolak karena risiko konflik bounding box dan kompleksitas koordinasi multi-touch antar View.

---

### D2: Oboe via Maven Prefab (bukan git submodule)

**Keputusan:** Dependensi Oboe ditarik menggunakan sistem Prefab dari Maven Central (`google.oboe:oboe`) dan dikonfigurasi di CMakeLists.txt dengan `find_package(oboe REQUIRED CONFIG)`.

**Alasan:** Pendekatan git submodule mengharuskan GitHub Actions meng-compile seluruh source Oboe dari nol setiap build (menambah waktu 5–10 menit) dan rentan terhadap masalah versi NDK. Prefab mengirimkan Oboe sebagai biner yang sudah dikompilasi untuk semua ABI yang didukung, sehingga build CI lebih cepat (~2–3 menit total) dan deterministik.

**Alternatif yang ditolak:** Git submodule (lambat dan kompleks di CI), SoundPool/ExoPlayer (tidak mendukung latensi rendah untuk perkusi), AAudio langsung tanpa Oboe (tidak ada fallback otomatis ke OpenSL ES untuk perangkat lama).

---

### D3: Voice Pool pre-alokasi dengan Voice Stealing

**Keputusan:** Mesin audio C++ menggunakan pool 32 slot suara yang dialokasikan di memori saat inisialisasi. Audio callback tidak pernah melakukan `malloc`/`new`. Saat semua slot terisi, slot tertua diambil alih (voice stealing) dengan micro-fade-out 1ms untuk mencegah bunyi pop.

**Alasan:** Audio callback Oboe berjalan di thread real-time berprioritas tinggi. Alokasi memori dinamis di thread ini memicu jitter GC (Java) atau heap locking (C++) yang menyebabkan glitch audio. Pre-alokasi memastikan audio callback selesai dalam waktu tetap (deterministic latency).

**Kapasitas 32 dipilih karena:** Dengan decay suara kendang 0.3–1.5 detik dan maksimum 10 jari, rolling paling agresif tidak akan melebihi 30 suara aktif bersamaan secara realistis. 32 memberikan margin aman dengan overhead CPU minimal.

---

### D4: Kotlin membaca WAV → DirectByteBuffer → C++ (bukan C++ baca file langsung)

**Keputusan:** Kotlin bertanggung jawab membaca file WAV (dari `assets/` atau storage) menjadi `DirectByteBuffer`, kemudian menyerahkan pointer memori tersebut ke C++ via JNI.

**Alasan:** Android modern (API 29+) membatasi akses file sistem langsung dari kode native di beberapa konteks. Lebih penting lagi, perbedaan antara file di `assets/` (dibaca dengan `AssetManager`) dan file di storage (dibaca dengan `File`/`InputStream`) hanya bisa ditangani dengan elegan di sisi Kotlin/Java. Kode C++ Oboe menjadi murni: hanya berurusan dengan pointer memori dan tidak perlu tahu dari mana data berasal. Ini juga menyederhanakan unit testing lapisan audio C++.

---

### D5: Koordinat relatif (0.0–1.0) di kit.json dengan Aspect-Fit berbasis tinggi board

**Keputusan:** Semua nilai posisi dan radius di `kit.json` menggunakan koordinat relatif 0.0–1.0. Saat render, `KendangBoardView` menghitung skala berdasarkan `boardHeight` (tinggi area bermain) dan memastikan satu unit koordinat = satu unit piksel yang sama untuk X dan Y (aspect-fit square).

**Alasan:** Perangkat Android memiliki variasi rasio layar yang sangat besar (4:3 tablet hingga 20:9 HP). Koordinat piksel tetap akan membuat kendang tampak gepeng atau melar di perangkat berbeda. Aspect-fit berbasis tinggi memastikan lingkaran tetap lingkaran di semua perangkat; ruang horizontal ekstra pada HP sangat panjang menjadi margin yang nyaman untuk posisi tangan.

---

### D6: Arsitektur tata letak utama — ConstraintLayout dengan dua zona

**Keputusan:** Layout utama Activity menggunakan `ConstraintLayout` yang mengikat `HeaderView` di atas (tinggi tetap ~48dp) dan `KendangBoardView` di bawahnya mengisi sisa tinggi layar.

```
Activity (ConstraintLayout, landscape-locked, Immersive Sticky)
├── HeaderView (height: 48dp, top: 0)
│   ├── KitSelectorButton (start)
│   ├── AdSlotContainer (center, GONE di MVP)
│   └── SettingsButton (end)
└── KendangBoardView (top: bottom_of_header, bottom: 0, match_parent width)
```

---

### D7: Geometri hit-testing — murni aljabar, nol ketergantungan Android

**Keputusan:** Semua logika hit-testing diimplementasikan dalam kelas Kotlin murni (`HitTester`, `DrumShape`, `DrumArea`) tanpa impor `android.*`.

Rumus yang digunakan:
- **Kulit oval/lingkaran:** `((x-cx)/rx)² + ((y-cy)/ry)² ≤ 1.0`
- **Area 1 Bass (zona utama):** Titik berada di dalam lingkaran Bass DAN di dalam lingkaran pembatas (`C_div`). Lingkaran `C_div` berpusat sedikit di sebelah kiri pusat Bass dengan radius yang menghasilkan busur cembung ke kanan (menonjol ke kanan ~40% diameter).
- **Area 2–5 Bass (zona bulan sabit):** Titik berada di dalam lingkaran Bass DAN di luar lingkaran `C_div`. Posisi vertikal `y` menentukan bilah (dibagi 4 zona sama tinggi dalam rentang vertikal kulit Bass).
- **Sedang, Kecil 2 (2 zona):** Dalam oval, bandingkan `y` dengan `cy`.
- **Kecil 1 (1 zona):** Dalam oval → langsung Kecil1-1.

Urutan evaluasi per sentuhan (x, y): Bass → Kecil 1 → Sedang → Kecil 2. Jika tidak ada yang cocok → abaikan (badan/background).

---

### D8: Scoped Storage — path `Android/data/<pkg>/files/kits/`

**Keputusan:** Kit dari storage perangkat disimpan di `Context.getExternalFilesDir("kits")` yang memetakan ke `Android/data/<package>/files/kits/`.

**Alasan:** Path ini tidak memerlukan izin `READ_EXTERNAL_STORAGE` atau `MANAGE_EXTERNAL_STORAGE` di Android 10+. File di path ini dapat diakses via kabel USB/file manager perangkat. Ini adalah pendekatan Scoped Storage yang bersih dan sesuai kebijakan Google Play.

---

### D9: Format WAV dummy — nada sintetik dari script Gradle task

**Keputusan:** Proyek menyertakan Gradle task yang men-generate 10 file WAV sintetik (nada sinus dengan decay singkat) menggunakan kode Kotlin/Java murni. Task ini dijalankan sebagai bagian dari `preBuild`, sehingga file dummy selalu ada saat build pertama CI.

**Frekuensi 10 nada dummy:** Bass-1: 65Hz, Bass-2: 90Hz, Bass-3: 130Hz, Bass-4: 180Hz, Bass-5: 230Hz, Sedang-1: 320Hz, Sedang-2: 460Hz, Kecil1-1: 620Hz, Kecil2-1: 780Hz, Kecil2-2: 980Hz.

**Alasan:** Frekuensi yang berbeda memudahkan pemain/pengembang mendeteksi dengan telinga apakah sentuhan jari mengenai zona yang tepat saat testing manual di HP.

---

### D10: GitHub Actions — Ubuntu latest, JDK 17 Temurin, cache Gradle

**Keputusan:** Workflow menggunakan `ubuntu-latest`, `actions/setup-java@v4` dengan distribusi `temurin` dan JDK 17, serta `cache: gradle` untuk mempercepat build kedua dan seterusnya.

**Struktur workflow:**
1. Checkout source code
2. Setup Java 17 (Temurin)
3. `chmod +x gradlew`
4. `./gradlew test` (unit test hit-testing + JSON parsing)
5. `./gradlew assembleDebug --stacktrace`
6. Upload `app-debug.apk` sebagai artifact (retensi 7 hari)

## Risks / Trade-offs

**[Risiko] Kegagalan kompilasi NDK/C++ baru terlihat setelah push** → *Mitigasi:* Seluruh logika hit-testing dan parsing JSON dibuat tanpa NDK (Kotlin murni), sehingga bagian C++ seminimal mungkin (hanya audio engine). Unit test Kotlin bisa dijalankan di CI sebelum APK diproduksi. Pesan error dari NDK cukup spesifik di log GitHub Actions.

**[Risiko] Latensi audio bervariasi antar perangkat Android** → *Mitigasi:* Oboe secara otomatis memilih AAudio (Android 8.1+) atau OpenSL ES, dan selalu menggunakan `PerformanceMode::LowLatency`. Query native sample rate (D1) meminimalkan resampling. Latensi akhir hanya bisa diverifikasi dengan tes manual di perangkat fisik.

**[Risiko] File `kit.json` yang rusak bisa membuat aplikasi crash** → *Mitigasi:* Lapisan pembacaan JSON (D4) memvalidasi struktur sebelum menyerahkan ke View/Audio. Kit yang tidak valid ditolak dengan pesan error yang dicatat; aplikasi jatuh kembali ke kit bawaan.

**[Trade-off] Mode Kalibrasi vs. keterbacaan kode** → Mode kalibrasi menambah kompleksitas state di `KendangBoardView` (mode bermain vs. mode drag). Ini diatasi dengan pemisahan bersih: `KendangBoardView` hanya tahu "sedang kalibrasi atau tidak", dan seluruh logika UI kalibrasi (slider, tombol) berada di layer terpisah di atasnya.

**[Trade-off] Satu Voice Pool global vs. per-kit** → Voice Pool 32 slot bersifat global (tidak per-kit). Ini cukup untuk MVP dengan satu kit 10 suara. Jika kit masa depan memiliki banyak suara atau karakter berbeda, Voice Pool perlu dibuat configurable per-kit.

## Open Questions

- **Lebar persis busur pembatas Bass (Area 1 vs Area 2–5):** Nilai ~40% dari diameter dipilih sebagai titik awal. Nilai yang tepat akan dikalibrasi langsung di perangkat menggunakan Mode Kalibrasi; tidak perlu diputuskan sebelum implementasi.
- **Detail animasi visual feedback (efek denyut):** Jenis animasi (flash highlight, scale pulse, dll.) belum ditetapkan. Implementasi awal akan menggunakan flash highlight sederhana (overlay alpha fade 100ms). Jenis animasi final ditentukan setelah tes manual di HP.
- **Spesifikasi teknis WAV rekaman asli:** Sample rate, bit depth, dan channel WAV asli akan diselaraskan dengan format dummy (48 kHz, 16-bit, Mono) saat aset rekaman asli siap dibuat oleh pemilik produk.
