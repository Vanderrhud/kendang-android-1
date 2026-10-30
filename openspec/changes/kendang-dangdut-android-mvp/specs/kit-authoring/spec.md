# Spec Delta

## Purpose

Menyediakan alat penyusun kit yang hanya tersedia di build debug, sebagai instrumen utama developer/pemilik produk untuk merakit kit kendang secara mandiri: memasangkan sampel rekaman WAV asli kendang dari penyimpanan HP ke tiap zona, mengatur posisi, ukuran, busur Bass, dan gain, menguji atau menyunting papan dalam mode UJI/EDIT, serta menyimpan, mengekspor, dan mengimpor kit dalam format paket `.vrkendang` — semua tanpa build ulang.

## ADDED Requirements

### Requirement: Mode Developer hanya tersedia di build debug
Mode Developer (alat penyusun kit) SHALL hanya dapat diakses pada APK debug dan tidak hadir sama sekali pada APK release. Tombol akses ke Mode Developer muncul di menu Pengaturan (⚙) khusus pada build debug.

#### Scenario: Tombol Mode Developer tampil di build debug
- **WHEN** pengguna membuka menu Pengaturan di build debug
- **THEN** terdapat opsi untuk membuka Mode Developer

#### Scenario: Mode Developer tidak ada di build release
- **WHEN** pengguna membuka menu Pengaturan di build release
- **THEN** tidak ada opsi Mode Developer di menu; kode Mode Developer tidak terdapat di APK

### Requirement: Buat kit baru dengan zona awal tanpa suara
Developer SHALL dapat membuat kit baru di Mode Developer dengan mengisi nama dan `kitId`. `kitId` divalidasi mengikuti aturan `kit-data`, tidak boleh sama dengan `kitId` kit bawaan, dan tidak dapat diubah setelah kit dibuat; nama kit boleh diubah. Tata letak awal diambil dari template (usulan: New Metro) dengan seluruh `sound` null, lalu aplikasi meminta lokasi file `.vrkendang` sebagai file sumber.

#### Scenario: Kit baru berhasil dibuat dengan zona kosong
- **WHEN** developer memilih opsi "Buat Kit Baru", lalu mengisi nama dan `kitId` yang valid
- **THEN** kit baru terbentuk dari template dengan seluruh zona berstatus `sound` null, aplikasi meminta lokasi file `.vrkendang`, dan file yang dipilih menjadi file sumber kit

#### Scenario: kitId duplikat atau tidak valid ditolak
- **WHEN** developer mengisi `kitId` yang sama dengan `kitId` kit bawaan atau `kitId` yang melanggar aturan `kit-data`
- **THEN** pembuatan kit ditolak dengan pesan galat yang jelas dan kit baru tidak dibuat

#### Scenario: kitId tidak dapat diubah setelah kit dibuat
- **WHEN** developer mengubah nama kit yang sudah dibuat
- **THEN** nama kit berubah dan `kitId` tetap sama

### Requirement: Buka kit sebagai salinan kerja
Developer SHALL dapat membuka file `.vrkendang` dari penyimpanan HP atau kit bawaan APK sebagai salinan kerja di `filesDir/dev-kit/` (bukan `cacheDir`); kit bawaan tidak pernah diubah. Membuka `.vrkendang` mengekstrak ke direktori sementara, memvalidasi, lalu baru menggantikan salinan kerja. Bila gagal, aplikasi menampilkan pesan galat dan salinan kerja saat ini tidak berubah; membatalkan pemilih file juga tidak mengubah apa pun.

#### Scenario: Developer membuka file `.vrkendang` dari penyimpanan HP
- **WHEN** developer memilih "Buka Kit" dan memilih file `.vrkendang` yang valid dari pemilih file sistem
- **THEN** file diekstrak ke direktori sementara, divalidasi, dimuat ke `filesDir/dev-kit/` sebagai salinan kerja, dan file tersebut menjadi file sumber kit yang siap diedit

