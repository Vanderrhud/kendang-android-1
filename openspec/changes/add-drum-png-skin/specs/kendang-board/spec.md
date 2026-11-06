## ADDED Requirements

### Requirement: Render kendang memakai gambar PNG bila tersedia
Bila sebuah kendang memiliki gambar PNG yang valid dan berhasil dimuat, papan SHALL menggambar gambar tersebut ke kotak batas kendang (`center ± radius`, yaitu lebar `2·rx` dan tinggi `2·ry`) sehingga gambar bulat otomatis menjadi oval saat `rx != ry`. Gambar SHALL mengikuti perubahan posisi dan ukuran secara real-time (Mode Developer maupun Mode Sesuaikan), dan urutan Z-order serta hit-testing SHALL tidak berubah.

#### Scenario: Gambar bulat tampil bulat pada kendang bulat
- **WHEN** kendang dengan `rx == ry` memiliki gambar PNG bulat
- **THEN** gambar tampil bulat sempurna dan menutupi tepat area kulit kendang

#### Scenario: Gambar mengikuti bentuk oval secara otomatis
- **WHEN** developer mengubah radius kendang sehingga `rx != ry`
- **THEN** gambar PNG langsung diregangkan menjadi oval mengikuti kotak batas baru pada saat itu juga, dan area sentuhan tetap berimpit dengan gambar

#### Scenario: Gambar mengikuti geser dan ubah ukuran
- **WHEN** pemain atau developer menggeser atau mencubit kendang yang bergambar
- **THEN** gambar bergerak dan berubah ukuran bersama kendang tanpa tertinggal

#### Scenario: Z-order gambar selaras dengan hit-test
- **WHEN** dua kendang bergambar saling tumpang tindih
- **THEN** gambar kendang berindeks lebih kecil tampak di atas, konsisten dengan prioritas hit-test

### Requirement: Render bawaan Canvas dimatikan untuk kendang bergambar
Untuk kendang yang digambar dengan PNG, papan SHALL tidak menggambar badan 3D, kulit, cincin dalam, maupun rim bawaan Canvas. Yang tampil di atas gambar hanya garis panduan atau label sesuai mode aktif dan highlight sentuhan. Kendang tanpa gambar SHALL tetap memakai render bawaan Canvas seperti sebelumnya.

#### Scenario: Kendang bergambar tidak menampilkan render bawaan
- **WHEN** sebuah kendang memiliki gambar PNG yang berhasil dimuat
- **THEN** tidak ada badan 3D, kulit, cincin dalam, atau rim bawaan yang tergambar untuk kendang tersebut

#### Scenario: Kendang tanpa gambar tetap memakai render bawaan
- **WHEN** sebuah kendang memiliki `image` null
- **THEN** kendang tampil dengan render bawaan Canvas seperti sebelumnya, berdampingan dengan kendang lain yang bergambar

#### Scenario: Highlight sentuhan tetap tampil di atas gambar
- **WHEN** pemain menyentuh zona pada kendang bergambar
- **THEN** visual highlight zona tampil presisi di atas gambar PNG, termasuk pada zona tanpa suara

### Requirement: Kegagalan memuat gambar kendang jatuh ke render bawaan
Bila gambar kendang tidak dapat dimuat atau didekode, papan SHALL memperlakukan kendang tersebut sebagai tanpa gambar dan memakai render bawaan Canvas, mencatat galat, dan tidak crash. Pemuatan dan pendekodean gambar SHALL tidak dilakukan di dalam siklus gambar per frame, dan gambar SHALL tidak membuat sentuhan tertunda.

#### Scenario: File gambar rusak tidak menyebabkan crash
- **WHEN** kit memuat gambar kendang yang rusak atau tidak dapat didekode
- **THEN** kendang tampil dengan render bawaan Canvas, galat dicatat, dan aplikasi tetap berjalan normal

#### Scenario: Pergantian kit memuat gambar kit baru
- **WHEN** pemain mengaktifkan kit lain lewat Halaman Pilih Kit
- **THEN** papan menampilkan gambar kendang milik kit baru (atau render bawaan bagi kendang tanpa gambar) dan tidak memakai gambar kit sebelumnya
