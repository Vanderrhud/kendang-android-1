# Spec Delta

## Purpose

Memungkinkan pemain untuk mengatur posisi dan ukuran kendang langsung di layar perangkat dan menyimpan hasilnya sebagai layout override di penyimpanan internal aplikasi, tanpa memerlukan build ulang dan tanpa mengubah data suara kit.

## ADDED Requirements

### Requirement: Mode kalibrasi dapat diaktifkan dari tombol Pengaturan
Mode kalibrasi (edit tata letak) SHALL dapat diakses melalui tombol Pengaturan (⚙) di header yang tersedia di semua build (debug maupun release). Khusus di build debug, tombol yang sama juga memberikan akses ke Mode Developer (alat penyusun kit).

#### Scenario: Pemain membuka mode kalibrasi dari menu
- **WHEN** pemain membuka menu dari tombol Pengaturan dan memilih "Edit Tata Letak"
- **THEN** layar beralih ke mode kalibrasi yang menampilkan kontrol pengaturan posisi dan ukuran kendang

### Requirement: Geser kendang untuk mengubah posisi
Di mode kalibrasi, pemain SHALL dapat menggeser (drag) setiap kendang ke posisi baru di dalam area papan menggunakan satu jari.

#### Scenario: Kendang mengikuti gerakan jari
- **WHEN** pemain menyentuh dan menggeser kendang di mode kalibrasi
- **THEN** kendang bergerak mengikuti posisi jari secara real-time

### Requirement: Ubah ukuran kendang
Di mode kalibrasi, pemain SHALL dapat mengubah ukuran setiap kendang melalui kontrol yang tersedia (slider) dalam batas skala minimum dan maksimum yang wajar (0.5x–1.5x).

#### Scenario: Slider mengubah ukuran kendang
- **WHEN** pemain menggerakkan slider ukuran untuk kendang yang dipilih
- **THEN** ukuran kendang berubah secara real-time di layar

### Requirement: Tampilkan panduan batas zona di mode kalibrasi
Di mode kalibrasi, sistem SHALL menampilkan garis panduan transparan yang memperlihatkan batas-batas zona akustik pada setiap kendang untuk membantu pemain memosisikan kendang secara akurat.

#### Scenario: Batas zona terlihat di mode kalibrasi saja
- **WHEN** mode kalibrasi aktif
- **THEN** garis panduan zona akustik terlihat pada setiap kendang

#### Scenario: Batas zona tidak terlihat di mode bermain normal
- **WHEN** mode kalibrasi tidak aktif
- **THEN** tidak ada garis panduan yang terlihat pada kulit kendang

### Requirement: Simpan hasil kalibrasi sebagai layout override
Pemain SHALL dapat menyimpan posisi dan ukuran kendang yang telah dikalibrasi. Hasil kalibrasi SHALL disimpan sebagai file override kecil di penyimpanan internal aplikasi (`files/layouts/<kitId>.json`) yang hanya berisi delta posisi dan skala, dan langsung diterapkan tanpa perlu menutup dan membuka ulang aplikasi. File audio kit tidak disalin atau dimodifikasi.

#### Scenario: Kalibrasi tersimpan dan langsung aktif
- **WHEN** pemain menyimpan hasil kalibrasi
- **THEN** file override tersimpan dan tata letak kendang di papan diperbarui sesuai hasil kalibrasi tanpa restart

#### Scenario: Hanya posisi dan ukuran yang disimpan, bukan file suara
- **WHEN** pemain menyimpan kalibrasi
- **THEN** file yang tersimpan hanya berisi data posisi center dan scale per kendang; tidak ada file WAV yang disalin atau dimodifikasi

### Requirement: Reset ke posisi default kit
Di mode kalibrasi, pemain SHALL dapat mengembalikan tata letak kendang ke posisi dan ukuran default yang didefinisikan di `kit.json` bawaan APK dengan menghapus file layout override.

#### Scenario: Reset mengembalikan posisi default
- **WHEN** pemain memilih opsi "Reset ke Bawaan" di mode kalibrasi
- **THEN** file override dihapus dan semua kendang kembali ke posisi dan ukuran default dari kit.json bawaan APK
