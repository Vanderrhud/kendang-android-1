# Spec Delta

## Purpose

Menyediakan alat penyusun kit yang hanya tersedia di build debug, sebagai instrumen utama developer/pemilik produk untuk merakit kit kendang secara mandiri: memasangkan sampel rekaman WAV asli kendang dari penyimpanan HP ke tiap zona, mengatur posisi, ukuran, busur Bass, dan gain, menguji atau menyunting papan dalam mode UJI/EDIT, serta menyimpan, mengekspor, dan mengimpor kit dalam format paket `.vrkendang` — semua tanpa build ulang.

## ADDED Requirements

### Requirement: Mode Developer hanya tersedia di build debug
Mode Developer (alat penyusun kit) SHALL hanya dapat diakses pada APK debug dan tidak hadir sama sekali pada APK release. Tombol `Mode Developer` muncul di sisi kanan Header utama khusus pada build debug dan membuka Halaman Mode Developer layar penuh.

#### Scenario: Tombol Mode Developer tampil di build debug
- **WHEN** aplikasi dijalankan pada build debug
- **THEN** tombol `Mode Developer` tampil di sisi kanan Header utama dan membuka Halaman Mode Developer layar penuh saat diketuk

#### Scenario: Mode Developer tidak ada di build release
- **WHEN** aplikasi dijalankan pada build release
- **THEN** tombol `Mode Developer` tidak tampil di Header dan kode Mode Developer tidak terdapat di APK

