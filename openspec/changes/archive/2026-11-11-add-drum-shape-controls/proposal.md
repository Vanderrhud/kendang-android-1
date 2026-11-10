# Proposal

## Why

Saat ini di Mode Developer, ukuran kendang hanya dikendalikan oleh satu slider (`Radius/Ukuran`) yang mengunci jari-jari horizontal dan vertikal sama besar (`rx == ry`, bulat sempurna) serta belum memiliki kontrol kemiringan/rotasi kendang secara utuh. Padahal, untuk memvalidasi peregangan aset gambar PNG menjadi elips serta menciptakan ilusi visual perspektif *Point of View* (POV) seolah kendang dimainkan di depan pemain, pembuat kit membutuhkan kontrol kelonjongan (oval) dan kemiringan sudut kendang secara langsung di HP.

## What Changes

- Menambahkan medan opsional `rotation: Float = 0f` (rentang `0.0`–`360.0` derajat) pada `DrumData` di `kit.json` (kompatibel mundur dengan kit lama tanpa `rotation`) beserta aturan validasinya di `KitValidator`.
- Memperbarui mesin hit-testing (`HitTester`) agar memutar balik titik sentuh sebesar `-drum.rotation` terhadap titik pusat kendang sebelum mengevaluasi batas elips `(rx, ry)` dan pembagian zona di dalamnya (`single`, `split`, maupun `bass`).
- Memperbarui pipeline render papan (`KendangBoardView`, `ZoneHighlightDrawer`, dan `ZoneGuideOverlayProvider`) agar memutar kanvas sebesar `drum.rotation` pada pusat kendang saat menggambar badan 3D, kulit/rim Canvas atau gambar PNG, garis batas zona, serta highlight ketukan.
- Memperbarui kotak **POSISI & UKURAN KENDANG** di Mode Developer (`DevAuthoringActivity` & `DevKitAuthoringSession`) dengan:
  - Slider **Ukuran** (`0.08`–`0.50`) yang mengatur jari-jari utama $S = \max(rx, ry)$ sembari menjaga rasio kelonjongan.
  - Slider **Kelonjongan** (`-100%` s/d `+100%`, titik tengah `0%` = bulat sempurna, negatif = lonjong mendatar, positif = lonjong tegak, dengan ketebalan minimum 10% dari $S$ pada ujung ekstrem agar nilai radius tetap valid).
  - Slider **Kemiringan / Rotasi** (`0°`–`360°`) yang mengatur rotasi orientasi kendang (`drum.rotation`).
  - Tombol **`↺ Reset Bentuk`** untuk mengembalikan kelonjongan ke `0%` (bulat sempurna) dan kemiringan kendang ke `0°`.
- Mempertahankan kotak **PARAMETER PUTARAN SUDUT** (`divider.angle`) pada kendang non-bass dengan $\ge 2$ zona secara terpisah agar garis sekat zona di dalam elips tetap dapat diputar secara independen relatif terhadap orientasi kendang.
- Mempertahankan perilaku gestur cubit (*pinch-to-scale*) yang mengalikan `rx` dan `ry` dengan faktor skala yang sama sehingga rasio kelonjongan tetap terjaga.

## Capabilities

### New Capabilities

*(Tidak ada capability baru; seluruh perubahan memperluas capability yang sudah ada).*

### Modified Capabilities

- `kit-data`: Menambahkan medan opsional `rotation` (`0.0`–`360.0` derajat, default `0.0`) pada skema `DrumData` di `kit.json` beserta validasi rentang nilainya.
- `kendang-board`: Memperbarui hit-testing dan rendering (badan 3D, kulit/rim Canvas, gambar PNG, garis panduan zona, dan highlight ketukan) agar mengikuti elips `(rx, ry)` dan rotasi orientasi kendang (`drum.rotation`).
- `kit-authoring`: Mengganti slider ukuran tunggal di Mode Developer dengan slider **Ukuran**, **Kelonjongan** (`-100%` s/d `+100%`), **Kemiringan / Rotasi** (`0°`–`360°`), dan tombol **`↺ Reset Bentuk`**, sambil mempertahankan kotak putaran sudut sekat zona (`divider.angle`) secara terpisah.

## Impact

- **`:kit-core`**:
  - `KitModels.kt`: Tambah `val rotation: Float = 0f` pada `DrumData`.
  - `KitValidator.kt`: Validasi `drum.rotation in 0.0f..360.0f`.
  - `HitTester.kt`: Transformasi rotasi balik `-drum.rotation` pada koordinat sentuh lokal kendang.
- **`:app` (main)**:
  - `KendangBoardView.kt`, `ZoneHighlightDrawer.kt`: Rotasi kanvas `drum.rotation` saat menggambar kendang, panduan zona, dan highlight ketukan.
- **`:app` (debug / Mode Developer)**:
  - `DevKitAuthoringSession.kt`: Tambah fungsi `updateDrumRotation` dan helper konversi Ukuran + Kelonjongan $\leftrightarrow (rx, ry)$.
  - `DevAuthoringActivity.kt`: Tambah slider Ukuran, Kelonjongan, Kemiringan/Rotasi, dan tombol Reset Bentuk pada kotak Posisi & Ukuran Kendang.
  - `ZoneGuideOverlayProvider.kt`: Rotasi kanvas `drum.rotation` saat menggambar overlay garis panduan.
