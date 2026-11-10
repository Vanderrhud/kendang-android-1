# Tasks

> Build dan test tidak dapat dijalankan di VM lokal; verifikasi otomatis dilakukan oleh GitHub Actions CI (unit test `:kit-core` dan `:app` serta build APK). Verifikasi visual/interaksi di HP dilakukan manual oleh pemilik produk.

## 1. Model, validasi, dan paket `.vrkendang` untuk body kendang di `:kit-core`

- [x] 1.1 Tambah `data class DrumBodyData(image, width, height, offsetX = 0f, offsetY = 0f, rotation = 0f)` dan properti opsional `@EncodeDefault val body: DrumBodyData? = null` pada `DrumData` di `KitModels.kt`; verifikasi dengan unit test di `KitJsonParserTest` (kit lama tanpa `body` default `null` + round-trip `body` kustom).
- [x] 1.2 Tambah validasi `drum.body` di `KitValidator.kt` (`body.image == "images/${drum.id}-body.png"` tanpa path absolut/`..`, `width` & `height` di `> 0.0..5.0`, `offsetX` & `offsetY` di `-3.0..3.0`, serta `rotation` di `0.0..360.0`); verifikasi dengan unit test di `KitValidatorTest` untuk konfigurasi body valid dan berbagai pelanggaran batas/path.
- [x] 1.3 Perbarui `KitPackageReader.kt` dan `KitPackageWriter.kt` agar memvalidasi keberadaan/format PNG `drum.body?.image` (`images/<drumId>-body.png`) saat membaca paket dan mengemasnya saat menulis paket tanpa menyertakan file PNG yatim; verifikasi dengan unit test di `KitPackageReaderTest`.

## 2. Pemuatan bitmap dan rendering Layer 2 (Parent-Child) di `:app` (main)

- [x] 2.1 Tambah `loadBodyImages(kit, imageSource)` di `KitImageLoader.kt` serta properti `drumBodyBitmaps` di `KendangBoardView.kt` dan pemuatannya di `MainActivity.kt`; verifikasi dengan unit test di `KitImageLoaderTest` untuk pemuatan bitmap body, penanganan gagal dekode/baca secara aman, dan pelepasan bitmap.
- [x] 2.2 Perbarui `KendangBoardView.onDraw` menjadi urutan render **Layer 2 (Per-Kendang Berurutan)** dari belakang ke depan (`drums.asReversed()`): render gambar PNG body dengan transformasi *parent-child* (`drum.center`, `drum.rotation` + `body.offsetX`, `body.offsetY`, `body.width`, `body.height`, `body.rotation`) atau badan 3D prosedural bila tanpa gambar kulit & body, lalu render kulit kendang tepat di atasnya tanpa memengaruhi hit-testing; verifikasi dengan unit test di `KendangBoardViewTest`.

## 3. Pemasangan PNG body dan kontrol Parameter Body di Mode Developer (`debug`)

- [x] 3.1 Tambah `installBodyPng(drumId, inputStream, devKitDir)` dan `clearBodyImage(drumId, devKitDir)` di `DevImageInstaller.kt` (validasi PNG persegi 1:1 <= 2 MB, downsample <= 1024 px, simpan atomik ke `images/<drumId>-body.png`, inisialisasi `DrumBodyData` rapat ke kulit bila belum ada); verifikasi dengan unit test di `DevImageInstallerTest`.
- [x] 3.2 Tambah fungsi `installDrumBodyImage`, `clearDrumBodyImage`, `updateDrumBody`, `dockDrumBodyToSkin`, dan `resetDrumBody` di `DevKitAuthoringSession.kt`, serta perbarui `deleteDrum`, `renameDrumId`, dan `copyWorkingKitToInternalDir` agar menyertakan `images/<drumId>-body.png`; verifikasi dengan unit test di `DevKitAuthoringSessionTest`.
- [x] 3.3 Perbarui `DevAuthoringActivity.kt` untuk menampilkan tombol **Pilih PNG Body** / **Kosongkan Body** pada bagian Gambar Kendang serta kotak **PARAMETER BODY KENDANG** (slider Lebar Body `0.08`–`1.00`, Panjang/Tinggi Body `0.08`–`1.00`, Geser X `-0.50`–`+0.50`, Geser Y `-0.50`–`+0.50`, Kemiringan Body `0°`–`360°`, tombol **`⚡ Rapatkan ke Kulit`**, dan tombol **`↺ Reset Body`**) beserta pemuatan ulang `drumBodyBitmaps` pada papan pratinjau; verifikasi kompilasi debug dan `testDebugUnitTest` lulus di CI.

## 4. Verifikasi menyeluruh

- [ ] 4.1 Jalankan CI di GitHub Actions: seluruh unit test `:kit-core` dan `:app` lulus, verifikasi APK release bebas kode Mode Developer lulus, dan artefak APK debug berhasil diterbitkan.
- [ ] 4.2 Uji manual di HP fisik: pasang gambar PNG kulit dan PNG body pada kendang di Mode Developer, uji tombol `⚡ Rapatkan ke Kulit` dan `↺ Reset Body`, sesuaikan slider Lebar/Tinggi/Geser/Kemiringan Body, geser dan putar kendang induk untuk memastikan body mengikuti secara *parent-child*, serta pastikan urutan tumpukan antar kendang dan area ketuk (hit-testing) berfungsi akurat.
