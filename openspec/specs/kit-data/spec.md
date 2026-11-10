# kit-data Specification

## Purpose
Mendefinisikan format data kit kendang berbasis JSON, perilaku sistem dalam membaca, memvalidasi, dan mengelola sumber data kit (bawaan APK, layout override pemain, paket `.vrkendang`), batas keamanan paket, serta penanganan zona tanpa suara yang aman.

## Requirements

### Requirement: Satu kit adalah satu folder mandiri di dalam APK
Setiap kit kendang SHALL direpresentasikan sebagai satu folder di `assets/kits/<kitId>/` yang berisi `kit.json` dan folder `sounds/` untuk WAV. Kit bawaan New Metro berisi `kit.json` dan sampel rekaman suara asli di `sounds/*.wav`.

#### Scenario: Kit bawaan New Metro tersedia saat aplikasi pertama dibuka
- **WHEN** aplikasi diinstal dan dibuka pertama kali
- **THEN** kit New Metro tersedia di daftar kit berdasarkan file `assets/kits/new-metro/kit.json` dengan seluruh sampel suara di `sounds/` termuat

#### Scenario: Zona kit bawaan berstatus unassigned saat pertama dibuka
- **WHEN** developer atau pemain pertama kali membuka kit New Metro sebelum memasangkan suara
- **THEN** semua zona ditampilkan di papan kendang, dan sentuhan pada zona memberikan visual feedback (highlight) tanpa suara dan tanpa crash

### Requirement: Kolom sound mendukung nilai null (zona unassigned)
Kolom `sound` di setiap entri zona dalam `kit.json` SHALL mendukung nilai `null`, yang berarti zona tersebut belum memiliki file suara (misalnya kit bawaan awal MVP atau saat membuat kit baru). Zona unassigned ditangani secara aman oleh seluruh sistem.

#### Scenario: Kit dengan zona unassigned berhasil dimuat tanpa error
- **WHEN** `kit.json` memiliki satu atau lebih zona dengan `"sound": null`
- **THEN** kit berhasil dimuat dan zona yang null tidak memicu crash atau error apapun

#### Scenario: Sentuhan pada zona unassigned tidak menghasilkan suara maupun error
- **WHEN** pengguna menyentuh zona yang nilai `sound`-nya null
- **THEN** visual tap highlight tetap tampil, tidak ada suara yang dibunyikan, dan tidak ada crash

### Requirement: Format dan pembacaan paket kit .vrkendang
Sistem SHALL mendukung pembacaan dan ekstraksi format paket kit satu file berekstensi **`.vrkendang`**, yaitu file ZIP berisi `kit.json`, `sounds/*.wav`, dan `images/*.png`. Paket yang disusun sistem SHALL hanya memuat `kit.json`, file WAV yang dirujuk `kit.json` dengan nama `sounds/<zoneId>.wav`, dan file PNG yang dirujuk `kit.json` dengan nama `images/<drumId>.png`; file WAV atau PNG yatim (tidak dirujuk) SHALL tidak disertakan.

#### Scenario: Paket .vrkendang berhasil diekstrak dan dibaca
- **WHEN** paket `.vrkendang` yang valid dibaca oleh pembaca paket
- **THEN** file `kit.json`, seluruh file WAV, dan seluruh file PNG di dalamnya berhasil diekstrak dan siap divalidasi

#### Scenario: Paket .vrkendang dengan path traversal ditolak
- **WHEN** paket `.vrkendang` mengandung entri ZIP dengan path `../` atau path absolut
- **THEN** pembaca paket menolak file tersebut dengan pesan galat yang jelas dan tidak ada file yang diekstrak ke disk

#### Scenario: Paket hasil penyusunan hanya memuat WAV yang dirujuk
- **WHEN** paket `.vrkendang` disusun dari kit yang direktori kerjanya memiliki file WAV yatim
- **THEN** paket hanya berisi `kit.json` dan file `sounds/<zoneId>.wav` yang dirujuk oleh `kit.json`, tanpa file WAV yatim

