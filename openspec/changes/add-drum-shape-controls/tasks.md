# Tasks

> Build dan test tidak dapat dijalankan di VM lokal; verifikasi otomatis dilakukan oleh GitHub Actions CI (unit test `:kit-core` dan `:app` serta build APK). Verifikasi visual/interaksi di HP dilakukan manual oleh pemilik produk.

## 1. Model, validasi, dan hit-testing rotasi kendang di `:kit-core`

- [x] 1.1 Tambah `val rotation: Float = 0f` pada `DrumData` di `KitModels.kt` dan pastikan kompatibilitas mundur parser untuk kit lama tanpa `rotation`; verifikasi dengan unit test di `KitJsonParserTest` (kit lama default `0.0` + round-trip `rotation` kustom).
- [x] 1.2 Tambah validasi rentang `drum.rotation in 0.0f..360.0f` di `KitValidator.kt`; verifikasi dengan unit test di `KitValidatorTest` untuk nilai valid (`0.0`, `45.0`, `360.0`) dan nilai di luar batas (`-1.0`, `360.1`).
- [x] 1.3 Perbarui `HitTester.findHit` agar memutar balik vektor sentuh `(xu - cxu, yu - cyu)` sebesar `-drum.rotation` sebelum evaluasi batas elips `(rx, ry)` serta pembagian zona internal (`bass`, `split`, `single`), termasuk saat dikombinasikan dengan `divider.angle`; verifikasi dengan unit test di `HitTesterTest` untuk kendang oval miring dan kombinasi `rotation` + `divider.angle`.

## 2. Rendering rotasi orientasi kendang di `:app` (main)

- [x] 2.1 Perbarui `KendangBoardView.onDraw` dan `drawInteractiveZoneGuidesAndLabels` agar menerapkan `canvas.rotate(drum.rotation, cx, cy)` saat menggambar badan 3D, kulit/rim Canvas atau gambar PNG, elips seleksi, serta garis panduan zona (dengan teks label tetap tegak), dan perbarui `KendangBoardView.findDrumAtPixel` (deteksi sentuh mode edit) agar ikut memperhitungkan `drum.rotation`; verifikasi dengan unit test di `KendangBoardViewTest`.
- [x] 2.2 Perbarui `ZoneHighlightDrawer.drawHighlight` agar menerapkan `canvas.rotate(drum.rotation, cx, cy)` saat menggambar highlight ketukan untuk model `single`, `split`, dan `bass`; verifikasi kompilasi dan unit test `:app` lulus di CI.

## 3. Kontrol Ukuran, Kelonjongan, dan Kemiringan di Mode Developer (`debug`)

- [x] 3.1 Tambah helper konversi dua arah `(Ukuran S, Kelonjongan E) <-> Radius(rx, ry)`, fungsi `updateDrumRotation(drumId, rotation)`, dan fungsi `resetDrumShape(drumId)` di `DevKitAuthoringSession.kt`; verifikasi dengan unit test di `DevKitAuthoringSessionTest` untuk kelonjongan negatif/positif/nol, konsistensi saat Ukuran diubah, rotasi `0..360`, dan reset bentuk.
- [x] 3.2 Perbarui kotak **POSISI & UKURAN KENDANG** di `DevAuthoringActivity.kt` dengan mengganti slider tunggal `Radius/Ukuran` menjadi slider **Ukuran (`0.08`–`0.50`)**, **Kelonjongan (`-100%` s/d `+100%`)**, **Kemiringan / Rotasi (`0°`–`360°`)**, dan tombol **`↺ Reset Bentuk`** sambil mempertahankan kotak **PARAMETER PUTARAN SUDUT** (`divider.angle`) secara terpisah, serta perbarui `ZoneGuideOverlayProvider.kt` agar memutar kanvas sesuai `drum.rotation`; verifikasi kompilasi debug dan testDebug lulus di CI.

## 4. Verifikasi menyeluruh

- [x] 4.1 Jalankan CI di GitHub Actions: seluruh unit test `:kit-core` dan `:app` lulus, verifikasi APK release bebas kode Mode Developer lulus, dan artefak APK debug berhasil diterbitkan.
- [ ] 4.2 Uji manual di HP fisik: ubah Ukuran, Kelonjongan (`-100%` s/d `+100%`), dan Kemiringan/Rotasi (`0°`–`360°`) pada kendang dengan gambar PNG maupun tanpa gambar, pastikan visual dan area ketuk (hit-testing) tetap presisi untuk efek perspektif POV, uji tombol `↺ Reset Bentuk`, dan pastikan kotak Parameter Putaran Sudut (`divider.angle`) tetap bekerja independen.