#### Scenario: Developer membuka kit bawaan APK sebagai salinan kerja
- **WHEN** developer memilih kit bawaan dari daftar dan memilih "Buka sebagai Salinan Kerja"
- **THEN** kit bawaan diekstrak ke `filesDir/dev-kit/`; kit asli di dalam APK tidak tersentuh

#### Scenario: Paket tidak valid tidak mengubah salinan kerja
- **WHEN** developer memilih file `.vrkendang` dengan ZIP rusak, entri path traversal, ukuran melebihi batas, atau `kit.json` tidak valid
- **THEN** aplikasi menampilkan pesan galat yang jelas dan salinan kerja yang sedang terbuka tetap tidak berubah

#### Scenario: Membatalkan pemilih file tidak mengubah apa pun
- **WHEN** developer membuka pemilih file lalu membatalkannya
- **THEN** tidak ada file yang diekstrak dan salinan kerja saat ini tetap sama

#### Scenario: Salinan kerja bertahan dari pembersihan cache sistem
- **WHEN** sistem Android membersihkan direktori cache aplikasi
- **THEN** salinan kerja developer di `filesDir/dev-kit/` tetap tersedia

### Requirement: Pasang file suara WAV dari penyimpanan HP per zona
Developer SHALL dapat memasangkan WAV dari penyimpanan HP ke zona mana pun di kit yang sedang diedit. File disalin sebagai `sounds/<zoneId>.wav` (menggantikan file lama), `kit.json` diperbarui, dan batas ukuran per WAV dari `kit-data` berlaku. File diurai `WavParser` dan PCM-nya langsung dimuat ke audio engine agar dapat langsung diuji; opsi "Kosongkan suara" mengembalikan `sound` ke null, dan pembatalan tidak mengubah apa pun.

#### Scenario: Developer memilih file WAV dari HP dan memasangkannya ke zona
- **WHEN** developer memilih zona (misalnya Bass-1) lalu menekan "Pilih Suara (WAV)" dan memilih file dari pemilih file sistem
- **THEN** file WAV disalin ke direktori kerja sebagai `sounds/<zoneId>.wav`, `kit.json` diperbarui, file diurai, langsung di-load ke audio engine, dan zona tersebut langsung berbunyi saat diketuk

#### Scenario: File non-WAV atau WAV selain PCM 16-bit mono ditolak dengan pesan jelas
- **WHEN** developer memilih file yang bukan WAV PCM 16-bit mono
- **THEN** file ditolak dan aplikasi menampilkan pesan galat yang jelas: "Format harus WAV PCM 16-bit mono"; zona tetap seperti sebelumnya

#### Scenario: WAV melebihi batas ukuran ditolak
- **WHEN** developer memilih file WAV yang melebihi batas ukuran per WAV dari `kit-data`
- **THEN** file ditolak dengan pesan galat yang jelas dan sound zona tetap seperti sebelumnya

#### Scenario: Mengosongkan suara zona
- **WHEN** developer memilih "Kosongkan suara" pada zona yang memiliki sound
- **THEN** nilai `sound` zona tersebut menjadi null di `kit.json` dan zona tidak lagi membunyikan file WAV lama

#### Scenario: Membatalkan pemilih file suara tidak mengubah apa pun
- **WHEN** developer membuka pemilih file WAV lalu membatalkannya
- **THEN** sound dan `kit.json` zona tersebut tetap seperti sebelumnya

#### Scenario: Suara yang baru dipasang langsung bisa diuji
- **WHEN** developer telah berhasil memasangkan file WAV ke zona
- **THEN** developer dapat langsung mengetuk zona tersebut di papan dalam mode UJI dan mendengar suara yang baru dipasang, tanpa perlu menyimpan atau restart

### Requirement: Pemuatan sample saat stream berjalan tidak mengganggu audio
Saat stream audio sedang berjalan, pemuatan atau penggantian sample SHALL tidak memblokir atau menyebabkan glitch pada audio callback. File SHALL diurai dan di-resample di luar thread audio, lalu sample SHALL ditukar secara atomik. Bila zona sedang berbunyi, suara lama SHALL diselesaikan atau dihentikan secara mulus.