#### Scenario: Paket hasil penyusunan hanya memuat PNG yang dirujuk
- **WHEN** paket `.vrkendang` disusun dari kit yang direktori kerjanya memiliki file PNG yatim (misalnya bekas kendang yang sudah dihapus)
- **THEN** paket hanya berisi file `images/<drumId>.png` yang dirujuk oleh `kit.json`, tanpa file PNG yatim

#### Scenario: Paket dengan gambar dapat ditulis lalu dibaca kembali identik
- **WHEN** kit yang memiliki gambar kendang ditulis menjadi paket `.vrkendang` lalu dibaca kembali
- **THEN** `kit.json` dan seluruh file PNG hasil baca identik dengan kit asal

### Requirement: Batas keamanan paket .vrkendang
Sistem SHALL menerapkan batas keamanan saat membaca paket `.vrkendang`: maksimum 100 entri ZIP, total ukuran hasil dekompresi maksimum 100 MB, batas ukuran file per WAV maksimum 5 MB, dan batas ukuran file per PNG maksimum 2 MB. Seluruh entri ZIP SHALL diperiksa dan divalidasi lebih dulu sebelum file apapun diekstrak atau ditulis ke disk.

#### Scenario: Paket yang melebihi batas entri atau ukuran ditolak sebelum penulisan disk
- **WHEN** paket `.vrkendang` mengandung lebih dari 100 entri, berukuran total dekompresi > 100 MB, memiliki file WAV > 5 MB, atau memiliki file PNG > 2 MB
- **THEN** proses ekstraksi ditolak dengan pesan galat batas keamanan dan tidak ada file yang ditulis ke disk

### Requirement: Skema kit.json
File `kit.json` SHALL mendeklarasikan struktur wajib: metadata kit (`formatVersion`, `kitId`, `name`, `version`, `canvasAspect`) dan array `drums[]` (memuat `id`, `model`, `center`, `radius`, `zones[]`, serta `divider` opsional untuk pembatas Bass atau putaran sudut kendang radial, dan `image` opsional). Medan `sound` per zona dapat bernilai `null` atau path relatif. Medan `image` per drum dapat bernilai `null` (belum ada gambar) atau `images/<drumId>.png`. Kit tanpa medan `image` SHALL tetap valid dan diperlakukan sebagai `image: null`. Urutan elemen array `drums` SHALL menentukan prioritas evaluasi hit-testing.

#### Scenario: Kit New Metro terurai menjadi 4 drum dan 10 zona sesuai prioritas array
- **WHEN** file `kit.json` New Metro di-parse
- **THEN** terbentuk struktur 4 drum (Bass, Kecil 1, Sedang, Kecil 2) dan total 10 zona dengan urutan evaluasi hit-testing mengikuti indeks array drums

#### Scenario: Kit lama tanpa medan image tetap berhasil diurai
- **WHEN** `kit.json` yang tidak memiliki medan `image` pada drum mana pun di-parse
- **THEN** kit berhasil dimuat, semua drum berstatus tanpa gambar, dan tampilan kendang tidak berubah dari sebelumnya

### Requirement: Aturan validasi identitas dan geometri kit.json
Sistem SHALL memvalidasi identitas dan geometri `kit.json`: `formatVersion` dikenali; `kitId` hanya huruf kecil, angka, dan tanda hubung; semua `id` kendang dan zona unik; `canvasAspect` > 0; `center.x` dan `center.y` di rentang 0.0–1.0 (atau -3.0–3.0 dengan retensi kanvas); `rx` dan `ry` > 0 dan ≤ 5.0; serta medan opsional `rotation` per kendang berada di rentang 0.0–360.0 derajat (diperlakukan `0.0` bila tidak ada pada kit lama).

#### Scenario: Pelanggaran format identitas ditolak
- **WHEN** `kit.json` memiliki `kitId` dengan huruf besar/karakter ilegal, terdapat duplikasi `id`, atau `formatVersion` tidak dikenali
- **THEN** validasi menolak kit dengan pesan galat identitas tidak valid

