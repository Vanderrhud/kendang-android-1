## ADDED Requirements

### Requirement: Pasang gambar PNG kendang dari penyimpanan HP
Developer SHALL dapat memasang gambar PNG dari penyimpanan HP ke kendang mana pun via pemilih file sistem. File disimpan sebagai `images/<drumId>.png`, `kit.json` diperbarui, dan gambar langsung tampil di pratinjau. Gambar SHALL berbentuk persegi (lebar == tinggi); gambar non-persegi ditolak. Opsi "Kosongkan gambar" mengembalikan `image` ke null dan menghapus file; pembatalan tidak mengubah apa pun.

#### Scenario: Developer memasang PNG ke kendang
- **WHEN** developer memilih kendang lalu menekan tombol pasang gambar dan memilih file PNG yang valid dan berbentuk persegi
- **THEN** file disimpan sebagai `images/<drumId>.png`, `image` di `kit.json` terisi, dan kendang langsung tampil dengan gambar tersebut di pratinjau

#### Scenario: File yang bukan PNG valid ditolak
- **WHEN** developer memilih file yang bukan PNG valid
- **THEN** file ditolak dengan pesan galat yang jelas dan gambar kendang tetap seperti sebelumnya

#### Scenario: Gambar non-persegi ditolak
- **WHEN** developer memilih file PNG yang tidak berbentuk persegi (lebar != tinggi, misalnya 1200x800 piksel)
- **THEN** file ditolak dengan pesan galat bahwa gambar harus berbentuk persegi (rasio 1:1) dan gambar kendang tetap seperti sebelumnya

#### Scenario: PNG melebihi 2 MB ditolak
- **WHEN** developer memilih file PNG berukuran lebih dari 2 MB
- **THEN** file ditolak dengan pesan galat batas ukuran dan gambar kendang tetap seperti sebelumnya

#### Scenario: Mengosongkan gambar kendang
- **WHEN** developer memilih "Kosongkan gambar" pada kendang yang memiliki gambar
- **THEN** `image` menjadi null, file `images/<drumId>.png` dihapus, dan kendang kembali memakai render bawaan Canvas

#### Scenario: Membatalkan pemilih file gambar tidak mengubah apa pun
- **WHEN** developer membuka pemilih file gambar lalu membatalkannya
- **THEN** gambar dan `kit.json` kendang tersebut tetap seperti sebelumnya

### Requirement: Gambar PNG di-downsample saat dipasang
Saat memasang gambar, sistem SHALL memperkecil gambar sehingga sisi terpanjang maksimum 1024 piksel dengan proporsi tetap terjaga, lalu menyimpan hasilnya sebagai PNG. Gambar yang sisi terpanjangnya sudah ≤ 1024 piksel SHALL tidak diperbesar. Tujuannya mencegah kehabisan memori akibat foto beresolusi tinggi.

#### Scenario: Gambar resolusi tinggi diperkecil
- **WHEN** developer memasang PNG berukuran 4000x4000 piksel yang ≤ 2 MB
- **THEN** file yang tersimpan di `images/<drumId>.png` berukuran sisi terpanjang 1024 piksel

#### Scenario: Gambar kecil tidak diperbesar
- **WHEN** developer memasang PNG berukuran 512x512 piksel
- **THEN** file yang tersimpan tetap berukuran 512x512 piksel

### Requirement: Siklus hidup file gambar terikat pada kendang
File gambar kendang SHALL mengikuti siklus hidup kendangnya di salinan kerja. Menghapus kendang SHALL menghapus `images/<drumId>.png`. Setiap perubahan `id` kendang SHALL me-rename file gambar menjadi `images/<idBaru>.png` dan memperbarui `image` di `kit.json`, sehingga nama file selalu identik dengan `id` kendang dan tidak ada file gambar yatim.

#### Scenario: Menghapus kendang menghapus gambarnya
- **WHEN** developer menghapus kendang non-bass yang memiliki gambar
- **THEN** file `images/<drumId>.png` kendang tersebut ikut terhapus dari direktori kerja

#### Scenario: Mengubah id kendang me-rename gambarnya
- **WHEN** id sebuah kendang yang memiliki gambar berubah dari `kecil-2` menjadi `ketipung-kanan`
- **THEN** file berpindah menjadi `images/ketipung-kanan.png` dan `image` di `kit.json` berisi `images/ketipung-kanan.png`

### Requirement: Simpan, ekspor, dan impor menyertakan gambar kendang
Menyimpan kit (internal maupun `+ Kit Baru`), mengekspor `.vrkendang`, dan membuka atau mengimpor kit SHALL menyertakan gambar kendang. Ekspor SHALL hanya memuat gambar yang dirujuk `kit.json`. Impor SHALL menerapkan batas keamanan PNG dari `kit-data` dan menolak paket yang gambarnya melanggar aturan validasi.

#### Scenario: Gambar ikut tersimpan ke penyimpanan internal
- **WHEN** developer menekan `✓ Simpan` atau `+ Kit Baru` pada kit yang memiliki gambar kendang
- **THEN** folder `images/` ikut tersimpan di `files/kits/<kitId>/` dan kit di Halaman Pilih Kit tampil dengan gambar tersebut

#### Scenario: Ekspor dan impor mempertahankan gambar
- **WHEN** developer mengekspor kit bergambar ke `.vrkendang` lalu mengimpornya kembali
- **THEN** kit hasil impor menampilkan gambar kendang yang sama

#### Scenario: Paket impor dengan PNG melanggar aturan ditolak
- **WHEN** developer mengimpor paket yang berisi PNG > 2 MB, nama gambar tidak identik dengan id kendang, atau path traversal
- **THEN** impor ditolak dengan pesan galat yang jelas dan kit yang sedang diedit tidak berubah
