# Spec Delta

## Purpose

Menyediakan tampilan papan kendang yang responsif dan akurat secara visual maupun akustik: merender semua kendang sebagai satu kesatuan visual, menangani semua event multi-touch secara terpusat, dan mendeteksi zona akustik yang disentuh dengan matematika murni.

## ADDED Requirements

### Requirement: Seluruh papan kendang dirender dalam satu View
Papan kendang SHALL dirender oleh satu Custom View tunggal (`KendangBoardView`) yang mencakup seluruh area papan, bukan oleh View terpisah per kendang.

#### Scenario: Semua kendang tampil dalam satu area papan
- **WHEN** kit dimuat dan Activity ditampilkan
- **THEN** semua kendang (Bass, Kecil 1, Sedang, Kecil 2) terlihat dalam satu area papan yang menjadi satu Custom View

#### Scenario: Kulit Bass tampil sebagai lingkaran sempurna
- **WHEN** kit New Metro dimuat
- **THEN** kulit Bass tampil sebagai lingkaran sempurna

### Requirement: Penanganan multi-touch terpusat
Papan kendang SHALL menangani semua event sentuhan dari semua jari secara bersamaan dalam satu handler touch, tanpa batasan jumlah jari dari sisi aplikasi.

#### Scenario: Banyak jari diterima bersamaan
- **WHEN** pemain menyentuh layar dengan lebih dari satu jari secara bersamaan
- **THEN** setiap sentuhan diproses secara independen dan memicu suara zona yang sesuai (bila zona tersebut sudah memiliki file suara)

#### Scenario: Sentuhan di badan kendang diabaikan
- **WHEN** pemain menyentuh area badan (hiasan visual) kendang di luar kulit
- **THEN** tidak ada suara yang dihasilkan

### Requirement: Suara berbunyi saat jari menyentuh layar
Zona akustik SHALL memicu suara pada saat jari menyentuh layar (ACTION_DOWN / ACTION_POINTER_DOWN), bukan saat jari diangkat.

#### Scenario: Suara muncul saat jari landing
- **WHEN** jari pemain menyentuh zona akustik yang telah memiliki file suara
- **THEN** suara zona tersebut langsung berbunyi tanpa menunggu jari diangkat

### Requirement: Hit-testing zona akustik Bass (Model bass)
Kulit Bass SHALL dibagi menjadi 5 zona akustik: Area 1 sebagai zona utama di sisi kiri/tengah dibatasi oleh busur pembatas `C_div`; dan Area 2–5 berupa zona bulan sabit di sisi kanan yang dibagi menjadi 4 bilah sama tinggi (band 0–3 dari atas ke bawah).

#### Scenario: Sentuhan di zona utama (Area 1) terdeteksi benar
- **WHEN** jari menyentuh area kiri/tengah kulit Bass, di dalam `C_div`
- **THEN** zona Bass-1 terdeteksi dan (jika ada suara) dibunyikan

#### Scenario: Sentuhan di zona bulan sabit (Area 2–5) terdeteksi benar
- **WHEN** jari menyentuh area bulan sabit kanan kulit Bass
- **THEN** zona yang tepat (Bass-2, Bass-3, Bass-4, atau Bass-5) terdeteksi berdasarkan posisi vertikal jari

#### Scenario: Sentuhan di luar kulit Bass tidak membunyikan apa pun
- **WHEN** jari menyentuh area di luar batas oval kulit Bass
- **THEN** tidak ada suara yang dihasilkan

### Requirement: Hit-testing zona akustik Sedang dan Kecil 2 (Model split)
Kulit Sedang dan Kecil 2 SHALL masing-masing dibagi menjadi 2 zona akustik oleh satu garis horizontal di tengah: zona atas (`upper`) dan zona bawah (`lower`).

#### Scenario: Sentuhan di bagian bawah Sedang menghasilkan Sedang-1
- **WHEN** jari menyentuh bagian bawah oval Sedang
- **THEN** zona Sedang-1 terdeteksi dan (jika ada suara) dibunyikan

#### Scenario: Sentuhan di bagian atas Sedang menghasilkan Sedang-2
- **WHEN** jari menyentuh bagian atas oval Sedang
- **THEN** zona Sedang-2 terdeteksi dan (jika ada suara) dibunyikan

### Requirement: Hit-testing zona akustik Kecil 1 (Model single)
Seluruh kulit Kecil 1 SHALL merupakan satu zona akustik tunggal (Kecil1-1).

#### Scenario: Sentuhan di mana pun pada kulit Kecil 1 membunyikan Kecil1-1
- **WHEN** jari menyentuh bagian mana pun di dalam kulit oval Kecil 1
- **THEN** zona Kecil1-1 terdeteksi dan (jika ada suara) dibunyikan

### Requirement: Logika hit-testing dapat diuji tanpa perangkat
Logika hit-testing zona akustik SHALL diimplementasikan sebagai kelas Kotlin murni di modul `:kit-core` yang tidak bergantung pada framework Android, sehingga dapat diuji otomatis dengan JVM unit test tanpa emulator atau perangkat fisik.

#### Scenario: Unit test hit-testing berjalan di CI
- **WHEN** GitHub Actions menjalankan unit test suite
- **THEN** tes untuk logika hit-testing Bass, Sedang, Kecil 1, dan Kecil 2 berjalan dan lulus tanpa perangkat

### Requirement: Visual feedback saat zona disentuh
Zona yang diketuk SHALL menampilkan respons visual sesaat (highlight overlay yang memudar) untuk mengkonfirmasi input pemain diterima. Visual feedback ini tampil untuk semua zona yang disentuh, termasuk zona yang belum memiliki file suara (unassigned).

#### Scenario: Zona memberikan respons visual saat disentuh
- **WHEN** jari pemain menyentuh zona akustik yang valid (dengan atau tanpa suara)
- **THEN** zona tersebut memberikan umpan balik visual yang terlihat oleh pemain

#### Scenario: Zona unassigned tetap memberi visual feedback
- **WHEN** pemain menyentuh zona yang belum memiliki file suara
- **THEN** visual feedback (highlight) tetap tampil; tidak ada suara, tidak ada crash

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
