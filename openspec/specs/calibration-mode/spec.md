# calibration-mode Specification

## Purpose
Memungkinkan pemain untuk mengatur posisi dan ukuran kendang langsung di layar perangkat dan menyimpan hasilnya sebagai layout override di penyimpanan internal aplikasi, tanpa memerlukan build ulang dan tanpa mengubah data suara kit.

## Requirements

### Requirement: Halaman Sesuaikan dapat diaktifkan dari menu utama
Mode kalibrasi tata letak pemain (`Halaman Sesuaikan`) SHALL dapat diakses melalui tombol `☰ Menu` → `Sesuaikan` di header utama yang tersedia di semua build. Saat Halaman Sesuaikan aktif, perubahan posisi dan ukuran hanya berlaku untuk kit yang sedang aktif.

#### Scenario: Pemain membuka Halaman Sesuaikan dari menu
- **WHEN** pemain membuka `☰ Menu` di pojok kiri atas dan memilih `Sesuaikan`
- **THEN** layar beralih ke Halaman Sesuaikan layar penuh dengan header `✕ Batal`, petunjuk singkat di tengah, serta tombol `↺ Reset` dan `✓ Simpan` di kanan

### Requirement: Pemilihan kendang di mode kalibrasi
Pemain SHALL dapat memilih kendang dengan mengetuknya; kendang terpilih SHALL diberi tanda visual pembeda. Menggeser kendang SHALL secara otomatis memilih kendang tersebut. Bila sentuhan mengenai area tumpang tindih antara dua kendang, kendang yang berada lebih awal di array `drums` pada `kit.json` yang terpilih. Sentuhan di luar semua kendang SHALL tidak mengubah kendang yang sedang dipilih.

#### Scenario: Mengetuk kendang memilih kendang dengan indikator visual
- **WHEN** pemain mengetuk suatu kendang di mode kalibrasi
- **THEN** kendang tersebut menjadi kendang terpilih dan menampilkan highlight batas pilihan

#### Scenario: Sentuhan di area tumpang tindih memilih kendang prioritas
- **WHEN** sentuhan terjadi pada titik tumpang tindih antara Kecil 1 dan Sedang
- **THEN** Kecil 1 terpilih karena memiliki indeks lebih awal di array `drums`

#### Scenario: Sentuhan di luar seluruh kendang mempertahankan pilihan
- **WHEN** pemain menyentuh area kosong kanvas di luar kendang mana pun
- **THEN** kendang yang sedang terpilih sebelumnya tetap terpilih

### Requirement: Geser kendang dengan satu jari dan batas retensi aman kanvas
Pemain SHALL dapat menggeser kendang terpilih menggunakan satu jari melebihi batas kanvas. Jari tambahan selama penggeseran SHALL diabaikan. Posisi kendang SHALL dijepit secara real-time saat jari bergerak dalam satuan kanvas dengan batas retensi aman (safety retention boundary) sehingga setidaknya 20% radius kendang tetap berada di dalam kanvas agar kendang tidak hilang total dari layar.

#### Scenario: Kendang dapat digeser melebihi kanvas dan dijepit pada batas retensi aman
- **WHEN** pemain menggeser kendang terpilih melewati tepi kanvas
- **THEN** kendang mengikuti posisi jari melebihi batas tepi kanvas dan dijepit pada batas retensi aman sehingga sebagian lingkaran kendang tetap menyentuh kanvas untuk disentuh atau ditarik kembali

#### Scenario: Jari kedua selama proses geser diabaikan
- **WHEN** pemain meletakkan jari kedua di layar saat jari pertama sedang menggeser kendang
- **THEN** jari kedua diabaikan dan gerakan kendang tetap dikendalikan oleh jari pertama

### Requirement: Ubah ukuran kendang dengan gestur cubit dua jari (pinch-to-scale)
Sistem SHALL memungkinkan pemain mengubah ukuran kendang terpilih secara langsung menggunakan gestur cubit dua jari (`ScaleGestureDetector`) di rentang skala `0.5x–5.0x` (hingga 5 kali lipat) tanpa menampilkan bilah bawah atau slider yang menutupi papan. Batas skala maksimum SHALL mengizinkan perbesaran hingga `5.0x` dan selalu lolos validasi `KitValidator` (merujuk bagian "Layout override pemain di Halaman Sesuaikan" di `design.md`).

