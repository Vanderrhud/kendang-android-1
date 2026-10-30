# Spec Delta

## Purpose

Mendefinisikan format data kit kendang berbasis JSON dan perilaku sistem dalam membaca, memvalidasi, dan memprioritaskan sumber data kit (bawaan APK vs. override dari storage perangkat).

## ADDED Requirements

### Requirement: Satu kit adalah satu folder mandiri
Setiap kit kendang SHALL direpresentasikan sebagai satu folder mandiri yang berisi file `kit.json` dan subfolder `sounds/` yang berisi seluruh file WAV kit tersebut, sehingga menambah kit baru tidak memerlukan perubahan pada kode aplikasi.

#### Scenario: Kit bawaan tersedia di APK
- **WHEN** aplikasi diinstal pertama kali tanpa koneksi internet
- **THEN** kit New Metro tersedia dan bisa dimainkan langsung dari aset bawaan APK

#### Scenario: Menambah kit baru tidak memerlukan build ulang
- **WHEN** folder kit baru ditempatkan di storage perangkat dengan struktur yang benar
- **THEN** kit tersebut muncul di daftar kit yang bisa dipilih tanpa perlu memperbarui APK

### Requirement: Prioritas Dual-Source (Storage override APK)
Sistem pembaca kit SHALL memprioritaskan folder kit yang berada di storage perangkat (`Android/data/<package>/files/kits/`) di atas kit bawaan APK (`assets/kits/`) jika keduanya memiliki nama folder yang sama.

#### Scenario: Kit dari storage menggantikan kit bawaan
- **WHEN** folder `new_metro/` ada di storage perangkat
- **THEN** aplikasi memuat kit dari storage, bukan dari aset APK, sehingga perubahan kit lokal langsung berlaku tanpa install ulang

#### Scenario: Kit bawaan menjadi fallback saat storage kosong
- **WHEN** tidak ada folder kit di storage perangkat
- **THEN** aplikasi memuat kit bawaan dari aset APK

### Requirement: Koordinat kit menggunakan sistem relatif
Semua nilai posisi dan ukuran di `kit.json` SHALL dinyatakan dalam koordinat relatif (nilai 0.0 hingga 1.0) terhadap dimensi area papan, bukan dalam satuan piksel tetap.

#### Scenario: Koordinat relatif menghasilkan tata letak proporsional di HP berbeda
- **WHEN** kit yang sama dimuat di HP 5 inci dan tablet 10 inci
- **THEN** posisi dan ukuran kendang terlihat proporsional di kedua perangkat

### Requirement: Data kit dapat dibaca dan divalidasi otomatis
Sistem SHALL memvalidasi file `kit.json` saat dimuat dan menolak kit dengan struktur data yang tidak valid, dengan pesan error yang dapat diidentifikasi untuk keperluan debugging.

#### Scenario: Kit valid berhasil dimuat
- **WHEN** `kit.json` memiliki struktur dan nilai yang benar
- **THEN** kit berhasil dimuat dan siap dimainkan

#### Scenario: Kit dengan JSON tidak valid ditolak
- **WHEN** `kit.json` mengandung syntax JSON yang rusak atau field wajib yang hilang
- **THEN** kit tersebut tidak dimuat dan sistem mencatat error yang dapat diidentifikasi

### Requirement: Pembacaan JSON kit dapat diuji tanpa perangkat
Logika pembacaan dan parsing `kit.json` SHALL dapat diuji otomatis dengan JVM unit test tanpa memerlukan emulator atau perangkat fisik.

#### Scenario: Unit test parsing JSON kit berjalan di CI
- **WHEN** GitHub Actions menjalankan unit test suite
- **THEN** tes untuk pembacaan dan validasi `kit.json` berjalan dan lulus