#### Scenario: Pelanggaran batas koordinat kanvas atau radius ditolak
- **WHEN** `kit.json` memiliki `canvasAspect <= 0`, koordinat `center` di luar batas valid, atau radius `rx`/`ry` ≤ 0 atau > 5.0
- **THEN** validasi menolak kit dengan pesan galat rentang geometri tidak valid

#### Scenario: Pelanggaran rentang rotasi kendang ditolak
- **WHEN** suatu kendang di `kit.json` memiliki nilai `rotation` kurang dari 0.0 atau lebih dari 360.0
- **THEN** validasi menolak kit dengan pesan galat rentang rotasi kendang tidak valid

#### Scenario: Kit lama tanpa medan rotation diperlakukan berotasi 0 derajat
- **WHEN** `kit.json` yang tidak memiliki medan `rotation` pada drum di-parse
- **THEN** setiap drum memiliki nilai `rotation` default `0.0` dan lolos validasi

### Requirement: Aturan validasi gain, divider, dan region kit.json
Sistem SHALL memvalidasi `gain` di rentang 0.0–2.0; parameter `divider` model `bass` memiliki `dx` dan `dy` di rentang -3.0 sampai 3.0 serta `r` > 0 dan ≤ 3.0; parameter `divider.angle` (jika ada) berada di rentang 0.0 sampai 360.0; dan region sesuai model (`bass`: `main` dan `crescent` dengan `band` 0–3 dan `divider` lengkap; `split`: 1–4 zona dengan region `upper`, `lower`, `sector`, atau `full`; `single`: 1 zona dengan region `full`).

#### Scenario: Pelanggaran batas gain ditolak
- **WHEN** suatu zona memiliki nilai `gain` di bawah 0.0 atau di atas 2.0
- **THEN** validasi menolak kit dengan pesan galat gain di luar batas

#### Scenario: Pelanggaran rentang parameter divider ditolak
- **WHEN** drum model `bass` memiliki `divider.dx` atau `divider.dy` di luar -3.0 sampai 3.0, atau `divider.r` ≤ 0 atau > 3.0, atau `divider.angle` di luar 0.0..360.0
- **THEN** validasi menolak kit dengan pesan galat rentang divider tidak valid

#### Scenario: Pelanggaran region per model ditolak
- **WHEN** drum model `bass` tidak menyertakan `divider`, memiliki `band` di luar 0–3, atau region bukan `main` dan `crescent`; drum model `split` memiliki > 4 zona atau region tidak sah; atau drum model `single` bukan `full`
- **THEN** validasi menolak kit dengan pesan galat struktur region model tidak sesuai

### Requirement: Daftar kit bawaan
Aplikasi SHALL mendaftar seluruh kit bawaan di `assets/kits/*/kit.json` yang valid dan menampilkannya berdasarkan nama kit untuk dipilih oleh pemain. Kit bawaan yang tidak valid SHALL dilewati dan dicatat ke log; duplikasi `kitId` SHALL ditolak. Direktori kerja kit developer (`filesDir/dev-kit/`) SHALL tidak dimunculkan di daftar kit pemain.

#### Scenario: Kit bawaan valid tampil di daftar kit
- **WHEN** pemain membuka pemilih kit
- **THEN** kit New Metro tampil dengan nama "New Metro" sesuai data bawaan APK

#### Scenario: Kit tidak valid dilewati dan kitId ganda ditolak
- **WHEN** terdapat folder kit bawaan dengan JSON rusak atau memiliki `kitId` yang sama dengan kit lain
- **THEN** folder rusak dilewati, duplikasi ditolak, dan daftar hanya menampilkan kit bawaan yang valid

#### Scenario: Kit kerja developer tidak muncul di daftar pemain
- **WHEN** developer sedang menyunting kit di direktori kerja developer
- **THEN** kit kerja tersebut tidak muncul di daftar kit layar utama pemain

