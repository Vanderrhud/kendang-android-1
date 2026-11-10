# Design

## Context

Lihat `proposal.md` (Why) untuk motivasi. Kondisi arsitektur saat ini yang membentuk pendekatan teknis:

- `DrumData` di `:kit-core` saat ini memiliki `image: String? = null` yang menyimpan path relatif `images/<drumId>.png` untuk kulit kendang.
- `KendangBoardView.onDraw` saat ini memisahkan loop render menjadi dua tahap global: (1) seluruh badan 3D prosedural (`drawDrumBody3D`) untuk kendang tanpa gambar kulit, lalu (2) seluruh permukaan kulit kendang. Akibatnya, kendang yang memiliki PNG kulit kehilangan representasi body sama sekali, dan apabila ada body per-kendang yang ditumpuk lintas Z-order, pemisahan loop global akan menghalangi body kendang depan menutupi kendang belakang secara alami.
- `KitImageLoader` saat ini memuat `Map<String, Bitmap>` yang dipetakan hanya dari `drum.id` untuk gambar kulit.
- `:kit-core` adalah modul Kotlin/JVM murni tanpa dependensi `android.*`, sedangkan dekode `Bitmap` dan manipulasi UI Mode Developer berada di `:app`.
- Lingkungan VM lokal tidak memiliki JDK/Android SDK; seluruh kompilasi dan pengujian dijalankan terpusat melalui GitHub Actions CI.

## Goals / Non-Goals

**Goals:**
- Menyediakan model data modular `DrumBodyData` yang opsional (`body: DrumBodyData? = null`) dan sepenuhnya kompatibel mundur dengan `kit.json` lama.
- Menerapkan transformasi hierarkis *parent-child* dan urutan render **Layer 2 (Per-Kendang Berurutan)** di `KendangBoardView` tanpa membebani performa frame rate maupun latensi multi-touch.
- Menyediakan alur pemasangan PNG body serta kontrol geometri lengkap (termasuk fitur *Auto-Dock* `⚡ Rapatkan ke Kulit`) di Mode Developer (`debug`).

**Non-Goals:**
- Tidak menerapkan distorsi mesh perspektif 3D / homografi 4-titik di Canvas; efek perspektif visual dan cincin batas digambar di dalam aset PNG persegi transparan dan dikontrol melalui dimensi 2D bebas (`width`, `height`, `offsetX`, `offsetY`, `rotation`).
- Tidak menjadikan area gambar body sebagai area sentuh aktif (hit-testing tetap eksklusif pada elips permukaan kulit kendang).

## Decisions

### D1. Struktur Modular `DrumBodyData` dan Konvensi Path `images/<drumId>-body.png`
Pada `KitModels.kt`, ditambahkan:
```kotlin
@Serializable
data class DrumBodyData(
    val image: String,
    val width: Float,
    val height: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f
)
```
dan pada `DrumData`:
```kotlin
@EncodeDefault val body: DrumBodyData? = null
```
- **Alasan**: Mengelompokkan seluruh properti body di dalam `DrumBodyData?` menjaga `DrumData` tetap bersih bila kendang tidak memakai body (`body == null`).
- **Konvensi Nama File**: Path `body.image` wajib bernilai `"images/${drum.id}-body.png"`. Konvensi deterministik berbasis `drum.id` ini sejajar dengan `images/${drum.id}.png` milik kulit, memudahkan validasi di `KitValidator`, mencegah tabrakan nama di folder `images/`, dan memudahkan penghapusan/rename otomatis saat siklus hidup kendang berubah.
- **Rentang Validasi di `KitValidator`**:
  - `body.image == "images/${drum.id}-body.png"` (tanpa path absolut atau `..`).
  - `body.width > 0.0f && body.width <= 5.0f` dan `body.height > 0.0f && body.height <= 5.0f`.
  - `body.offsetX in -3.0f..3.0f` dan `body.offsetY in -3.0f..3.0f`.
  - `body.rotation in 0.0f..360.0f`.

### D2. Urutan Render Layer 2 (Per-Kendang Berurutan) dan Transformasi Parent-Child
Di `KendangBoardView.onDraw`, dua loop terpisah (semua body lalu semua kulit) digabungkan menjadi **satu loop per-kendang** berdasarkan urutan Z-order dari belakang ke depan (`for (drum in currentKit.drums.asReversed())`):
1. **Tahap Body Kendang**:
   - Hitung pusat kendang `(cx, cy)` dan rotasi induk `drum.rotation`.
   - Bila `drum.body != null` dan bitmap body tersedia di `drumBodyBitmaps[drum.id]`:
     - Terapkan transformasi *parent*: `canvas.save()` lalu `canvas.rotate(drum.rotation, cx, cy)`.
     - Di dalam kerangka koordinat kendang induk tersebut, hitung pusat lokal body:
       - `bodyCx = cx + mapper.toPixelRadiusX(body.offsetX)`
       - `bodyCy = cy + mapper.toPixelRadiusY(body.offsetY)`
       - `halfW = mapper.toPixelRadiusX(body.width / 2f)`
       - `halfH = mapper.toPixelRadiusY(body.height / 2f)`
     - Bila `body.rotation != 0f`, putar kanvas sebesar `body.rotation` terhadap `(bodyCx, bodyCy)`.
     - Gambar bitmap body ke `RectF(bodyCx - halfW, bodyCy - halfH, bodyCx + halfW, bodyCy + halfH)`.
     - `canvas.restore()`.
   - Bila kendang tidak memiliki bitmap body **dan** tidak memiliki bitmap kulit (`drumBitmaps[drum.id] == null`), gambar badan prosedural lama `drawDrumBody3D(canvas, cx, cy, baseRx, baseRy)` (dengan rotasi `drum.rotation`).
