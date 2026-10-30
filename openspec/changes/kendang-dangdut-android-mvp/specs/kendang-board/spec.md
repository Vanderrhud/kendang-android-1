# Spec Delta

## Purpose

Mengelola tampilan seluruh kulit kendang sebagai kesatuan visual yang utuh dan menangani semua input multi-touch secara terpusat untuk menentukan zona akustik yang tersentuh.

## ADDED Requirements

### Requirement: Rendering kulit kendang sebagai satu kesatuan visual
Papan kendang SHALL menampilkan setiap kulit kendang sebagai satu bidang visual yang mulus dan utuh tanpa garis pemisah yang terlihat antar zona akustik.

#### Scenario: Kulit Bass tampil sebagai lingkaran penuh
- **WHEN** kit dimuat dan layar dirender
- **THEN** kulit Bass tampil sebagai satu lingkaran penuh tanpa garis internal yang membagi zona

#### Scenario: Kulit oval tampil sebagai satu bidang
- **WHEN** kit dimuat dan layar dirender
- **THEN** kulit Sedang, Kecil 1, dan Kecil 2 masing-masing tampil sebagai satu bidang oval yang mulus

### Requirement: Tata letak kendang bersumber dari data kit
Posisi, ukuran, dan bentuk setiap kendang di layar SHALL ditentukan sepenuhnya dari data `kit.json`, bukan dari kode yang ditulis tetap.

#### Scenario: Posisi kendang mengikuti data kit
- **WHEN** file `kit.json` dimuat dengan koordinat relatif tertentu
- **THEN** setiap kendang dirender di posisi yang sesuai dengan koordinat tersebut di area papan

#### Scenario: Kit berbeda menghasilkan tata letak berbeda
- **WHEN** kit yang berbeda dimuat
- **THEN** jumlah, posisi, dan ukuran kendang berubah sesuai data kit baru tanpa perubahan kode

### Requirement: Aspect-Fit mempertahankan bentuk lingkaran di semua perangkat
Papan kendang SHALL memastikan kulit lingkaran (Bass) selalu tampil sebagai lingkaran sempurna, tidak gepeng maupun memanjang, di semua rasio layar perangkat Android.

#### Scenario: Bass tetap lingkaran di HP rasio panjang (20:9)
- **WHEN** aplikasi berjalan di HP dengan rasio layar landscape 20:9
- **THEN** kulit Bass tampil sebagai lingkaran sempurna, bukan elips

#### Scenario: Bass tetap lingkaran di tablet
- **WHEN** aplikasi berjalan di tablet dengan rasio layar landscape 4:3
- **THEN** kulit Bass tampil sebagai lingkaran sempurna

### Requirement: Penanganan multi-touch terpusat
Papan kendang SHALL menangani semua event sentuhan dari semua jari secara bersamaan dalam satu handler touch, tanpa batasan jumlah jari dari sisi aplikasi.

#### Scenario: Banyak jari diterima bersamaan
- **WHEN** pemain menyentuh layar dengan lebih dari satu jari secara bersamaan
- **THEN** setiap sentuhan diproses secara independen dan memicu suara zona yang sesuai

#### Scenario: Sentuhan di badan kendang diabaikan
- **WHEN** pemain menyentuh area badan (hiasan visual) kendang di luar kulit
- **THEN** tidak ada suara yang dihasilkan

### Requirement: Suara berbunyi saat jari menyentuh layar
Zona akustik SHALL memicu suara pada saat jari menyentuh layar (ACTION_DOWN / ACTION_POINTER_DOWN), bukan saat jari diangkat.

#### Scenario: Suara muncul saat jari landing
- **WHEN** jari pemain menyentuh zona akustik
- **THEN** suara zona tersebut langsung berbunyi tanpa menunggu jari diangkat

### Requirement: Hit-testing zona akustik Bass (Model A)
Kulit Bass SHALL dibagi menjadi 5 zona akustik: Area 1 sebagai zona utama luas di sisi kiri/tengah, dibatasi oleh kurva busur yang menonjol ke kanan; dan Area 2–5 berupa zona bulan sabit di sisi kanan yang dipotong oleh 3 garis horizontal menjadi 4 bilah sama tinggi.