#### Scenario: Memasang sample baru saat zona lain sedang berbunyi
- **WHEN** developer memasang atau mengganti WAV saat stream audio sedang berjalan dan zona lain sedang berbunyi
- **THEN** file diurai dan di-resample di luar thread audio, sample ditukar secara atomik, dan tidak terjadi glitch atau blokir pada audio callback

#### Scenario: Mengganti sample zona yang sedang berbunyi
- **WHEN** developer mengganti WAV pada zona yang sedang membunyikan suara lama
- **THEN** suara lama diselesaikan atau dihentikan secara mulus tanpa bunyi pop

### Requirement: Dua mode papan UJI dan EDIT di Mode Developer
Mode Developer SHALL menyediakan dua mode papan: UJI dan EDIT. Pada mode UJI, sentuhan SHALL membunyikan zona seperti pemain. Pada mode EDIT, sentuhan SHALL memilih atau menggeser kendang dan TIDAK membunyikan suara (selaras dengan spesifikasi `kendang-board` dan `calibration-mode`). Dari mode EDIT, developer SHALL dapat memilih zona lewat panel daftar zona atau dengan menyentuhnya, lalu mengatur WAV, gain, dan parameter divider dari panel. Zona yang dipilih SHALL diberi tanda visual.

#### Scenario: Mode UJI membunyikan zona seperti pemain
- **WHEN** papan Mode Developer berada di mode UJI dan developer menyentuh zona yang memiliki sound
- **THEN** zona tersebut membunyikan suara seperti pada mode bermain pemain

#### Scenario: Mode EDIT memilih kendang tanpa bunyi
- **WHEN** papan Mode Developer berada di mode EDIT dan developer menyentuh kendang
- **THEN** kendang atau zona tersebut terpilih dengan tanda visual, posisi dapat digeser, dan tidak ada suara yang dibunyikan atau trigger audio yang dikirim

#### Scenario: Memilih zona lewat panel daftar zona
- **WHEN** developer memilih salah satu zona dari panel daftar zona di mode EDIT
- **THEN** zona tersebut ditandai sebagai zona terpilih dan panel menampilkan kontrol WAV, gain, dan divider untuk zona itu

### Requirement: Ubah posisi dan ukuran kendang secara interaktif
Developer SHALL dapat menggeser posisi setiap kendang dan mengubah ukurannya (via slider skala) secara interaktif di layar dalam Mode Developer pada mode EDIT, sama seperti di Mode Kalibrasi pemain dan tanpa membunyikan suara. Semua perubahan tata letak SHALL ditulis ke `kit.json` di salinan kerja dan ke file `.vrkendang` saat disimpan, bukan ke layout override pemain.

#### Scenario: Menggeser kendang mengubah posisinya secara real-time
- **WHEN** developer menyentuh dan menggeser kendang di mode EDIT
- **THEN** posisi kendang berubah real-time di layar tanpa membunyikan suara

#### Scenario: Slider skala mengubah ukuran kendang
- **WHEN** developer menggerakkan slider ukuran untuk kendang yang dipilih
- **THEN** ukuran kendang berubah real-time di layar dan nilai `rx`/`ry` di `kit.json` ikut diperbarui

### Requirement: Ubah gain (volume) per zona
Developer SHALL dapat mengatur nilai gain (volume) per zona di rentang 0.0–2.0 menggunakan kontrol yang tersedia (slider atau input angka). Perubahan gain SHALL berlaku pada ketukan berikutnya tanpa memuat ulang sample.

#### Scenario: Gain zona dapat diubah dan langsung terdengar
- **WHEN** developer mengubah nilai gain suatu zona
- **THEN** perubahan volume terdengar saat zona diketuk berikutnya tanpa memuat ulang file sample

### Requirement: Ubah parameter busur pembatas Bass (divider)
Developer SHALL dapat menyesuaikan parameter busur pembatas Bass (`dx`, `dy`, `r`) secara interaktif untuk mengkalibrasi batas antara zona utama dan zona bulan sabit kendang Bass. Nilai divider SHALL mengikuti rentang validasi `kit-data`: `dx` dan `dy` di rentang -3.0 sampai 3.0, serta `r` > 0 dan ≤ 3.0. Nilai di luar rentang SHALL ditolak dengan pesan jelas.

