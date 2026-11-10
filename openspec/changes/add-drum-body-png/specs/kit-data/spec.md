## MODIFIED Requirements

### Requirement: Format dan pembacaan paket kit .vrkendang
Sistem SHALL mendukung pembacaan dan ekstraksi format paket kit satu file berekstensi **`.vrkendang`**, yaitu file ZIP berisi `kit.json`, `sounds/*.wav`, dan `images/*.png`. Paket yang disusun sistem SHALL hanya memuat `kit.json`, file WAV yang dirujuk `kit.json` dengan nama `sounds/<zoneId>.wav`, dan file PNG yang dirujuk `kit.json` dengan nama `images/<drumId>.png` (kulit) serta `images/<drumId>-body.png` (body); file WAV atau PNG yatim (tidak dirujuk) SHALL tidak disertakan.

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
- **WHEN** paket `.vrkendang` disusun dari kit yang direktori kerjanya memiliki file PNG yatim (misalnya bekas kendang atau body yang sudah dihapus)
- **THEN** paket hanya berisi file `images/<drumId>.png` dan `images/<drumId>-body.png` yang dirujuk oleh `kit.json`, tanpa file PNG yatim

#### Scenario: Paket dengan gambar dapat ditulis lalu dibaca kembali identik
- **WHEN** kit yang memiliki gambar kulit dan/atau gambar body kendang ditulis menjadi paket `.vrkendang` lalu dibaca kembali
- **THEN** `kit.json` dan seluruh file PNG hasil baca identik dengan kit asal

### Requirement: Skema kit.json
File `kit.json` SHALL mendeklarasikan struktur wajib: metadata kit (`formatVersion`, `kitId`, `name`, `version`, `canvasAspect`) dan array `drums[]` (memuat `id`, `model`, `center`, `radius`, `zones[]`, serta `divider` opsional, `image` opsional untuk kulit, dan objek `body` opsional untuk body kendang). Medan `image` per drum bernilai `null` atau `images/<drumId>.png`. Objek `body` per drum bernilai `null` atau memuat `image` (`images/<drumId>-body.png`), `width`, `height`, `offsetX`, `offsetY`, dan `rotation`. Kit tanpa medan `image` atau `body` SHALL tetap valid dan diperlakukan sebagai `null`. Urutan elemen array `drums` SHALL menentukan prioritas evaluasi hit-testing.

#### Scenario: Kit New Metro terurai menjadi 4 drum dan 10 zona sesuai prioritas array
- **WHEN** file `kit.json` New Metro di-parse
- **THEN** terbentuk struktur 4 drum (Bass, Kecil 1, Sedang, Kecil 2) dan total 10 zona dengan urutan evaluasi hit-testing mengikuti indeks array drums

#### Scenario: Kit lama tanpa medan image tetap berhasil diurai
- **WHEN** `kit.json` yang tidak memiliki medan `image` maupun `body` pada drum mana pun di-parse
- **THEN** kit berhasil dimuat, semua drum berstatus tanpa gambar kulit dan tanpa gambar body, dan tampilan kendang tidak berubah dari sebelumnya

#### Scenario: Kit dengan objek body kendang berhasil diurai dan diserialisasi ulang
- **WHEN** `kit.json` yang memiliki objek `body` pada salah satu drum di-parse lalu diserialisasi kembali
- **THEN** nilai `body.image`, `body.width`, `body.height`, `body.offsetX`, `body.offsetY`, dan `body.rotation` terjaga secara presisi

## ADDED Requirements

### Requirement: Aturan validasi parameter body kendang di kit.json
Bila suatu kendang di `kit.json` menyertakan objek `body` tidak null, sistem SHALL memvalidasi bahwa `body.image` persis bernilai `images/<drumId>-body.png` (tanpa path absolut atau `..`), `body.width` dan `body.height` berada di rentang `> 0.0` dan `<= 5.0`, `body.offsetX` dan `body.offsetY` berada di rentang `-3.0..3.0`, serta `body.rotation` berada di rentang `0.0..360.0` derajat.

#### Scenario: Path gambar body tidak sesuai konvensi ditolak
- **WHEN** suatu kendang memiliki `body.image` yang bukan `images/<drumId>-body.png` atau mengandung `..` / path absolut
- **THEN** validasi menolak kit dengan pesan galat path gambar body tidak valid

#### Scenario: Dimensi, offset, atau rotasi body di luar batas ditolak
- **WHEN** suatu kendang memiliki `body.width <= 0.0` atau `> 5.0`, `body.height <= 0.0` atau `> 5.0`, `body.offsetX`/`body.offsetY` di luar `-3.0..3.0`, atau `body.rotation` di luar `0.0..360.0`
- **THEN** validasi menolak kit dengan pesan galat rentang geometri body tidak valid