### Requirement: Pola pemilihan per kendang di Mode Developer
Mode Developer SHALL menyajikan antarmuka dua panel: Panel Kiri (`PILIH KENDANG`) yang menampilkan daftar 4 kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`) beserta pratinjau visual papan, dan Panel Kanan (`KONFIGURASI: <NAMA KENDANG>`) yang hanya menampilkan daftar zona, pengaturan sampel WAV + volume (`gain`), dan parameter khusus milik kendang yang sedang dipilih di Panel Kiri. Tombol pengubah urutan prioritas kendang (`Naik`/`Turun`) SHALL tidak ditampilkan di UI.

#### Scenario: Memilih kendang di panel kiri memperbarui panel kanan
- **WHEN** developer memilih kendang `Bass` di panel kiri
- **THEN** panel kanan menampilkan khusus konfigurasi `Bass`: chip zona `bass-1` s/d `bass-5`, kontrol sampel suara zona aktif, dan slider parameter busur Bass (`dx`, `dy`, `r`)

#### Scenario: Memilih kendang selain Bass menyembunyikan parameter busur Bass
- **WHEN** developer memilih kendang `Kecil 1`, `Sedang`, atau `Kecil 2` di panel kiri
- **THEN** panel kanan menampilkan chip zona milik kendang tersebut (`kecil1-1`, `sedang-1`/`sedang-2`, atau `kecil2-1`/`kecil2-2`) beserta kontrol sampel suara dan gain, tanpa menampilkan parameter busur Bass

### Requirement: Simpan sebagai Kit Baru di dalam aplikasi (`+ Kit Baru`)
Developer SHALL dapat menyimpan konfigurasi yang sedang diedit menjadi kit baru di penyimpanan internal aplikasi (`filesDir/kits/<kitId>/`) melalui tombol `+ Kit Baru` dengan mengisi nama kit baru dan properti terkait (`kitId` divalidasi atau dibentuk otomatis sesuai aturan `kit-data` dan tidak boleh menimpa `kitId` bawaan), tanpa membuka pemilih file sistem HP. Kit baru yang disimpan SHALL langsung tersedia di Halaman Pilih Kit pemain.

#### Scenario: Menyimpan konfigurasi saat ini sebagai kit baru
- **WHEN** developer menekan tombol `+ Kit Baru` dan mengisi nama kit baru yang valid
- **THEN** salinan kerja saat ini disimpan sebagai kit baru di `filesDir/kits/<kitId>/`, langsung menjadi kit yang sedang diedit, dan muncul di daftar Halaman Pilih Kit pemain

#### Scenario: kitId duplikat atau tidak valid ditolak
- **WHEN** developer mengisi `kitId` yang sama dengan `kitId` bawaan atau melanggar aturan `kit-data`
- **THEN** penyimpanan kit baru ditolak dengan pesan galat yang jelas

### Requirement: Buka kit sebagai salinan kerja dan Impor `.vrkendang`
Saat Mode Developer dibuka, aplikasi SHALL memuat kit yang sedang aktif sebagai salinan kerja di `filesDir/dev-kit/` (bukan `cacheDir`); kit bawaan APK tidak pernah diubah secara langsung. Developer juga SHALL dapat mengimpor file paket `.vrkendang` dari penyimpanan HP melalui tombol `Impor` (`ACTION_OPEN_DOCUMENT`), yang mengekstrak ke direktori sementara, memvalidasi, lalu menggantikan salinan kerja. Bila gagal, aplikasi menampilkan pesan galat dan salinan kerja saat ini tidak berubah.

#### Scenario: Developer mengimpor file `.vrkendang` dari penyimpanan HP
- **WHEN** developer menekan tombol `Impor` dan memilih file `.vrkendang` yang valid dari pemilih file sistem
- **THEN** file diekstrak ke direktori sementara, divalidasi, dan dimuat ke `filesDir/dev-kit/` sebagai salinan kerja yang siap diedit

#### Scenario: Paket tidak valid tidak mengubah salinan kerja
- **WHEN** developer memilih file `.vrkendang` dengan ZIP rusak, entri path traversal, ukuran melebihi batas, atau `kit.json` tidak valid
- **THEN** aplikasi menampilkan pesan galat yang jelas dan salinan kerja yang sedang terbuka tetap tidak berubah

#### Scenario: Membatalkan pemilih file tidak mengubah apa pun
- **WHEN** developer membuka pemilih file `Impor` lalu membatalkannya
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

### Requirement: Ubah parameter busur pembatas Bass (divider), bentuk oval, dan jumlah zona aktif
Developer SHALL dapat menyesuaikan jumlah zona aktif kendang Bass (1 sampai 5 zona) serta menyesuaikan parameter geometri pembatas Bass secara interaktif dan modular per zona terpilih:
1. Pemilihan jumlah zona (1–5 zona) secara dinamis mengaktifkan/menyembunyikan zona Bass terkait (`bass-1` s/d `bass-5`) tanpa menghilangkan sound dan gain zona yang telah dipasang.
2. Saat zona utama (`bass-1`) dipilih, antarmuka menyediakan kontrol bentuk oval bebas (`dx`, `dy`, `rx`, `ry`) di rentang validasi `kit-data` (`dx`/`dy` -3.0 s.d. 3.0, `rx`/`ry` 0.1 s.d. 3.0).
3. Saat zona tepi (`bass-2` s.d. `bass-5`) dipilih, antarmuka menyediakan kontrol khusus posisi sekat pembatas zona tersebut secara independen tanpa tercampur dengan parameter oval `bass-1`.
4. Permukaan kendang Bass SHALL tampil sebagai kulit alami bersih tanpa tempelan plak hitam kosmetik; garis oval dan sekat pembatas hanya tampil sebagai garis panduan visual tipis selama proses konfigurasi/kalibrasi.

#### Scenario: Perubahan parameter oval bass-1 langsung terlihat di papan
- **WHEN** developer memilih `bass-1` dan mengubah parameter oval `dx`, `dy`, `rx`, atau `ry`
- **THEN** garis panduan oval pembatas di layar berubah mengikuti nilai baru secara real-time tanpa tercampur dengan kontrol sekat zona tepi

#### Scenario: Perubahan posisi sekat zona tepi bass-2
- **WHEN** developer memilih `bass-2` dan mengubah slider posisi sekat
- **THEN** garis sekat pembatas zona tersebut bergeser naik/turun di kanvas preview secara real-time

#### Scenario: Mengubah jumlah zona aktif Bass menjadi 3 zona
- **WHEN** developer memilih tombol `3` pada pemilih jumlah zona Bass
- **THEN** kendang Bass memiliki 3 zona aktif (`bass-1`, `bass-2`, `bass-3`) dengan suara dan gain tersimpan tetap terjaga, serta zona yang tidak aktif disembunyikan dari daftar chip

#### Scenario: Parameter divider di luar rentang ditolak
- **WHEN** developer memasukkan nilai `dx`/`dy` di luar -3.0 sampai 3.0 atau `rx`/`ry` ≤ 0 atau > 3.0
- **THEN** perubahan ditolak dengan pesan galat yang jelas dan nilai divider sebelumnya tetap dipakai

### Requirement: Simpan edit kit saat ini di dalam aplikasi (`✓ Simpan`) dan Ekspor paket `.vrkendang` terpisah (`Ekspor`)
Mode Developer SHALL memisahkan aksi penyimpanan internal dari aksi ekspor file ZIP:
1. Tombol `✓ Simpan` SHALL menyimpan dan menimpa konfigurasi kit yang sedang diedit langsung di penyimpanan internal aplikasi (`filesDir/kits/<kitId>/` dan `filesDir/dev-kit/`) tanpa membuka pemilih file penyimpanan HP.
2. Tombol `Ekspor` SHALL secara khusus mengekspor kit saat ini menjadi satu file paket `.vrkendang` (ZIP) ke penyimpanan HP melalui pemilih lokasi sistem (`ACTION_CREATE_DOCUMENT`), setelah paket disusun penuh di cache dan divalidasi oleh `KitPackageReader` serta `KitValidator`.

#### Scenario: Tombol Simpan menyimpan perubahan secara internal tanpa membuka pemilih file HP
- **WHEN** developer mengedit suara atau parameter zona lalu menekan tombol `✓ Simpan`
- **THEN** perubahan disimpan menimpa kit saat ini di penyimpanan internal aplikasi (`filesDir/kits/<kitId>/`), aktif langsung di papan utama, dan tidak membuka pemilih lokasi file sistem HP

#### Scenario: Tombol Ekspor menulis paket .vrkendang ke penyimpanan HP
- **WHEN** developer menekan tombol `Ekspor` dan memilih lokasi di penyimpanan HP
- **THEN** paket `.vrkendang` disusun di cache, divalidasi oleh `KitPackageReader` dan `KitValidator`, lalu disalin ke lokasi URI yang dipilih tanpa membuat file `.bak`

#### Scenario: Membatalkan Ekspor tidak mengubah salinan kerja
- **WHEN** developer menekan tombol `Ekspor` lalu membatalkan pemilih lokasi file
- **THEN** salinan kerja dan kit tersimpan di dalam aplikasi tetap tidak berubah

#### Scenario: Paket hanya berisi WAV yang dirujuk kit.json
- **WHEN** developer mengekspor kit yang direktori kerjanya masih memiliki file WAV yatim
- **THEN** paket `.vrkendang` hanya berisi `kit.json` dan file `sounds/<zoneId>.wav` yang dirujuk oleh `kit.json`, tanpa file WAV yatim

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

### Requirement: Cakupan penyuntingan struktur kit dan fleksibilitas kendang
Struktur kit SHALL mendukung penambahan kendang baru dan penghapusan kendang (kecuali Bass yang diproteksi tidak boleh dihapus). Developer SHALL dapat mengubah nama kit, posisi, radius, jumlah zona (Bass: 1–5 zona; kendang non-bass: 1–4 zona), parameter divider Bass, parameter putaran sudut rotasi ($0^\circ - 360^\circ$) pada kendang non-bass dengan $\ge 2$ zona, serta sound dan gain tiap zona.

#### Scenario: Developer menambah kendang baru
- **WHEN** developer menekan tombol `+ Kendang`, memasukkan nama kendang dan memilih jumlah zona awal (1–4 zona)
- **THEN** kendang baru ditambahkan ke kit dengan ID unik dan model yang sesuai (`single` untuk 1 zona, `split` untuk 2–4 zona), langsung dapat diatur dan dimainkan

#### Scenario: Developer menghapus kendang non-bass
- **WHEN** developer memilih kendang selain Bass lalu menekan `Hapus Kendang` dan mengonfirmasi dialog
- **THEN** kendang beserta seluruh zonanya dan file WAV terkait dihapus dari kit, dan seleksi berpindah ke Bass

#### Scenario: Menghapus kendang Bass ditolak
- **WHEN** developer mencoba menghapus kendang Bass
- **THEN** tombol hapus tidak tersedia untuk Bass dan operasi penghapusan ditolak

#### Scenario: Developer mengatur jumlah zona dan putaran sudut kendang non-bass
- **WHEN** developer memilih jumlah zona (1–4) pada kendang non-bass dan menggeser slider putaran sudut ($0^\circ - 360^\circ$)
- **THEN** kendang menyesuaikan jumlah zona juring radialnya dan garis pembatas sekat berputar secara real-time pada papan pratinjau

#### Scenario: Developer menyesuaikan posisi dan ukuran kendang di Mode Developer
- **WHEN** developer menggeser kendang dengan 1 jari, mencubit dengan 2 jari, atau menggunakan slider Posisi X, Posisi Y, dan Radius di panel kanan
- **THEN** posisi dan ukuran kendang diperbarui secara real-time pada pratinjau dan tersimpan di salinan kerja

#### Scenario: Kendang yang disentuh dan digeser otomatis berada di lapisan teratas (Z-Order Overlap)
- **WHEN** developer menyentuh atau menggeser suatu kendang saat terjadi tumpang tindih dengan kendang lain
- **THEN** kendang tersebut otomatis diangkat ke lapisan teratas (indeks 0 array `drums`), tampak di atas kendang lain secara visual, dan memenangkan evaluasi sentuhan (hit-test) pada area tumpang tindih

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