#### Scenario: Gestur cubit dua jari mengubah ukuran kendang secara proporsional hingga 5x
- **WHEN** pemain mencubit atau merentangkan dua jari pada papan saat suatu kendang terpilih di Halaman Sesuaikan
- **THEN** nilai `rx` dan `ry` kendang tersebut berubah proporsional secara real-time di layar dalam rentang `0.5x–5.0x`

#### Scenario: Skala maksimum mencapai 5x lipat
- **WHEN** pemain merentangkan dua jari secara maksimal pada kendang terpilih
- **THEN** batas skala dapat mencapai hingga `5.0x` dari ukuran radius dasar bawaan kit

### Requirement: Tanpa suara di mode kalibrasi
Seluruh interaksi sentuhan di mode kalibrasi SHALL tidak membunyikan suara audio dan SHALL tidak mengirimkan event ke antrean audio engine (sesuai spesifikasi `kendang-board`).

#### Scenario: Interaksi di mode kalibrasi hening
- **WHEN** pemain mengetuk atau menggeser kendang di mode kalibrasi
- **THEN** tidak ada suara yang diputar dan antrean trigger audio tidak dipicu

### Requirement: Tampilkan panduan batas zona dari geometri satu sumber kebenaran
Mode kalibrasi SHALL merender garis panduan batas zona akustik transparan di atas kulit kendang menggunakan rumus dan geometri hit-testing yang sama persis dengan yang digunakan untuk pemicu suara dan visual feedback (termasuk busur pembatas `C_div` pada Bass, 4 bilah bulan sabit Bass, dan garis horizontal split pada Sedang dan Kecil 2).

#### Scenario: Garis panduan batas zona tampil presisi
- **WHEN** mode kalibrasi aktif
- **THEN** garis panduan zona akustik (termasuk busur Bass dan garis split) tergambar transparan di atas kulit kendang mengikuti posisi dan skala kendang secara real-time

### Requirement: Simpan, batal, dan konfirmasi perubahan belum disimpan
Mode kalibrasi SHALL menyediakan tombol "Simpan" dan "Batal". Tombol "Simpan" menulis override untuk `kitId` yang sedang aktif. Tombol "Batal" membuang seluruh perubahan dan mengembalikan tata letak semula. Jika pemain mencoba keluar dari mode kalibrasi dengan perubahan belum disimpan, sistem SHALL menampilkan dialog konfirmasi simpan atau buang. Perubahan yang belum disimpan SHALL tidak ditulis ke disk.

#### Scenario: Tombol Simpan menerapkan dan menulis perubahan
- **WHEN** pemain menekan tombol "Simpan"
- **THEN** tata letak baru ditulis ke file override milik kit aktif dan mode kalibrasi ditutup

#### Scenario: Tombol Batal membuang perubahan
- **WHEN** pemain menekan tombol "Batal"
- **THEN** seluruh perubahan posisi dan ukuran dibatalkan, tata letak kembali ke kondisi sebelum diedit, dan tidak ada file yang ditulis ke disk

#### Scenario: Menekan tombol back dengan perubahan memicu dialog konfirmasi
- **WHEN** pemain menekan tombol back sistem saat ada perubahan posisi/ukuran yang belum disimpan
- **THEN** aplikasi menampilkan dialog konfirmasi untuk menyimpan atau membuang perubahan sebelum keluar

### Requirement: Penulisan atomik dan penanganan kegagalan simpan
Penulisan layout override ke penyimpanan internal (`files/layouts/<kitId>.json`) SHALL dilakukan secara atomik (menulis ke file sementara `.tmp` terlebih dahulu sebelum mengganti nama file). Bila penulisan gagal (misalnya ruang penyimpanan penuh), sistem SHALL menampilkan pesan galat singkat, pemain tetap berada di mode kalibrasi dengan perubahannya, file override sebelumnya tidak rusak, dan aplikasi SHALL tidak crash.