#### Scenario: Sentuhan di zona utama (Area 1) terdeteksi benar
- **WHEN** jari menyentuh area kiri/tengah kulit Bass, di sebelah kiri kurva pembatas
- **THEN** zona Bass-1 terdeteksi dan suara Bass-1 dibunyikan

#### Scenario: Sentuhan di zona bulan sabit (Area 2–5) terdeteksi benar
- **WHEN** jari menyentuh area bulan sabit kanan kulit Bass
- **THEN** zona yang tepat (Bass-2, Bass-3, Bass-4, atau Bass-5) terdeteksi berdasarkan posisi vertikal jari

#### Scenario: Sentuhan di luar kulit Bass tidak membunyikan apa pun
- **WHEN** jari menyentuh area di luar batas lingkaran kulit Bass
- **THEN** tidak ada suara yang dihasilkan

### Requirement: Hit-testing zona akustik Sedang dan Kecil 2
Kulit Sedang dan Kecil 2 SHALL masing-masing dibagi menjadi 2 zona akustik oleh satu garis horizontal di tengah: zona bawah (Area 1) dan zona atas (Area 2).

#### Scenario: Sentuhan di bagian bawah Sedang menghasilkan Sedang-1
- **WHEN** jari menyentuh bagian bawah oval Sedang
- **THEN** zona Sedang-1 terdeteksi dan suaranya dibunyikan

#### Scenario: Sentuhan di bagian atas Sedang menghasilkan Sedang-2
- **WHEN** jari menyentuh bagian atas oval Sedang
- **THEN** zona Sedang-2 terdeteksi dan suaranya dibunyikan

### Requirement: Hit-testing zona akustik Kecil 1
Seluruh kulit Kecil 1 SHALL merupakan satu zona akustik tunggal (Kecil1-1).

#### Scenario: Sentuhan di mana pun pada kulit Kecil 1 membunyikan Kecil1-1
- **WHEN** jari menyentuh bagian mana pun di dalam kulit oval Kecil 1
- **THEN** zona Kecil1-1 terdeteksi dan suaranya dibunyikan

### Requirement: Logika hit-testing dapat diuji tanpa perangkat
Logika hit-testing zona akustik (matematika penentuan area yang tersentuh) SHALL diimplementasikan sebagai kelas Kotlin murni yang tidak bergantung pada framework Android, sehingga dapat diuji otomatis dengan JVM unit test tanpa emulator atau perangkat fisik.

#### Scenario: Unit test hit-testing Bass berjalan di CI
- **WHEN** GitHub Actions menjalankan unit test suite
- **THEN** tes untuk logika hit-testing Bass, Sedang, Kecil 1, dan Kecil 2 berjalan dan lulus tanpa perangkat

### Requirement: Visual feedback saat zona dipukul
Zona yang diketuk SHALL menampilkan respons visual sesaat untuk mengkonfirmasi input pemain diterima.

#### Scenario: Zona memberikan respons visual saat disentuh
- **WHEN** jari pemain menyentuh zona akustik yang valid
- **THEN** zona tersebut memberikan umpan balik visual yang terlihat oleh pemain

### Requirement: Orientasi layar terkunci landscape
Aplikasi SHALL selalu berjalan dalam orientasi landscape dan tidak dapat diputar ke portrait oleh pemain maupun sistem.

#### Scenario: Memutar perangkat tidak mengubah orientasi
- **WHEN** pemain memutar perangkat ke posisi portrait
- **THEN** aplikasi tetap menampilkan layout landscape

### Requirement: Mode layar penuh (Immersive Sticky)
Aplikasi SHALL berjalan dalam mode immersive fullscreen agar nav bar sistem tidak muncul saat pemain sedang bermain secara intensif.

#### Scenario: Nav bar tidak mengganggu sesi bermain
- **WHEN** pemain melakukan rolling cepat di dekat tepi layar
- **THEN** nav bar sistem tidak muncul secara tidak sengaja karena sentuhan pemain

### Requirement: Layar tidak mati selama aplikasi di depan
Layar perangkat SHALL tetap menyala selama aplikasi aktif di foreground, tanpa memandang pengaturan sleep timeout perangkat.

#### Scenario: Layar tidak redup saat jeda bermain
- **WHEN** pemain berhenti mengetuk kendang selama beberapa detik
- **THEN** layar tidak redup atau mati secara otomatis
