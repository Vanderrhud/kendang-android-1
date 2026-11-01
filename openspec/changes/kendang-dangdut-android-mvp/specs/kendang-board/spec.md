# Spec Delta

## Purpose

Menyediakan tampilan papan kendang yang responsif dan akurat secara visual maupun akustik: merender semua kendang sebagai satu kesatuan visual, menangani semua event multi-touch secara terpusat, mendeteksi zona akustik yang disentuh dengan matematika murni, memberikan highlight visual presisi per zona, dan menyediakan integrasi header navigasi.

## ADDED Requirements

### Requirement: Seluruh papan kendang dirender dalam satu View
Papan kendang SHALL dirender oleh satu Custom View tunggal (`KendangBoardView`) yang mencakup seluruh area papan, bukan oleh View terpisah per kendang.

#### Scenario: Semua kendang tampil dalam satu area papan
- **WHEN** kit dimuat dan Activity ditampilkan
- **THEN** semua kendang (Bass, Kecil 1, Sedang, Kecil 2) terlihat dalam satu area papan yang menjadi satu Custom View

#### Scenario: Kulit kendang tampil proporsional tanpa distorsi
- **WHEN** kit dimuat pada perangkat dengan rasio layar apapun
- **THEN** kulit tiap kendang tampil sesuai nilai `center` dan `radius` di `kit.json` (berbentuk lingkaran sempurna bila `rx == ry`), dan tidak melar di rasio layar berbeda

### Requirement: Kanvas aspect-fit dan sistem koordinat
Kanvas papan kendang SHALL di-fit berdasarkan tinggi `KendangBoardView`, dipusatkan secara horizontal, dan sisa ruang di kiri-kanan dijadikan margin. Sentuhan di area margin atau di luar kanvas SHALL diabaikan. Konversi dari piksel ke satuan kanvas (`X_u = x * canvasAspect`, `Y_u = y`) SHALL dilakukan satu kali secara konsisten untuk render dan hit-testing.

#### Scenario: Kanvas aspect-fit mempertahankan posisi tengah dan bentuk lingkaran
- **WHEN** kit dengan `canvasAspect 2.0` dirender pada layar 2000x1000 dan 1600x1000
- **THEN** titik tengah kanvas selalu berada tepat di tengah layar, dan kendang berbentuk lingkaran tetap lingkaran sejati

#### Scenario: Sentuhan di margin luar kanvas diabaikan
- **WHEN** jari menyentuh area margin hitam di luar batas kanvas kendang
- **THEN** sentuhan diabaikan tanpa memicu visual feedback atau suara

### Requirement: Penanganan multi-touch terpusat dan aturan event sentuhan
Papan kendang SHALL menangani seluruh event sentuhan secara terpusat tanpa batasan jumlah jari. Suara dan visual feedback SHALL hanya dipicu oleh `ACTION_DOWN` dan `ACTION_POINTER_DOWN`. Event `ACTION_MOVE` SHALL tidak memicu suara baru saat jari bergeser melintasi batas zona. Event `ACTION_UP` dan `ACTION_CANCEL` SHALL tidak memicu suara dan tidak menimbulkan crash.

#### Scenario: Banyak jari diterima bersamaan
- **WHEN** pemain menyentuh layar dengan lebih dari satu jari secara bersamaan
- **THEN** setiap sentuhan diproses secara independen dan memicu suara serta visual feedback zona terkait

#### Scenario: Menggeser jari tidak memicu suara zona baru
- **WHEN** jari menyentuh zona A (ACTION_DOWN) lalu digeser ke zona B (ACTION_MOVE)
- **THEN** zona B tidak membunyikan suara saat jari melintas masuk

#### Scenario: Melepas jari tidak menimbulkan suara maupun crash
- **WHEN** pemain mengangkat jari (ACTION_UP atau ACTION_POINTER_UP) atau sentuhan dibatalkan (ACTION_CANCEL)
- **THEN** tidak ada suara baru yang dibunyikan dan aplikasi tidak crash

#### Scenario: Sentuhan di ruang kosong di luar kulit kendang diabaikan
- **WHEN** pemain menyentuh area kanvas di luar kulit kendang mana pun
- **THEN** tidak ada suara yang dihasilkan dan tidak ada visual feedback

### Requirement: Hit-testing zona akustik Bass (Model bass)
Kulit Bass SHALL dibagi menjadi 5 zona akustik: Area 1 (Bass-1) di dalam busur pembatas `C_div`; dan Area 2–5 berupa bilah bulan sabit di sisi luar `C_div` yang dibagi menjadi 4 bilah sama tinggi pada rentang vertikal oval Bass (band 0–3 dari atas ke bawah): band 0 = Bass-2 (atas), band 1 = Bass-3, band 2 = Bass-4, dan band 3 = Bass-5 (bawah).