#### Scenario: Penulisan override berhasil secara atomik
- **WHEN** pemain menyimpan tata letak
- **THEN** file sementara ditulis lengkap lalu diganti namanya menjadi `files/layouts/<kitId>.json` secara atomik

#### Scenario: Kegagalan penyimpanan menampilkan galat tanpa merusak file lama
- **WHEN** penulisan file gagal karena kendala penyimpanan
- **THEN** sistem menampilkan pesan galat singkat, file override lama tetap utuh, posisi edit pemain tidak hilang, dan aplikasi tidak crash

### Requirement: Reset ke bawaan dengan konfirmasi
Opsi "Reset ke Bawaan" SHALL meminta konfirmasi terlebih dahulu kepada pemain. Setelah dikonfirmasi, file layout override milik kit aktif dihapus dari penyimpanan internal, papan kendang langsung kembali menampilkan tata letak default dari `kit.json`, dan pemain tetap berada di mode kalibrasi.

#### Scenario: Konfirmasi reset mengembalikan tata letak default
- **WHEN** pemain memilih "Reset ke Bawaan" dan menyetujui dialog konfirmasi
- **THEN** file override dihapus, semua kendang kembali ke posisi dan ukuran default bawaan `kit.json`, dan pemain tetap di mode kalibrasi

### Requirement: Tata letak Halaman Sesuaikan bebas panel bawah
Antarmuka Halaman Sesuaikan SHALL hanya menempatkan tombol aksi (`✕ Batal`, `↺ Reset`, `✓ Simpan`) dan teks petunjuk singkat pada bar header atas, tanpa bilah bawah atau slider di atas kanvas, sehingga seluruh area bermain kendang terlihat utuh dan bebas halangan.

#### Scenario: Seluruh area kendang bebas dari panel penutup
- **WHEN** Halaman Sesuaikan aktif di layar landscape
- **THEN** tidak ada panel bawah atau slider yang menutupi kanvas, dan keempat kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`) terlihat penuh

### Requirement: Batas independensi dengan Mode Developer
Layout override pemain SHALL tidak diterapkan pada salinan kerja kit di Mode Developer. Penyuntingan tata letak di Mode Developer adalah pengubahan paket kit mandiri yang diatur oleh spesifikasi `kit-authoring`. Semua perubahan tata letak di Mode Developer SHALL ditulis ke `kit.json` di salinan kerja dan ke file `.vrkendang` saat disimpan, bukan ke `files/layouts/<kitId>.json`.

#### Scenario: Mode Developer mengabaikan override pemain
- **WHEN** developer membuka Mode Developer untuk kit yang memiliki layout override pemain
- **THEN** Mode Developer memuat data asli kit dari paket/bawaan tanpa menerapkan layout override pemain

#### Scenario: Perubahan tata letak Mode Developer tidak ditulis ke override pemain
- **WHEN** developer mengubah posisi atau ukuran kendang lalu menyimpan kit di Mode Developer
- **THEN** perubahan ditulis ke `kit.json` salinan kerja dan file `.vrkendang`, dan `files/layouts/<kitId>.json` tidak berubah

### Requirement: Pengujian logika penjepitan dan penerapan override tanpa perangkat
Seluruh logika penjepitan posisi kanvas, penyesuaian skala maksimum, dan penggabungan layout override SHALL diimplementasikan di modul `:kit-core` (Kotlin/JVM murni tanpa dependensi `android.*`) dan diuji otomatis oleh JVM unit test di CI.

#### Scenario: Unit test CI menguji penjepitan posisi dan batas skala
- **WHEN** GitHub Actions menjalankan unit test modul `:kit-core`
- **THEN** tes memverifikasi bahwa center dijepit sehingga oval kendang selalu berada di dalam kanvas, skala di atas batas dijepit, file override rusak diabaikan, dan hasil penggabungan selalu lolos validasi `KitValidator`
