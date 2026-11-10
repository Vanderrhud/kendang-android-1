## MODIFIED Requirements

### Requirement: Siklus hidup file gambar terikat pada kendang
File gambar kulit dan gambar body kendang SHALL mengikuti siklus hidup kendangnya di salinan kerja. Menghapus kendang SHALL menghapus `images/<drumId>.png` dan `images/<drumId>-body.png`. Setiap perubahan `id` kendang SHALL me-rename file gambar menjadi `images/<idBaru>.png` dan `images/<idBaru>-body.png` serta memperbarui `image` dan `body.image` di `kit.json`, sehingga nama file selalu konsisten dengan `id` kendang dan tidak ada file gambar yatim.

#### Scenario: Menghapus kendang menghapus gambarnya
- **WHEN** developer menghapus kendang non-bass yang memiliki gambar kulit dan/atau gambar body
- **THEN** file `images/<drumId>.png` dan `images/<drumId>-body.png` kendang tersebut ikut terhapus dari direktori kerja

#### Scenario: Mengubah id kendang me-rename gambarnya
- **WHEN** id sebuah kendang yang memiliki gambar kulit dan gambar body berubah dari `kecil-2` menjadi `ketipung-kanan`
- **THEN** file berpindah menjadi `images/ketipung-kanan.png` dan `images/ketipung-kanan-body.png`, serta `image` dan `body.image` di `kit.json` diperbarui sesuai id baru

### Requirement: Simpan, ekspor, dan impor menyertakan gambar kendang
Menyimpan kit (internal maupun `+ Kit Baru`), mengekspor `.vrkendang`, dan membuka atau mengimpor kit SHALL menyertakan gambar kulit dan gambar body kendang. Ekspor SHALL hanya memuat gambar yang dirujuk `kit.json` (`image` maupun `body.image`). Impor SHALL menerapkan batas keamanan PNG dari `kit-data` dan menolak paket yang gambarnya melanggar aturan validasi.

#### Scenario: Gambar ikut tersimpan ke penyimpanan internal
- **WHEN** developer menekan `✓ Simpan` atau `+ Kit Baru` pada kit yang memiliki gambar kulit dan/atau gambar body kendang
- **THEN** seluruh file gambar yang dirujuk di folder `images/` ikut tersimpan di `files/kits/<kitId>/` dan kit tampil dengan gambar tersebut

#### Scenario: Ekspor dan impor mempertahankan gambar
- **WHEN** developer mengekspor kit bergambar kulit dan body ke `.vrkendang` lalu mengimpornya kembali
- **THEN** kit hasil impor menampilkan gambar kulit dan gambar body kendang beserta parameternya secara identik

#### Scenario: Paket impor dengan PNG melanggar aturan ditolak
- **WHEN** developer mengimpor paket yang berisi PNG > 2 MB, nama gambar kulit/body tidak sesuai id kendang, atau path traversal
- **THEN** impor ditolak dengan pesan galat yang jelas dan kit yang sedang diedit tidak berubah

## ADDED Requirements

### Requirement: Pasang dan kosongkan gambar PNG body kendang di Mode Developer
Developer SHALL dapat memasang gambar PNG body dari penyimpanan HP ke kendang mana pun via pemilih file sistem. Gambar wajib berformat PNG valid berbentuk persegi (lebar == tinggi) maksimum 2 MB, di-downsample hingga sisi terpanjang maksimum 1024 px, disimpan sebagai `images/<drumId>-body.png`, memperbarui objek `body` di `kit.json`, dan langsung tampil di pratinjau. Opsi kosongkan body menghapus `images/<drumId>-body.png` dan mengatur `body` menjadi null.

#### Scenario: Developer memasang PNG body ke kendang
- **WHEN** developer memilih kendang lalu menekan tombol pilih PNG body dan memilih file PNG persegi yang valid (<= 2 MB)
- **THEN** file disimpan sebagai `images/<drumId>-body.png`, objek `body` di `kit.json` terisi dengan parameter awal yang rapat ke kulit, dan gambar body langsung tampil di belakang kulit kendang pada pratinjau

#### Scenario: Gambar body non-persegi atau bukan PNG valid ditolak
- **WHEN** developer memilih file gambar body yang bukan PNG valid, tidak berbentuk persegi (1:1), atau melebihi 2 MB
- **THEN** file ditolak dengan pesan galat yang jelas dan kondisi body kendang tetap seperti sebelumnya

#### Scenario: Mengosongkan gambar body kendang
- **WHEN** developer menekan tombol kosongkan body pada kendang yang memiliki PNG body
- **THEN** `body` di `kit.json` menjadi null, file `images/<drumId>-body.png` dihapus, dan gambar body hilang dari pratinjau

### Requirement: Kontrol Parameter Body Kendang dan Rapatkan ke Kulit di Mode Developer
Saat kendang terpilih memiliki objek `body` tidak null, Mode Developer SHALL menampilkan kotak Parameter Body Kendang berisi slider Lebar Body (0.08–1.00), Panjang/Tinggi Body (0.08–1.00), Geser X (-0.50–+0.50), Geser Y (-0.50–+0.50), Kemiringan Body (0–360 derajat), tombol Rapatkan ke Kulit (`width = 2*rx`, `offsetX = 0`, `offsetY = height/2`, `rotation = 0`), dan tombol Reset Body.

#### Scenario: Menggeser slider parameter body memperbarui tampilan secara real-time
- **WHEN** developer menggeser slider Lebar Body, Panjang/Tinggi Body, Geser X, Geser Y, atau Kemiringan Body
- **THEN** nilai pada objek `body` kendang di `kit.json` diperbarui dan gambar PNG body di pratinjau langsung berubah sesuai nilai slider

#### Scenario: Menekan tombol Rapatkan ke Kulit menyelaraskan body tepat di bawah cincin kulit
- **WHEN** developer menekan tombol `⚡ Rapatkan ke Kulit` pada kendang yang memiliki PNG body
- **THEN** `body.width` disetel menjadi `2 * drum.radius.rx`, `body.offsetX` menjadi `0.0`, `body.offsetY` menjadi `body.height / 2`, dan `body.rotation` menjadi `0.0` sehingga bibir atas body menempel pas di ekuator kulit

#### Scenario: Menekan tombol Reset Body mengembalikan parameter body ke nilai awal standar
- **WHEN** developer menekan tombol `↺ Reset Body`
- **THEN** dimensi, offset, dan kemiringan body kembali ke nilai standar yang rapat terhadap kulit kendang saat ini
