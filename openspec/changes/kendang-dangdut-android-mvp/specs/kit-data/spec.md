# Spec Delta

## Purpose

Mendefinisikan format data kit kendang berbasis JSON, perilaku sistem dalam membaca, memvalidasi, dan mengelola sumber data kit (bawaan APK, layout override pemain, paket `.vrkendang`), serta penanganan zona tanpa suara yang aman.

## ADDED Requirements

### Requirement: Satu kit adalah satu folder mandiri di dalam APK
Setiap kit kendang SHALL direpresentasikan sebagai satu folder di `assets/kits/<kitId>/` yang hanya berisi `kit.json`. File audio WAV tidak disertakan di dalam APK; semua suara dimasukkan oleh developer via Mode Developer langsung dari penyimpanan HP.

#### Scenario: Kit bawaan tersedia saat aplikasi pertama dibuka
- **WHEN** aplikasi diinstal pertama kali
- **THEN** kit New Metro tersedia di daftar kit berdasarkan konfigurasi tata letak `kit.json` bawaan di APK

#### Scenario: Zona kit bawaan berstatus unassigned saat pertama dibuka
- **WHEN** developer pertama kali membuka kit New Metro sebelum memasangkan suara
- **THEN** semua zona ditampilkan di papan; sentuhan pada zona hening secara aman (tidak crash)

### Requirement: Kolom `sound` mendukung nilai null (zona unassigned)
Kolom `sound` di setiap entri zona dalam `kit.json` SHALL mendukung nilai `null`, yang berarti zona tersebut belum memiliki file suara. Zona unassigned ditangani secara aman oleh seluruh sistem.

#### Scenario: Kit dengan zona unassigned berhasil dimuat tanpa error
- **WHEN** `kit.json` memiliki satu atau lebih zona dengan `"sound": null`
- **THEN** kit berhasil dimuat dan zona yang null tidak memicu crash atau error apapun

#### Scenario: Sentuhan pada zona unassigned tidak menghasilkan suara maupun error
- **WHEN** pengguna menyentuh zona yang nilai `sound`-nya null
- **THEN** tidak ada suara yang dibunyikan dan tidak ada crash

### Requirement: Format paket kit `.vrkendang` (satu file ekspor/impor)
Sistem SHALL mendukung format paket kit satu file berekstensi **`.vrkendang`**, yaitu file ZIP berisi `kit.json` dan `sounds/*.wav`, untuk keperluan ekspor dan impor kit oleh developer via Mode Developer.

#### Scenario: Paket `.vrkendang` berhasil diimpor dan dimuat
- **WHEN** developer mengimpor file `.vrkendang` yang valid
- **THEN** kit beserta semua file WAV berhasil diekstrak, divalidasi, dan dimuat ke Mode Developer

#### Scenario: Paket `.vrkendang` dengan path traversal ditolak
- **WHEN** developer mengimpor file `.vrkendang` yang mengandung entri ZIP dengan path `../` atau path absolut
- **THEN** impor ditolak dengan pesan galat yang jelas dan tidak ada file yang ditulis ke disk

### Requirement: Layout override pemain (posisi dan ukuran saja)
Sistem SHALL memisahkan penyimpanan kustomisasi tata letak pemain dari data kit. Pemain hanya menyimpan delta posisi dan skala per kendang sebagai file override kecil di penyimpanan internal aplikasi (`files/layouts/<kitId>.json`), tanpa menduplikasi file WAV.

#### Scenario: Tata letak yang dikustomisasi pemain langsung aktif setelah disimpan
- **WHEN** pemain menyimpan hasil edit tata letak
- **THEN** file override tersimpan di penyimpanan internal aplikasi dan tata letak langsung diperbarui di papan

#### Scenario: Kit tetap diperbarui saat ada pembaruan aplikasi
- **WHEN** pembaruan aplikasi membawa versi kit bawaan yang lebih baru
- **THEN** suara dan parameter kit mengikuti versi terbaru; override posisi/skala pemain tetap dipakai selama id kendang masih cocok

### Requirement: Reset layout override pemain
Pemain SHALL dapat menghapus layout override untuk mengembalikan tata letak kit ke posisi dan ukuran bawaan APK.

#### Scenario: Reset menghapus override dan mengembalikan tata letak bawaan
- **WHEN** pemain memilih opsi "Reset ke Bawaan" di Mode Kalibrasi
- **THEN** file override di `files/layouts/<kitId>.json` dihapus dan papan menampilkan tata letak bawaan dari `kit.json` di APK

### Requirement: Koordinat kit menggunakan sistem relatif
Semua nilai posisi dan ukuran di `kit.json` SHALL dinyatakan dalam koordinat relatif (nilai 0.0 hingga 1.0) terhadap dimensi kanvas, bukan dalam satuan piksel tetap.

#### Scenario: Koordinat relatif menghasilkan tata letak proporsional di HP berbeda
- **WHEN** kit yang sama dimuat di HP dengan ukuran dan rasio layar berbeda
- **THEN** posisi dan ukuran kendang terlihat proporsional di semua perangkat

### Requirement: Data kit divalidasi ketat saat dimuat
Sistem SHALL memvalidasi `kit.json` dan paket `.vrkendang` saat dimuat dan menolak kit dengan struktur data tidak valid atau nilai di luar rentang yang diizinkan, dengan pesan galat yang dapat diidentifikasi. Kit tidak valid menyebabkan aplikasi kembali ke kit sebelumnya atau kit bawaan.

#### Scenario: Kit valid berhasil dimuat
- **WHEN** `kit.json` memiliki struktur dan nilai yang benar
- **THEN** kit berhasil dimuat dan siap dimainkan

#### Scenario: Kit dengan JSON tidak valid ditolak dan fallback ke kit sebelumnya
- **WHEN** `kit.json` mengandung syntax JSON rusak, field wajib yang hilang, atau nilai di luar rentang
- **THEN** kit tersebut tidak dimuat, pesan galat dicatat, dan aplikasi tetap menggunakan kit yang sebelumnya aktif

### Requirement: Logika pembacaan dan validasi kit dapat diuji tanpa perangkat
Logika pembacaan JSON, validasi skema, penggabungan layout override, dan pembacaan paket kit SHALL diimplementasikan di modul `:kit-core` (Kotlin/JVM murni tanpa `android.*`) sehingga dapat diuji otomatis dengan JVM unit test di CI.

#### Scenario: Unit test parsing dan validasi kit berjalan di CI
- **WHEN** GitHub Actions menjalankan unit test suite
- **THEN** tes untuk parsing `kit.json`, validasi skema, penggabungan override, dan pembacaan paket `.vrkendang` (termasuk uji path traversal) berjalan dan lulus
