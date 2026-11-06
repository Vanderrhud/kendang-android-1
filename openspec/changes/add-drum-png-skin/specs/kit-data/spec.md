## MODIFIED Requirements

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

## ADDED Requirements

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
