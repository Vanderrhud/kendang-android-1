# Proposal

## Why

Saat ini aset gambar PNG per kendang hanya mencakup permukaan kulit kendang (`drum.image`), sementara badan/tabung kendang (body) otomatis disembunyikan begitu PNG kulit dipasang. Agar pembuat kit dapat membangun tampilan perspektif POV 2.5D/3D yang utuh dan tampak menyatu secara organik dengan cincin batas kulit kendang, setiap kendang memerlukan dukungan gambar PNG body modular tersendiri dengan hubungan gerak hierarkis (*parent-child*) dan kontrol dimensi/posisi mandiri di Mode Developer.

## What Changes

- **Objek Modular `DrumBodyData` di `kit.json` (`:kit-core`)**:
  - Menambahkan `data class DrumBodyData` opsional pada `DrumData` (`val body: DrumBodyData? = null`, kompatibel mundur penuh dengan kit lama):
    - `image: String` — konvensi path tetap `images/<drumId>-body.png`.
    - `width: Float` dan `height: Float` — dimensi bebas kotak gambar body dalam satuan kanvas (`> 0.0` dan `<= 5.0`).
    - `offsetX: Float = 0f` dan `offsetY: Float = 0f` — pergeseran relatif terhadap titik pusat kendang (`-3.0..3.0`).
    - `rotation: Float = 0f` — sudut kemiringan tambahan body relatif terhadap rotasi kendang induk (`0.0..360.0`).
  - Memperbarui `KitValidator`, `KitPackageReader`, dan `KitPackageWriter` untuk memvalidasi dan mengemas file `images/<drumId>-body.png` yang dirujuk `drum.body?.image` (tanpa menyertakan PNG yatim).
- **Transformasi Parent-Child & Urutan Render Per-Kendang di `:app` (main)**:
  - Memperbarui `KitImageLoader` agar memuat bitmap kulit maupun bitmap body per kendang.
  - Memperbarui `KendangBoardView.onDraw` menggunakan urutan **Layer 2 (Per-Kendang Berurutan)** dari belakang ke depan (`drums.asReversed()`): untuk setiap kendang, gambar body-nya terlebih dahulu di belakang, lalu gambar kulit kendang di atasnya. Dengan demikian kendang di lapisan depan secara alami dapat menutupi kendang di lapisan belakang, sementara kulit suatu kendang selalu berada di atas body miliknya sendiri.
  - Menerapkan transformasi **Parent-Child**: gambar PNG body ikut bergeser bersama `drum.center` dan ikut berputar bersama `drum.rotation`, ditambah penyesuaian lokal `offsetX`, `offsetY`, `width`, `height`, dan `body.rotation`. Body murni kosmetik visual dan tidak memengaruhi hit-testing zona maupun efek denyut kulit.
- **Kontrol Gambar & Parameter Body Kendang di Mode Developer (`debug`)**:
  - Memperbarui `DevImageInstaller` dan `DevKitAuthoringSession` untuk memasang/mengosongkan PNG body (`images/<drumId>-body.png`, validasi PNG persegi 1:1 maks 2 MB, downsample maks 1024 px), memperbarui parameter geometri body, serta menjaga siklus hidup file saat hapus/rename/simpan/ekspor/impor.
  - Menambahkan tombol **Pilih PNG Body** / **Kosongkan Body** serta kotak **PARAMETER BODY KENDANG** di `DevAuthoringActivity` (aktif saat kendang memiliki PNG body) yang memuat:
    - Slider **Lebar Body** (`0.08`–`1.00`)
    - Slider **Panjang / Tinggi Body** (`0.08`–`1.00`)
    - Slider **Geser X (Offset X)** (`-0.50`–`+0.50`)
    - Slider **Geser Y (Offset Y)** (`-0.50`–`+0.50`)
    - Slider **Kemiringan Body** (`0°`–`360°`)
    - Tombol **`⚡ Rapatkan ke Kulit`** (*Auto-Dock*): menyelaraskan lebar body dengan diameter horizontal kulit (`2 * rx`) dan menempatkan tepi atas body tepat di garis tengah ekuator elips kulit (`offsetX = 0`, `offsetY = height / 2`, `rotation = 0`) sehingga cincin bawah kulit langsung menutup sambungan atas body secara mulus.
    - Tombol **`↺ Reset Body`**: mengembalikan parameter body ke posisi standar.

## Capabilities

### New Capabilities
<!-- Tidak ada capability baru; seluruh perubahan memperluas capability yang sudah ada. -->

### Modified Capabilities
- `kit-data`: Menambahkan skema opsional `body: DrumBodyData?` pada `DrumData`, aturan validasi `DrumBodyData` (`image`, `width`, `height`, `offsetX`, `offsetY`, `rotation`), serta dukungan pembacaan/penulisan `images/<drumId>-body.png` di paket `.vrkendang`.
- `kendang-board`: Mengubah urutan render kendang menjadi per-kendang berurutan (body lalu kulit per kendang sesuai Z-order) dan menambahkan render gambar PNG body dengan transformasi hierarkis *parent-child*.
- `kit-authoring`: Menambahkan fitur pasang/kosongkan PNG body, kotak kontrol Parameter Body Kendang beserta tombol `⚡ Rapatkan ke Kulit` dan `↺ Reset Body` di Mode Developer, serta manajemen siklus hidup file `images/<drumId>-body.png`.

## Impact

- **`:kit-core`**: `KitModels.kt`, `KitValidator.kt`, `KitPackageReader.kt`, `KitPackageWriter.kt`, serta unit test `KitJsonParserTest`, `KitValidatorTest`, dan `KitPackageReaderTest`.
- **`:app` (main)**: `KitImageLoader.kt`, `KendangBoardView.kt`, `MainActivity.kt`, serta unit test `KitImageLoaderTest` dan `KendangBoardViewTest`.
- **`:app` (debug)**: `DevImageInstaller.kt`, `DevKitAuthoringSession.kt`, `DevAuthoringActivity.kt`, serta unit test `DevImageInstallerTest` dan `DevKitAuthoringSessionTest`.
- **Kompatibilitas**: Tidak ada perubahan *breaking*; seluruh kit lama tanpa medan `body` tetap valid 100% dan dirender identik seperti sebelumnya.