#### Scenario: Sentuhan di zona utama (Area 1) terdeteksi sebagai Bass-1
- **WHEN** jari menyentuh area kulit Bass yang berada di dalam busur pembatas `C_div`
- **THEN** zona Bass-1 terdeteksi dan diteruskan ke pemicu suara dan visual feedback

#### Scenario: Sentuhan di bilah bulan sabit terdeteksi sesuai band vertikal
- **WHEN** jari menyentuh area bulan sabit kulit Bass pada band tertentu (0, 1, 2, atau 3)
- **THEN** sistem secara tepat mendeteksi zona Bass-2, Bass-3, Bass-4, atau Bass-5 sesuai urutan dari atas ke bawah

#### Scenario: Sentuhan di luar kulit Bass tidak membunyikan apa pun
- **WHEN** jari menyentuh area di luar batas oval kulit Bass
- **THEN** tidak ada zona Bass yang terdeteksi

### Requirement: Hit-testing zona akustik model split dan radial
Kulit kendang non-bass (Sedang, Kecil 2, dan kendang baru) SHALL mendukung pembagian zona akustik 1 s/d 4 zona: pembagian 2 zona horizontal default (`lower` dan `upper`), serta pembagian juring radial (1/2 lingkaran untuk 2 zona, 1/3 lingkaran untuk 3 zona, dan 1/4 lingkaran untuk 4 zona) dengan parameter putaran sudut rotasi `divider.angle` ($0^\circ - 360^\circ$). Visual highlight feedback SHALL merender potongan juring presisi (`drawArc` dengan `useCenter = true`) yang mengikuti orientasi sudut tersebut.

#### Scenario: Sentuhan di bagian bawah Sedang menghasilkan Sedang-1 (default 2 zona)
- **WHEN** jari menyentuh bagian bawah oval Sedang (`Y >= cy`) pada konfigurasi default 2 zona tanpa rotasi
- **THEN** zona Sedang-1 terdeteksi sebagai zona lower

#### Scenario: Sentuhan di bagian atas Sedang menghasilkan Sedang-2 (default 2 zona)
- **WHEN** jari menyentuh bagian atas oval Sedang (`Y < cy`) pada konfigurasi default 2 zona tanpa rotasi
- **THEN** zona Sedang-2 terdeteksi sebagai zona upper

#### Scenario: Sentuhan pada kendang radial dengan rotasi sudut terdeteksi akurat
- **WHEN** kendang dikonfigurasi dengan 2..4 zona radial dan sudut rotasi tertentu (misal garis vertikal $90^\circ$ memisahkan kiri dan kanan)
- **THEN** sentuhan diuji terhadap sudut polar $\text{atan2}$ relatif terhadap rotasi, menghasilkan zona juring yang tepat sasaran

#### Scenario: Sentuhan tepat pada garis tengah Y == cy masuk ke zona lower (default 2 zona)
- **WHEN** jari menyentuh tepat pada garis tengah koordinat vertikal kendang (`Y == cy`) pada mode 2 zona default
- **THEN** sentuhan dievaluasi masuk ke zona bawah (lower), yaitu Sedang-1 pada Sedang atau Kecil2-1 pada Kecil 2

### Requirement: Hit-testing zona akustik Kecil 1 (Model single)
Seluruh kulit Kecil 1 SHALL merupakan satu zona akustik tunggal (Kecil1-1).

#### Scenario: Sentuhan di mana pun pada kulit Kecil 1 membunyikan Kecil1-1
- **WHEN** jari menyentuh bagian mana pun di dalam kulit oval Kecil 1
- **THEN** zona Kecil1-1 terdeteksi dan diteruskan ke sistem

### Requirement: Prioritas hit-testing ditentukan oleh urutan array kit.json
Bila suatu titik sentuhan berada di area tumpang tindih antara dua kendang, kendang yang memiliki indeks lebih awal di dalam array `drums` pada `kit.json` SHALL diprioritaskan dan memenangkan hit-testing.

#### Scenario: Sentuhan di area tumpang tindih ditentukan oleh urutan array
- **WHEN** jari menyentuh titik yang berada di dalam batas Kecil 1 dan Sedang secara bersamaan
- **THEN** kendang yang berada lebih awal di array `drums` (Kecil 1) yang terdeteksi

### Requirement: Satu sumber kebenaran geometri untuk render dan hit-testing
Komponen rendering visual dan pendeteksi sentuhan (hit-testing) SHALL menggunakan data geometri dari kit efektif yang sama (kit dasar yang telah digabungkan dengan layout override pemain).