#### Scenario: Perubahan parameter divider langsung terlihat di papan
- **WHEN** developer mengubah parameter divider Bass
- **THEN** garis panduan busur pembatas di layar berubah mengikuti nilai baru secara real-time

#### Scenario: Parameter divider di luar rentang ditolak
- **WHEN** developer memasukkan nilai `dx`/`dy` di luar -3.0 sampai 3.0 atau `r` ≤ 0 atau > 3.0
- **THEN** perubahan ditolak dengan pesan galat yang jelas dan nilai divider sebelumnya tetap dipakai

### Requirement: Simpan kit ke file sumber
Developer SHALL dapat menyimpan seluruh isi kit yang sedang diedit menjadi satu file `.vrkendang`. Bila kit memiliki file sumber, "Simpan" menimpa file itu lewat URI berizin persisten tanpa dialog; bila belum ada file sumber, "Simpan" membuka pemilih lokasi dan file yang dipilih menjadi file sumber. Penyimpanan SHALL memakai pengaman validasi paket dan tidak membuat file `.bak`.

#### Scenario: Simpan ke file sumber menimpa tanpa dialog
- **WHEN** kit memiliki file sumber dan developer menekan "Simpan"
- **THEN** paket disusun penuh di cache, dibaca ulang dan divalidasi, lalu ditulis menimpa file sumber lewat URI yang izinnya dipersistenkan tanpa membuka pemilih lokasi dan tanpa membuat file `.bak`

#### Scenario: Simpan tanpa file sumber membuka pemilih lokasi
- **WHEN** kit belum memiliki file sumber (kit baru atau salinan kerja dari kit bawaan) dan developer menekan "Simpan"
- **THEN** aplikasi membuka pemilih lokasi, menulis paket ke file yang dipilih, dan file itu menjadi file sumber kit

#### Scenario: Pengaman penulisan gagal tidak merusak salinan kerja
- **WHEN** validasi paket atau penulisan ke URI gagal
- **THEN** aplikasi menampilkan pesan galat, salinan kerja tidak berubah, dan developer ditawari Simpan Sebagai ke lokasi lain

#### Scenario: Izin URI hilang otomatis jatuh ke Simpan Sebagai
- **WHEN** izin URI file sumber hilang atau file sumber tidak dapat dibaca
- **THEN** aplikasi otomatis membuka alur Simpan Sebagai tanpa mengubah salinan kerja

#### Scenario: Paket hanya berisi WAV yang dirujuk kit.json
- **WHEN** developer menyimpan kit yang direktori kerjanya masih memiliki file WAV yatim
- **THEN** paket `.vrkendang` hanya berisi `kit.json` dan file `sounds/<zoneId>.wav` yang dirujuk oleh `kit.json`, tanpa file WAV yatim

### Requirement: Simpan kit sebagai file baru
"Simpan Sebagai" SHALL selalu meminta lokasi baru dan menulis file baru, walaupun kit sudah memiliki file sumber; file baru itu SHALL menjadi file sumber kit. Simpan Sebagai memakai pengaman penulisan dan aturan isi paket yang sama dengan Simpan. Membatalkan pemilih lokasi berarti tidak ada perubahan.

#### Scenario: Simpan Sebagai selalu meminta lokasi baru
- **WHEN** developer memilih "Simpan Sebagai" pada kit yang sudah memiliki file sumber
- **THEN** pemilih lokasi selalu terbuka, file baru ditulis, dan file baru tersebut menjadi file sumber kit

#### Scenario: Membatalkan Simpan Sebagai tidak mengubah file sumber
- **WHEN** developer membuka pemilih lokasi Simpan Sebagai lalu membatalkannya
- **THEN** file sumber lama dan salinan kerja tetap tidak berubah

### Requirement: Versi kit naik setiap penyimpanan
Setiap operasi simpan (Simpan maupun Simpan Sebagai) SHALL menaikkan nilai `version` di `kit.json` tepat satu tingkat. Tidak ada perilaku lain di MVP yang bergantung pada nilai `version`.