### Requirement: Koordinat kit menggunakan sistem relatif
Sistem koordinat kit SHALL mendefinisikan `canvasAspect` sebagai rasio lebar terhadap tinggi kanvas; posisi `x` dan `y` berada di rentang 0.0–1.0 terhadap lebar dan tinggi kanvas; radius `rx` dan `ry` adalah pecahan dari tinggi kanvas; serta perhitungan geometri kanvas menggunakan satuan `X_u = x * canvasAspect` dan `Y_u = y`.

#### Scenario: Geometri menghasilkan lingkaran sempurna di berbagai rasio layar
- **WHEN** kit dengan `canvasAspect 2.0` mendeklarasikan drum di titik `(0.5, 0.5)` dengan radius `rx: 0.2` dan `ry: 0.2`
- **THEN** drum tersebut dirender dan dihitung hit-testing-nya sebagai lingkaran sejati (bukan elips) pada perangkat dengan berbagai rasio layar

### Requirement: Layout override pemain (posisi dan ukuran saja)
Sistem SHALL memisahkan penyimpanan kustomisasi tata letak pemain sebagai file override kecil per kit (`files/layouts/<kitId>.json`). Nilai `scale` SHALL dibatasi di rentang 0.5–5.0 dan `center` SHALL dijepit dengan batas retensi aman sehingga setidaknya 20% radius kendang tetap berada di kanvas dan mengizinkan posisi kendang digeser sebagian keluar batas kanvas. `id` kendang yang tidak dikenal dalam file override SHALL diabaikan; file override yang rusak atau tidak dapat dibaca SHALL diabaikan sehingga tata letak bawaan tetap dipakai tanpa crash; dan kit efektif hasil penggabungan SHALL tetap lolos validasi `KitValidator`.

#### Scenario: Override posisi dan skala diterapkan dan dijepit ke batas valid
- **WHEN** pemain menyimpan override dengan perubahan posisi (termasuk di luar batas 0.0..1.0) dan skala hingga 5.0
- **THEN** posisi diterapkan dengan retensi aman, skala dijepit di 0.5–5.0, dan kit lolos validasi KitValidator

#### Scenario: ID tidak dikenal diabaikan dan file override rusak fallback ke bawaan
- **WHEN** file override berisi ID kendang yang tidak ada atau berkas override rusak
- **THEN** ID tidak dikenal diabaikan, berkas rusak diabaikan, dan aplikasi menampilkan tata letak bawaan kit tanpa crash

### Requirement: Penanganan fallback kit tidak valid
Sistem SHALL mengembalikan kit aktif ke kit yang sebelumnya aktif bila kit baru yang dipilih tidak valid; dan bila tidak ada kit sebelumnya yang aktif, sistem SHALL secara otomatis fallback ke kit bawaan pertama yang valid di `assets/kits/`.

#### Scenario: Fallback ke kit sebelumnya saat kit yang dipilih tidak valid
- **WHEN** pengguna mencoba berpindah ke kit yang rusak saat aplikasi sedang menjalankan kit yang valid
- **THEN** aplikasi menolak perpindahan kit, mencatat galat, dan tetap memainkan kit yang sebelumnya aktif

#### Scenario: Fallback ke kit bawaan pertama saat startup awal
- **WHEN** aplikasi dibuka pertama kali dan kit yang disimpan rusak atau tidak tersedia
- **THEN** aplikasi secara otomatis memuat kit bawaan pertama yang valid (`new-metro`)

### Requirement: Penguraian header WAV di modul :kit-core
Modul `:kit-core` SHALL menyediakan `WavParser` (Kotlin/JVM murni tanpa dependensi `android.*`) yang memeriksa header RIFF/WAVE, mengharuskan format PCM 16-bit mono, dan mengekstrak data byte PCM beserta sample rate aslinya untuk diteruskan ke audio engine. Mesin audio C++ SHALL tidak mengurai format kontainer WAV.

#### Scenario: File WAV PCM 16-bit mono berhasil diurai
- **WHEN** file WAV PCM 16-bit mono diurai oleh `WavParser`
- **THEN** data byte PCM dan sample rate asli berhasil diekstrak dan siap diserahkan ke audio engine