#### Scenario: Papan merender dan merespons sentuhan di posisi baru setelah override
- **WHEN** pemain mengubah posisi kendang dan menyimpan layout override
- **THEN** kendang digambar di posisi baru, dan sentuhan di posisi baru tersebut yang mendeteksi zona akustik

### Requirement: Mode operasi papan (Bermain dan Edit Tata Letak)
Papan kendang SHALL mendukung dua mode operasi: mode bermain dan mode edit tata letak. Pada mode edit tata letak, sentuhan digunakan untuk memanipulasi posisi atau ukuran kendang dan SHALL tidak membunyikan suara apa pun. Mode Developer SHALL memakai semantik yang sama melalui mode UJI (bermain) dan EDIT (edit tata letak).

#### Scenario: Mode bermain membunyikan suara
- **WHEN** papan berada di mode bermain dan zona bersuara diketuk
- **THEN** suara zona tersebut berbunyi

#### Scenario: Mode edit tata letak tidak membunyikan suara
- **WHEN** papan berada di mode edit tata letak dan kendang disentuh untuk digeser
- **THEN** posisi kendang berpindah mengikuti jari tanpa mengeluarkan suara audio

#### Scenario: Mode UJI dan EDIT di Mode Developer mengikuti semantik yang sama
- **WHEN** papan berada di Mode Developer dengan mode UJI atau EDIT
- **THEN** mode UJI membunyikan zona seperti mode bermain, sedangkan mode EDIT memilih atau menggeser kendang tanpa membunyikan suara dan tanpa mengirim trigger audio

### Requirement: Visual feedback presisi per bentuk zona
Papan kendang SHALL menampilkan highlight overlay visual sesaat (~100 ms) yang menutupi bentuk geometri spesifik dari zona akustik yang terdeteksi (bukan seluruh kulit kendang). Visual feedback ini SHALL menggunakan hasil hit-testing yang sama dengan pemicu suara, dan SHALL tetap tampil untuk zona tanpa suara (`sound: null`).

#### Scenario: Highlight hanya menutupi zona spesifik yang disentuh
- **WHEN** jari menyentuh bilah bulan sabit Bass-2
- **THEN** highlight putih semi-transparan muncul tepat di area bilah bulan sabit Bass-2 dan memudar, tanpa menutupi zona Bass lainnya

#### Scenario: Zona tanpa suara tetap menampilkan visual feedback
- **WHEN** pemain menyentuh zona yang belum memiliki file suara (sound null)
- **THEN** highlight visual pada zona tersebut tetap menyala sesaat; tidak ada suara dan tidak crash

### Requirement: Pengiriman trigger hit-test ke audio engine
Setiap sentuhan yang berhasil mendeteksi zona akustik pada mode bermain SHALL meneruskan event trigger ke audio engine melalui antrean lock-free. Bila zona berstatus tanpa suara (`sound: null`), event suara SHALL dilewati secara aman tanpa memicu pemutaran audio.

#### Scenario: Trigger dikirim ke audio engine untuk zona bersuara
- **WHEN** zona yang memiliki suara valid terdeteksi saat ketukan
- **THEN** event `(slotIndex, gain)` dikirimkan ke antrean ring buffer audio engine C++

#### Scenario: Zona tanpa suara tidak mengirim trigger ke audio engine
- **WHEN** zona tanpa suara (`sound: null`) terdeteksi saat ketukan
- **THEN** event dilewati tanpa mengirim trigger audio ke C++

### Requirement: Header navigasi Parent-Child dan Halaman Pilih Kit layar penuh
Tampilan utama Activity SHALL memiliki Header di bagian atas (~44–48dp) dengan pola menu Parent-Child di sisi kiri (`☰ Menu` disertai label `Kit: <Nama>`), slot iklan di tengah (tersembunyi pada MVP), dan tombol `Mode Developer` di sisi kanan (khusus build debug). Mengetuk tombol parent `☰ Menu` SHALL menampilkan dua opsi child: `Pilih Kit` (membuka Halaman Pilih Kit layar penuh) dan `Sesuaikan` (membuka Halaman Sesuaikan layar penuh). Pada Halaman Pilih Kit layar penuh, setiap baris kit SHALL hanya menampilkan nama kit di sisi kiri dan status `Aktif` atau tombol `Pakai` di sisi kanan (tanpa istilah teknis developer seperti "Kit Bawaan", "Kit Tersimpan", atau jumlah file suara), dan hanya 1 kit yang dapat berstatus `Aktif`.

