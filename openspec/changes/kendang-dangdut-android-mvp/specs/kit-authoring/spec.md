# Spec Delta

## Purpose

Menyediakan alat penyusun kit yang hanya tersedia di build debug, sebagai instrumen utama developer/pemilik produk untuk merakit kit kendang secara mandiri: memasangkan sampel rekaman WAV asli kendang dari penyimpanan HP ke tiap zona, mengatur posisi, ukuran, busur Bass, dan gain, serta mengekspor dan mengimpor kit dalam format paket `.vrkendang` — semua tanpa build ulang.

## ADDED Requirements

### Requirement: Mode Developer hanya tersedia di build debug
Mode Developer (alat penyusun kit) SHALL hanya dapat diakses pada APK debug dan tidak hadir sama sekali pada APK release. Tombol akses ke Mode Developer muncul di menu Pengaturan (⚙) khusus pada build debug.

#### Scenario: Tombol Mode Developer tampil di build debug
- **WHEN** pengguna membuka menu Pengaturan di build debug
- **THEN** terdapat opsi untuk membuka Mode Developer

#### Scenario: Mode Developer tidak ada di build release
- **WHEN** pengguna membuka menu Pengaturan di build release
- **THEN** tidak ada opsi Mode Developer di menu; kode Mode Developer tidak terdapat di APK

### Requirement: Buka kit sebagai salinan kerja
Developer SHALL dapat membuka file `.vrkendang` dari penyimpanan HP atau membuka kit bawaan APK sebagai titik awal kerja. Kit bawaan tidak pernah dimodifikasi langsung; membukanya menghasilkan salinan kerja di direktori cache (`cacheDir/dev-kit/`).

#### Scenario: Developer membuka file `.vrkendang` dari penyimpanan HP
- **WHEN** developer memilih "Buka Kit" dan memilih file `.vrkendang` dari pemilih file sistem
- **THEN** file diekstrak, divalidasi, dimuat ke direktori kerja, dan siap diedit

#### Scenario: Developer membuka kit bawaan APK sebagai salinan kerja
- **WHEN** developer memilih kit bawaan dari daftar dan memilih "Buka sebagai Salinan Kerja"
- **THEN** kit bawaan diekstrak ke direktori kerja; kit asli di dalam APK tidak tersentuh

### Requirement: Pasang file suara WAV dari penyimpanan HP per zona
Developer SHALL dapat memilih dan memasangkan file WAV dari penyimpanan HP ke zona mana pun di kit yang sedang diedit. Setelah dipasangkan, file langsung dimuat ke audio engine dan dapat langsung diuji ketukan di papan yang sama.

#### Scenario: Developer memilih file WAV dari HP dan memasangkannya ke zona
- **WHEN** developer memilih zona (misalnya Bass-1) lalu menekan "Pilih Suara (WAV)" dan memilih file dari pemilih file sistem
- **THEN** file WAV disalin ke direktori kerja, langsung di-load ke audio engine, dan zona tersebut langsung berbunyi saat diketuk

#### Scenario: File non-WAV atau WAV non-PCM 16-bit ditolak dengan pesan jelas
- **WHEN** developer memilih file yang bukan WAV PCM 16-bit
- **THEN** file ditolak dan aplikasi menampilkan pesan galat yang jelas: "Format harus WAV PCM 16-bit"; zona tetap seperti sebelumnya

#### Scenario: Suara yang baru dipasang langsung bisa diuji
- **WHEN** developer telah berhasil memasangkan file WAV ke zona
- **THEN** developer dapat langsung mengetuk zona tersebut di papan dan mendengar suara yang baru dipasang, tanpa perlu menyimpan atau restart

### Requirement: Ubah posisi dan ukuran kendang secara interaktif
Developer SHALL dapat menggeser posisi setiap kendang dan mengubah ukurannya (via slider skala) secara interaktif di layar dalam Mode Developer, sama seperti di Mode Kalibrasi pemain.

#### Scenario: Menggeser kendang mengubah posisinya secara real-time
- **WHEN** developer menyentuh dan menggeser kendang di Mode Developer
- **THEN** posisi kendang berubah real-time di layar

#### Scenario: Slider skala mengubah ukuran kendang
- **WHEN** developer menggerakkan slider ukuran untuk kendang yang dipilih
- **THEN** ukuran kendang berubah real-time di layar

### Requirement: Ubah gain (volume) per zona
Developer SHALL dapat mengatur nilai gain (volume) per zona di rentang 0.0–2.0 menggunakan kontrol yang tersedia (slider atau input angka).

#### Scenario: Gain zona dapat diubah dan langsung terdengar
- **WHEN** developer mengubah nilai gain suatu zona
- **THEN** perubahan volume terdengar saat zona diketuk setelahnya

### Requirement: Ubah parameter busur pembatas Bass (divider)
Developer SHALL dapat menyesuaikan parameter busur pembatas Bass (`dx`, `dy`, `r`) secara interaktif untuk mengkalibrasi batas antara zona utama dan zona bulan sabit kendang Bass.

#### Scenario: Perubahan parameter divider langsung terlihat di papan
- **WHEN** developer mengubah parameter divider Bass
- **THEN** garis panduan busur pembatas di layar berubah mengikuti nilai baru secara real-time

### Requirement: Simpan dan ekspor kit sebagai file `.vrkendang`
Developer SHALL dapat menyimpan seluruh isi kit yang sedang diedit (kit.json dan semua file WAV yang dipasangkan) menjadi satu file `.vrkendang` di penyimpanan HP menggunakan Android System File Picker. Menyimpan ke file yang sama menimpa isinya tanpa membuat salinan cadangan otomatis.

#### Scenario: Kit berhasil disimpan sebagai file `.vrkendang`
- **WHEN** developer menekan "Simpan / Ekspor" dan memilih lokasi tujuan
- **THEN** file `.vrkendang` yang berisi `kit.json` dan semua file WAV yang dipasangkan berhasil ditulis ke penyimpanan HP

#### Scenario: Menyimpan ke file yang sama menimpa tanpa konfirmasi cadangan
- **WHEN** developer menyimpan ke file `.vrkendang` yang sudah ada
- **THEN** isi file ditimpa sepenuhnya; tidak ada file `.bak` yang dibuat

### Requirement: Ekspor kit menjadi kit bawaan APK (alur rilis manual)
Developer SHALL dapat mengekspor kit yang sudah matang dari HP untuk ditempatkan di `assets/kits/<kitId>/` di repositori kode sebagai kit bawaan APK, dengan cara menyalin/mengekstrak file `.vrkendang` secara manual ke repo dan mendorongnya ke CI.

#### Scenario: Alur rilis kit bawaan berhasil
- **WHEN** developer mengekstrak file `.vrkendang` ke `assets/kits/<kitId>/` di repo, commit, dan push ke main
- **THEN** CI membangun APK baru yang menyertakan kit tersebut sebagai kit bawaan yang tersedia di daftar kit