#### Scenario: Versi kit bertambah satu setiap simpan
- **WHEN** developer menyimpan kit
- **THEN** nilai `version` di `kit.json` bertambah tepat satu dari nilai sebelumnya dan tidak ada perilaku lain yang bergantung pada nilai tersebut

### Requirement: Paket .vrkendang dapat menjadi kit bawaan APK yang valid
Paket `.vrkendang` yang valid SHALL dapat diekstrak apa adanya ke `assets/kits/<kitId>/` dan lolos validasi kit bawaan, dengan `kit.json` di akar paket dan file WAV di `sounds/`. Langkah ekstrak, commit, dan push SHALL didokumentasikan di README atau `docs/rilis-kit.md` serta tasks.md, bukan sebagai fitur aplikasi.

#### Scenario: Paket valid diekstrak menjadi kit bawaan
- **WHEN** isi paket `.vrkendang` yang valid diekstrak apa adanya ke `assets/kits/<kitId>/`
- **THEN** `kit.json` berada di akar direktori kit, file WAV berada di `sounds/`, dan kit tersebut lolos validasi kit bawaan

#### Scenario: Alur rilis kit bawaan terdokumentasi
- **WHEN** developer mengikuti dokumentasi rilis kit (README atau `docs/rilis-kit.md`) dan tasks.md
- **THEN** langkah ekstrak, commit, dan push dapat dilakukan tanpa memerlukan fitur ekspor tambahan di aplikasi

### Requirement: Indikator perubahan belum disimpan
Mode Developer SHALL menampilkan indikator bahwa salinan kerja memiliki perubahan yang belum disimpan. Keluar dari Mode Developer, membuka kit lain, atau menutup aplikasi dengan perubahan belum disimpan SHALL meminta konfirmasi untuk menyimpan atau membuang perubahan.

#### Scenario: Keluar dari Mode Developer dengan perubahan belum disimpan
- **WHEN** developer mencoba keluar dari Mode Developer saat ada perubahan belum disimpan
- **THEN** aplikasi menampilkan konfirmasi untuk menyimpan atau membuang perubahan

#### Scenario: Membuka kit lain dengan perubahan belum disimpan
- **WHEN** developer mencoba membuka kit lain saat salinan kerja memiliki perubahan belum disimpan
- **THEN** aplikasi meminta konfirmasi simpan atau buang sebelum menggantikan salinan kerja

#### Scenario: Menutup aplikasi dengan perubahan belum disimpan
- **WHEN** developer menutup aplikasi saat ada perubahan belum disimpan di Mode Developer
- **THEN** aplikasi meminta konfirmasi untuk menyimpan atau membuang perubahan

#### Scenario: Indikator perubahan ditampilkan
- **WHEN** salinan kerja memiliki perubahan yang belum disimpan
- **THEN** Mode Developer menampilkan indikator perubahan yang jelas

### Requirement: Batas dengan layout override pemain
Layout override pemain SHALL tidak diterapkan pada salinan kerja di Mode Developer. Semua perubahan tata letak di Mode Developer SHALL ditulis ke `kit.json` di salinan kerja dan ke file `.vrkendang` saat disimpan (selaras dengan spesifikasi `calibration-mode`), bukan ke layout override pemain.

#### Scenario: Mode Developer memuat kit tanpa override pemain
- **WHEN** developer membuka Mode Developer untuk kit yang memiliki layout override pemain
- **THEN** salinan kerja dimuat dari data asli kit tanpa menerapkan layout override pemain

#### Scenario: Perubahan tata letak ditulis ke kit.json dan paket
- **WHEN** developer mengubah tata letak di Mode Developer lalu menyimpan kit
- **THEN** perubahan ditulis ke `kit.json` salinan kerja dan file `.vrkendang`, dan layout override pemain tidak diubah