2. **Tahap Kulit Kendang**:
   - Gambar permukaan kulit kendang (bitmap PNG kulit atau oval Canvas bawaan) beserta efek denyut pukul (`pulseScale`) dan bingkai seleksi tepat di atas body kendang tersebut.

- **Alasan**:
  - Kulit kendang selalu digambar setelah body miliknya sendiri, sehingga bagian bawah kulit (cincin) selalu menutup bagian atas body secara rapi.
  - Kendang dengan prioritas Z-order lebih tinggi (di depan) menggambar body dan kulitnya setelah kendang belakang selesai digambar, menghasilkan tumpukan kedalaman 3D yang konsisten.
  - Karena body digambar di dalam blok `canvas.rotate(drum.rotation, cx, cy)` dengan acuan `(cx, cy)`, menggeser atau memutar kendang otomatis membawa body-nya (*parent-child*) tanpa perhitungan trigonometri manual yang rumit.
  - Body tidak dikalikan `pulseScale` agar saat kulit kendang berdenyut ketika dipukul, tabung kayu body tetap kokoh diam di tempatnya.

### D3. Manajemen Bitmap Kulit dan Body di `KitImageLoader`
- `KitImageLoader` menambahkan fungsi `loadBodyImages(kit: KitData, imageSource: KitImageSource): Map<String, Bitmap>` yang membaca `drum.body?.image` untuk setiap kendang dan mengembalikan pemetaan `drum.id -> Bitmap`.
- `KendangBoardView` memiliki properti `var drumBodyBitmaps: Map<String, Bitmap> = emptyMap()` berdampingan dengan `drumBitmaps`.
- Saat kit berganti atau dimuat ulang di `MainActivity` maupun `DevAuthoringActivity`, `kitImageLoader.releaseBitmaps(currentDrumBodyBitmaps)` dipanggil untuk mencegah kebocoran memori.

### D4. Matematika "⚡ Rapatkan ke Kulit" (*Auto-Dock*) dan Nilai Awal Pemasangan Body
Saat pengguna baru saja memasang PNG body pada suatu kendang, atau ketika pengguna menekan tombol **`⚡ Rapatkan ke Kulit`** / **`↺ Reset Body`**:
- Lebar body diselaraskan dengan diameter horizontal kulit kendang: `width = (drum.radius.rx * 2f).coerceIn(0.08f, 1.00f)`.
- Tinggi/panjang body saat pemasangan awal atau reset disetel ke `height = (drum.radius.rx * 2f).coerceIn(0.08f, 1.00f)` (sedangkan tombol `⚡ Rapatkan ke Kulit` mempertahankan `height` pilihan pengguna saat ini agar panjang tabung yang sudah diatur tidak hilang, atau menggunakan `2 * rx` bila belum ada).
- Offset horizontal disetel ke `offsetX = 0f` (tepat segaris tengah dengan kendang).
- Offset vertikal disetel ke `offsetY = height / 2f`. Dengan `offsetY = height / 2f`, tepi atas kotak gambar body (`bodyCy - height/2`) berada tepat di garis tengah horizontal ekuator kulit (`cy`), tempat lebar elips kulit mencapai titik terlebarnya (`2 * rx`). Bagian bawah elips kulit (`cy` hingga `cy + ry`) otomatis menindih bagian atas gambar body, menciptakan sambungan cincin melengkung yang menyatu sempurna tanpa celah di sisi kiri-kanan.
- Rotasi tambahan body disetel ke `rotation = 0f`.

### D5. Pemasangan PNG Body & Siklus Hidup File di Mode Developer
- `DevImageInstaller` diperluas dengan `installBodyPng(drumId, inputStream, devKitDir)` dan `clearBodyImage(drumId, devKitDir)` yang berbagi pipa validasi dan downsampling yang sama dengan PNG kulit (PNG valid, rasio persegi 1:1 `width == height`, ukuran file `<= 2 MB`, downsample `<= 1024 px`), menyimpan ke `images/<drumId>-body.png`, dan menginisialisasi objek `DrumBodyData` yang rapat ke kulit bila sebelumnya `body == null` (atau mempertahankan geometri `DrumBodyData` yang sudah ada bila hanya mengganti gambar body).
- `DevKitAuthoringSession` memperbarui `deleteDrum`, `renameDrumId`, `copyWorkingKitToInternalDir`, serta `KitPackageReader` dan `KitPackageWriter` agar `images/<drumId>-body.png` selalu ikut dikelola, divalidasi, dan dikemas tanpa meninggalkan file PNG yatim.

## Risks / Trade-offs

- **[Penggunaan RAM bertambah karena 2 bitmap per kendang]** → Dengan batas maksimum 1024x1024 px (`inSampleSize`) dan pelepasan bitmap lama via `releaseBitmaps` setiap pergantian kit, tambahan memori hanya ~4 MB per body kendang yang aktif.
- **[Sambungan visual antara kulit dan body terlihat kaku bila diatur manual dari nol]** → Dimitigasi dengan penempatan otomatis saat pemasangan awal serta tombol **`⚡ Rapatkan ke Kulit`** yang langsung mengunci tepi atas body ke garis ekuator kulit kendang dengan lebar `2 * rx`.
- **[Kode Mode Developer bocor ke APK Release]** → Seluruh UI kontrol body dan `DevImageInstaller` berada di source set `debug`; hanya model data, pemuat gambar, dan rendering `KendangBoardView` yang berada di `main`.

## Migration Plan

- Tidak diperlukan migrasi untuk kit yang sudah ada: `body: DrumBodyData? = null` bersifat opsional dengan nilai default `null`.
- Kit bawaan (`new-metro`) dan kit hasil simpanan sebelumnya tetap lolos `KitValidator` dan tampil identik tanpa perubahan.