#### Scenario: Menu Parent membuka opsi Pilih Kit dan Sesuaikan
- **WHEN** pemain mengetuk tombol `☰ Menu` di pojok kiri atas
- **THEN** aplikasi menampilkan opsi child `Pilih Kit` dan `Sesuaikan`

#### Scenario: Halaman Pilih Kit menampilkan nama kit dan tombol Pakai
- **WHEN** pemain membuka `Pilih Kit` dari menu
- **THEN** tampil halaman penuh berisi daftar nama kit; kit yang sedang digunakan menampilkan badge `Aktif` dan kit lainnya menampilkan tombol `Pakai`

#### Scenario: Menekan tombol Pakai mengaktifkan kit dan menggambar ulang papan
- **WHEN** pemain menekan tombol `Pakai` pada salah satu kit di Halaman Pilih Kit
- **THEN** kit tersebut menjadi satu-satunya kit `Aktif`, data kit dimuat, dan papan kendang digambar ulang sesuai konfigurasi kit yang baru dipilih

### Requirement: Orientasi layar terkunci landscape dan mode Immersive Sticky
Aplikasi SHALL selalu berjalan dalam orientasi landscape dan menggunakan mode Immersive Sticky: bar sistem tersembunyi secara default; bila bar muncul sementara akibat swipe dari tepi layar, sistem SHALL otomatis menyembunyikannya kembali; serta sentuhan biasa di dekat tepi layar SHALL tidak memunculkan bar sistem.

#### Scenario: Bar sistem tersembunyi kembali setelah swipe tepi
- **WHEN** bar sistem muncul sesaat karena pemain melakukan swipe dari tepi layar
- **THEN** bar sistem otomatis tersembunyi kembali setelah beberapa detik tanpa mengganggu sesi bermain

#### Scenario: Memutar perangkat tidak mengubah orientasi
- **WHEN** pemain memutar perangkat fisik ke posisi portrait
- **THEN** tampilan aplikasi tetap terkunci pada posisi landscape

### Requirement: Layar tidak mati selama aplikasi di depan
Aplikasi SHALL mengaktifkan flag `FLAG_KEEP_SCREEN_ON` sehingga layar perangkat tetap menyala penuh selama aplikasi aktif di foreground.

#### Scenario: Layar tidak redup saat jeda bermain
- **WHEN** pemain tidak menyentuh layar selama beberapa waktu saat aplikasi di depan
- **THEN** layar tidak redup atau mati otomatis

### Requirement: Pengecualian gestur sistem di Android 10+
Papan kendang SHALL mendaftarkan System Gesture Exclusion Rects pada perangkat dengan Android 10 (API 29) ke atas di sepanjang tepi kiri dan kanan layar, agar sentuhan rolling cepat tidak memicu gestur navigasi back sistem.

#### Scenario: Ketukan di tepi layar tidak memicu gestur back
- **WHEN** pemain melakukan ketukan cepat di dekat tepi kiri atau kanan layar pada perangkat Android 10+
- **THEN** ketukan diterima oleh kendang dan tidak memicu gestur navigasi back Android

### Requirement: Overlay garis batas zona di build debug
Khusus pada APK debug, papan kendang SHALL menyediakan overlay garis panduan batas zona akustik (termasuk kurva busur `C_div` dan garis split horizontal) untuk membantu kalibrasi; overlay ini SHALL tidak hadir pada APK release.

#### Scenario: Garis batas zona dapat ditampilkan pada build debug
- **WHEN** aplikasi dijalankan pada build debug dengan opsi overlay aktif
- **THEN** garis batas zona akustik tergambar di atas kulit kendang sebagai panduan visual

#### Scenario: Overlay panduan tidak ada pada build release
- **WHEN** aplikasi dijalankan pada build release
- **THEN** kode dan tampilan overlay batas zona tidak tersedia di dalam aplikasi

### Requirement: Logika hit-testing dapat diuji tanpa perangkat dengan variasi kasus lengkap
Logika hit-testing zona akustik SHALL diimplementasikan di modul `:kit-core` (Kotlin/JVM murni) dan unit test suite SHALL mencakup seluruh variasi kasus uji: satu titik di tiap zona, titik di tepi oval, titik tepat pada garis tengah `Y == cy` (masuk lower), titik tepat pada tepi lingkaran `C_div`, titik di area tumpang tindih dua kendang (menang sesuai urutan array), dan titik di ruang kosong di luar semua kendang.

#### Scenario: Unit test hit-testing mengevaluasi seluruh kasus batas
- **WHEN** GitHub Actions menjalankan unit test suite modul `:kit-core`
- **THEN** tes hit-testing mengevaluasi seluruh variasi titik batas (termasuk garis tengah, tepi lingkaran divider, dan tumpang tindih kendang) dan lulus 100%