#### Scenario: Chunk tak dikenal dilewati secara aman
- **WHEN** file WAV mengandung chunk metadata tambahan (seperti `LIST` atau `INFO`) sebelum atau sesudah chunk `data`
- **THEN** `WavParser` melewati chunk tersebut secara aman dan berhasil membaca data PCM dari chunk `data`

#### Scenario: File terpotong atau ukuran chunk melebihi panjang file ditolak
- **WHEN** file WAV memiliki header chunk `data` dengan ukuran yang melebihi sisa byte file yang tersedia
- **THEN** `WavParser` menolak file tersebut dengan pesan galat bahwa file terpotong atau rusak

#### Scenario: Format audio selain PCM 16-bit mono ditolak
- **WHEN** file WAV memiliki format non-PCM (termasuk `WAVE_FORMAT_EXTENSIBLE`, floating point, atau terkompresi), bit depth selain 16-bit, atau kanal selain mono
- **THEN** `WavParser` menolak file tersebut dengan pesan galat format tidak didukung

### Requirement: Logika data kit dapat diuji tanpa perangkat
Logika pembacaan JSON, validasi skema, penggabungan layout override, pembacaan dan penyusunan paket kit (`KitPackageReader`/`KitPackageWriter`), pembuatan kit dari template (`KitFactory`), dan parsing WAV SHALL diimplementasikan di modul `:kit-core` (Kotlin/JVM murni tanpa `android.*`) sehingga dapat diuji otomatis dengan JVM unit test di CI.

#### Scenario: Unit test CI memvalidasi seluruh kit bawaan di APK
- **WHEN** GitHub Actions menjalankan unit test suite modul `:kit-core`
- **THEN** unit test memvalidasi setiap file `assets/kits/*/kit.json` (memastikan struktur JSON valid, nilai di dalam batas, dan file WAV yang direferensikan ada dan valid bila `sound` tidak null) sehingga tidak ada kit rusak yang lolos build

#### Scenario: Unit test CI menguji penulisan paket dan pembuatan kit
- **WHEN** GitHub Actions menjalankan unit test suite modul `:kit-core`
- **THEN** tes menulis paket dengan `KitPackageWriter` lalu membacanya ulang dengan `KitPackageReader` dan memastikan kit identik, serta memastikan kit baru dari `KitFactory` lolos `KitValidator` dengan seluruh `sound` null

### Requirement: Aturan validasi gambar kendang
Bila `image` sebuah drum tidak null, sistem SHALL mewajibkan nilainya persis `images/<drumId>.png` dengan `<drumId>` sama dengan `id` drum tersebut, bukan path absolut, dan tanpa `..`. File gambar SHALL ada dan berformat PNG valid. Nama file gambar yang tidak sama dengan `id` kendang SHALL ditolak.

#### Scenario: Nama file gambar tidak identik dengan id kendang ditolak
- **WHEN** drum dengan `id` `sedang-1` memiliki `"image": "images/lainnya.png"`
- **THEN** validasi menolak kit dengan pesan galat bahwa nama gambar harus sama dengan id kendang

#### Scenario: Path gambar absolut atau mengandung traversal ditolak
- **WHEN** `image` berupa path absolut atau mengandung `..`
- **THEN** validasi menolak kit dengan pesan galat path gambar tidak valid

#### Scenario: File gambar yang dirujuk tidak ada atau bukan PNG valid ditolak
- **WHEN** `image` tidak null tetapi file `images/<drumId>.png` tidak ada atau bukan PNG valid
- **THEN** validasi kit bawaan atau paket menolak kit dengan pesan galat gambar tidak ditemukan atau tidak valid

#### Scenario: Kit dengan image null lolos validasi tanpa file gambar
- **WHEN** seluruh drum memiliki `image` null atau tanpa medan `image`
- **THEN** kit lolos validasi gambar tanpa memerlukan folder `images/`
