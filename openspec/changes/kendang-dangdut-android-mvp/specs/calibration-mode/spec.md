# Spec Delta

## Purpose

Memungkinkan pengguna (pengembang saat dev, pemain umum di masa depan) untuk mengatur posisi dan ukuran kendang langsung di layar perangkat dan menyimpan hasilnya ke file `kit.json` tanpa perlu build ulang.

## ADDED Requirements

### Requirement: Mode kalibrasi dapat diaktifkan dari menu
Mode kalibrasi SHALL dapat diakses melalui menu pengaturan (tombol Gear di header) tanpa memerlukan build khusus atau flag developer yang tersembunyi.

#### Scenario: Pemain membuka mode kalibrasi dari menu
- **WHEN** pemain membuka menu pengaturan dan memilih opsi kalibrasi tata letak
- **THEN** layar beralih ke mode kalibrasi yang menampilkan kontrol pengaturan posisi dan ukuran

### Requirement: Geser kendang untuk mengubah posisi
Di mode kalibrasi, pemain SHALL dapat menggeser (drag) setiap kendang ke posisi baru di dalam area papan menggunakan satu jari.

#### Scenario: Kendang mengikuti gerakan jari
- **WHEN** pemain menyentuh dan menggeser kendang di mode kalibrasi
- **THEN** kendang bergerak mengikuti posisi jari secara real-time

### Requirement: Ubah ukuran kendang
Di mode kalibrasi, pemain SHALL dapat mengubah ukuran setiap kendang melalui kontrol yang tersedia (slider atau gesture pinch) dalam batas ukuran minimum dan maksimum yang wajar.

#### Scenario: Slider mengubah ukuran kendang
- **WHEN** pemain menggerakkan slider ukuran untuk kendang yang dipilih
- **THEN** ukuran kendang berubah secara real-time di layar

### Requirement: Tampilkan panduan batas zona di mode kalibrasi
Di mode kalibrasi, sistem SHALL menampilkan garis panduan transparan yang memperlihatkan batas-batas zona akustik pada setiap kendang untuk membantu pengguna memosisikan kendang secara akurat.

#### Scenario: Batas zona terlihat di mode kalibrasi saja
- **WHEN** mode kalibrasi aktif
- **THEN** garis panduan zona akustik terlihat pada setiap kendang

#### Scenario: Batas zona tidak terlihat di mode bermain normal
- **WHEN** mode kalibrasi tidak aktif
- **THEN** tidak ada garis panduan yang terlihat pada kulit kendang

### Requirement: Simpan hasil kalibrasi ke storage lokal
Pemain SHALL dapat menyimpan posisi dan ukuran kendang yang telah dikalibrasi. Hasil kalibrasi SHALL disimpan sebagai file `kit.json` di storage perangkat dan langsung diterapkan tanpa perlu menutup dan membuka ulang aplikasi.

#### Scenario: Kalibrasi tersimpan dan langsung aktif
- **WHEN** pemain menyimpan hasil kalibrasi
- **THEN** file `kit.json` baru tersimpan di storage dan tata letak kendang di papan diperbarui sesuai hasil kalibrasi

### Requirement: Ekspor `kit.json` via Android Share Sheet
Pemain SHALL dapat mengirimkan file `kit.json` hasil kalibrasi ke aplikasi lain (Telegram, Google Drive, email, dll.) menggunakan Android Share Sheet bawaan sistem.

#### Scenario: File JSON dapat dibagikan ke aplikasi lain
- **WHEN** pemain menekan tombol Ekspor / Bagikan di mode kalibrasi
- **THEN** Android Share Sheet muncul dengan opsi aplikasi tujuan yang tersedia di perangkat

### Requirement: Reset ke posisi default kit
Di mode kalibrasi, pemain SHALL dapat mengembalikan tata letak kendang ke posisi dan ukuran default yang didefinisikan di `kit.json` bawaan.

#### Scenario: Reset mengembalikan posisi default
- **WHEN** pemain memilih opsi Reset di mode kalibrasi
- **THEN** semua kendang kembali ke posisi dan ukuran default dari data kit asli