### Requirement: Cakupan penyuntingan struktur kit di MVP
Struktur kit SHALL tetap 4 kendang dari template pada MVP. Developer SHALL dapat mengubah nama kit, `center`, radius (`rx`, `ry`), parameter divider Bass, sound dan gain tiap zona, serta urutan array `drums` (prioritas hit-test). Menambah atau menghapus kendang dan zona dicatat sebagai non-goal MVP dan pekerjaan lanjutan.

#### Scenario: Developer mengubah properti yang termasuk cakupan MVP
- **WHEN** developer mengubah nama kit, posisi, radius, divider Bass, sound/gain zona, atau urutan array `drums`
- **THEN** perubahan diterapkan pada salinan kerja dan dapat disimpan ke paket `.vrkendang`

#### Scenario: Menambah atau menghapus kendang/zona tidak tersedia di MVP
- **WHEN** developer mencari aksi untuk menambah atau menghapus kendang/zona di Mode Developer MVP
- **THEN** tidak ada aksi tersebut; jumlah kendang dan zona tetap mengikuti template

### Requirement: Semua perubahan salinan kerja tetap valid
Setiap perubahan pada salinan kerja SHALL menghasilkan `kit.json` yang lolos `KitValidator`. Kontrol (slider, input) SHALL membatasi nilai sesuai aturan validasi (gain 0.0–2.0, batas skala dan posisi sama dengan `calibration-mode`), dan perubahan yang tidak valid SHALL ditolak dengan pesan yang jelas.

#### Scenario: Nilai gain di luar rentang ditolak atau dijepit
- **WHEN** developer memasukkan nilai gain di bawah 0.0 atau di atas 2.0
- **THEN** kontrol membatasi nilai ke rentang 0.0–2.0 atau menolak input dengan pesan jelas, dan `kit.json` tetap lolos `KitValidator`

#### Scenario: Posisi dan ukuran mengikuti batas kalibrasi
- **WHEN** developer menggeser atau mengubah ukuran kendang sampai mendekati tepi kanvas
- **THEN** posisi dan skala dibatasi dengan aturan yang sama seperti `calibration-mode` sehingga oval kendang tetap di dalam kanvas dan `kit.json` lolos `KitValidator`

#### Scenario: Perubahan tidak valid tidak merusak kit
- **WHEN** suatu perubahan akan menghasilkan `kit.json` yang tidak valid
- **THEN** perubahan ditolak dengan pesan jelas dan salinan kerja tetap pada kondisi valid sebelumnya

### Requirement: Logika penyusunan kit dapat diuji tanpa perangkat
Modul `:kit-core` (Kotlin/JVM murni tanpa `android.*`) SHALL menyediakan `KitPackageWriter` untuk menyusun paket `.vrkendang` dan `KitFactory` untuk membuat kit baru dari template, beserta JVM unit test yang berjalan di CI.

#### Scenario: Tulis lalu baca ulang paket menghasilkan kit identik
- **WHEN** unit test menulis kit dengan `KitPackageWriter` lalu membacanya kembali dengan `KitPackageReader`
- **THEN** kit hasil pembacaan identik dengan kit yang ditulis

#### Scenario: Kit baru dari template lolos validasi dengan semua sound null
- **WHEN** unit test membuat kit baru dengan `KitFactory`
- **THEN** kit tersebut lolos `KitValidator` dengan seluruh zona berstatus `sound` null

#### Scenario: Mengganti atau mengosongkan sound memperbarui kit.json dan daftar file paket
- **WHEN** unit test mengganti atau mengosongkan sound pada suatu zona lalu menyusun paket
- **THEN** `kit.json` dan daftar file WAV di paket sesuai dengan perubahan tersebut

#### Scenario: WAV yatim tidak ikut paket
- **WHEN** unit test menyusun paket dari direktori kerja yang memiliki file WAV yang tidak dirujuk `kit.json`
- **THEN** file WAV yatim tersebut tidak disertakan dalam paket

#### Scenario: kitId yang sama dengan kit bawaan ditolak
- **WHEN** unit test mencoba membuat kit baru dengan `kitId` yang sama dengan kit bawaan
- **THEN** `KitFactory` menolak pembuatan kit tersebut dengan pesan galat yang jelas
