# Spec Delta

## Purpose

Menyediakan pemutaran suara dengan latensi serendah mungkin, mendukung banyak suara aktif secara bersamaan (polyphony), menangani zona yang belum memiliki suara secara aman, dan mengelola siklus hidup audio agar hemat sumber daya saat aplikasi tidak di foreground.

## ADDED Requirements

### Requirement: Latensi sentuhan ke suara serendah mungkin
Mesin audio SHALL menggunakan jalur audio berperforma rendah-latensi (Fast Audio Path) yang disediakan oleh sistem Android, dengan target keluaran audio ≤ 20 ms dan latensi sentuh-ke-bunyi total ≤ 40 ms pada perangkat dengan jalur low-latency (sebagai target pengukuran manual pada perangkat fisik, bukan syarat kelulusan CI).

#### Scenario: Aplikasi menggunakan native sample rate perangkat
- **WHEN** aplikasi pertama kali dibuka dan stream audio diinisialisasi
- **THEN** mesin audio membuka stream audio tanpa menetapkan sample rate, lalu membaca sample rate yang diberikan oleh stream dan menggunakannya sebagai target resampling PCM

#### Scenario: Penggunaan OpenSL ES otomatis di Android 8.0 (API 26)
- **WHEN** aplikasi berjalan pada perangkat dengan Android 8.0 (API 26)
- **THEN** stream audio secara otomatis menggunakan OpenSL ES dan tetap meminta mode LowLatency

### Requirement: Audio callback aman real-time
Audio callback SHALL tidak melakukan alokasi memori dinamis, tidak menggunakan mutex, dan tidak memanggil JNI. Trigger dari thread UI SHALL masuk lewat antrean lock-free single-producer/single-consumer (SPSC).

#### Scenario: Sentuhan zona diproses di callback tanpa lock atau alokasi
- **WHEN** sentuhan zona masuk ke antrean lock-free dari thread UI
- **THEN** audio callback membaca event dari antrean dan memulai voice tanpa melakukan locking atau alokasi memori

### Requirement: Kepemilikan data sample di memori C++
Mesin audio C++ SHALL menyalin data PCM ke alokasi memorinya sendiri saat pemanggilan fungsi load berlangsung. Setelah pemanggilan fungsi load kembali, Kotlin bebas melepas buffer dan C++ SHALL tidak menyimpan pointer ke buffer milik Kotlin.

#### Scenario: Kotlin melepas buffer setelah load selesai
- **WHEN** pemanggilan fungsi load ke C++ selesai dan kembali ke Kotlin
- **THEN** Kotlin dapat melepas atau mendaur ulang buffer, dan C++ tetap memutar suara dari salinan memori miliknya sendiri tanpa dangling pointer

### Requirement: Polyphony hingga 32 suara simultan
Mesin audio SHALL mampu memainkan hingga 32 suara secara bersamaan tanpa saling memotong; di atas 32 suara berlaku Voice Stealing.

#### Scenario: Rolling cepat menumpuk suara
- **WHEN** pemain mengetuk zona yang sama berulang kali secara cepat
- **THEN** setiap ketukan menghasilkan suara baru yang terdengar menumpuk di atas suara sebelumnya tanpa ada yang terpotong hingga batas 32 suara

#### Scenario: Banyak jari membunyikan zona berbeda bersamaan
- **WHEN** pemain menggunakan banyak jari untuk menyentuh zona-zona berbeda secara bersamaan
- **THEN** semua zona yang tersentuh dan telah memiliki file suara menghasilkan suara secara bersamaan

### Requirement: Voice Stealing saat kapasitas penuh
Ketika kapasitas melebihi 32 slot suara aktif, mesin audio SHALL secara otomatis mengambil alih slot suara yang paling tua untuk membunyikan suara baru, dengan transisi yang mulus (micro-fade-out 1 ms) tanpa bunyi pop atau klik.

#### Scenario: Suara tertua diambil alih saat rolling ekstrem
- **WHEN** lebih dari 32 suara aktif bersamaan (misalnya rolling sangat cepat)
- **THEN** suara yang paling lama aktif dihentikan secara mulus dan slotnya digunakan untuk suara baru

### Requirement: Gain per zona dan master limiter
Mesin audio SHALL mengalikan setiap voice aktif dengan nilai gain zona (rentang 0.0–2.0), dan hasil penjumlahan seluruh voice aktif SHALL melewati master limiter agar tidak menghasilkan distorsi clipping keras (hard clipping). Perubahan gain per zona SHALL berlaku pada ketukan berikutnya tanpa memuat ulang sample.

#### Scenario: Banyak voice aktif dengan gain tinggi tidak clipping keras
- **WHEN** banyak voice aktif bersamaan dengan nilai gain tinggi yang berpotensi melebihi amplitudo maksimum
- **THEN** sinyal keluaran melewati master limiter sehingga tidak menghasilkan distorsi clipping keras

#### Scenario: Gain baru berlaku pada ketukan berikutnya tanpa memuat ulang sample
- **WHEN** nilai gain suatu zona diubah saat kit aktif
- **THEN** ketukan berikutnya pada zona tersebut memakai nilai gain baru tanpa memuat ulang file WAV

### Requirement: Pemuatan sample saat stream berjalan tidak memblokir audio callback
Mesin audio SHALL mendukung pemuatan atau penggantian sample saat stream berjalan tanpa memblokir atau menyebabkan glitch pada audio callback. File SHALL diurai dan di-resample di luar thread audio, lalu sample SHALL ditukar secara atomik. Bila zona sedang berbunyi, suara lama SHALL diselesaikan atau dihentikan secara mulus.

#### Scenario: Memuat sample baru saat stream berjalan
- **WHEN** sample baru dimuat atau menggantikan sample lama saat stream audio sedang berjalan
- **THEN** penguraian dan resampling dilakukan di luar thread audio, sample ditukar secara atomik, dan audio callback tidak diblokir atau menghasilkan glitch

#### Scenario: Mengganti sample zona yang sedang berbunyi
- **WHEN** sample pada zona yang sedang membunyikan suara lama diganti
- **THEN** suara lama diselesaikan atau dihentikan secara mulus tanpa bunyi pop

### Requirement: Penanganan zona tanpa suara (unassigned) yang aman
Zona kendang yang belum dipasangi file suara SHALL tidak menyebabkan crash atau error apapun saat disentuh. Sentuhan pada zona unassigned diabaikan secara aman oleh audio engine tanpa menghasilkan bunyi.

#### Scenario: Sentuhan pada zona unassigned tidak menimbulkan error
- **WHEN** pemain atau developer menyentuh zona yang `sound`-nya null atau belum dipasangkan
- **THEN** tidak ada suara yang dibunyikan, tidak ada crash, dan tidak ada pesan error yang muncul ke pengguna

### Requirement: Resampling PCM ke native sample rate stream
Mesin audio C++ SHALL menerima data PCM dan sample rate asli (setelah diurai oleh modul Kotlin) dan secara otomatis me-resample data PCM tersebut ke sample rate native stream Oboe, sehingga suara dapat diputar dengan pitch yang benar tanpa C++ mengurai file WAV.

#### Scenario: PCM dengan sample rate berbeda dari stream diputar dengan benar
- **WHEN** data PCM dengan sample rate tertentu dimuat ke audio engine
- **THEN** audio engine me-resample data PCM tersebut ke sample rate native stream dan suara diputar dengan pitch yang benar

### Requirement: WAV dimuat ke RAM saat kit diaktifkan
Seluruh file WAV dari kit aktif SHALL dimuat ke dalam memori RAM saat kit diaktifkan sebelum kendang bisa dimainkan, sehingga pemutaran suara tidak memerlukan pembacaan disk saat jari mengetuk.

#### Scenario: Semua WAV dimuat ke RAM saat kit diaktifkan
- **WHEN** suatu kit diaktifkan
- **THEN** semua file WAV pada kit tersebut dimuat ke RAM sebelum kendang bisa dimainkan, sehingga ketukan menghasilkan suara seketika tanpa jeda pembacaan disk

#### Scenario: Kit tanpa satu pun file WAV tetap bisa diaktifkan
- **WHEN** suatu kit yang seluruh zonanya tanpa file suara (semua sound null) diaktifkan
- **THEN** kit tetap berhasil aktif, stream audio Oboe tetap dibuka secara normal, sentuhan pada zona tidak menghasilkan suara, dan tidak terjadi crash

### Requirement: Penanganan kegagalan memuat WAV yang aman
Bila satu file WAV gagal dimuat ke dalam audio engine, zona tersebut SHALL diperlakukan sebagai zona tanpa suara (unassigned), galat SHALL dicatat ke log, dan aplikasi SHALL tidak crash.

#### Scenario: File WAV gagal dimuat
- **WHEN** terjadi kegagalan saat membaca atau memuat data PCM suatu file WAV
- **THEN** zona terkait diperlakukan sebagai tanpa suara, pesan galat dicatat, dan aplikasi tetap berjalan normal tanpa crash

### Requirement: Audio stream dijeda saat aplikasi masuk background
Mesin audio SHALL menghentikan (pause) stream audio saat aplikasi tidak lagi berada di foreground dan menghentikan semua voice aktif agar tidak ada bunyi menggantung saat resume, serta melanjutkan (resume) stream saat aplikasi kembali ke foreground.

#### Scenario: Audio berhenti dan voice dimatikan saat aplikasi diminimalkan
- **WHEN** pemain menekan tombol Home atau beralih ke aplikasi lain
- **THEN** stream audio Oboe dijeda, semua voice aktif dihentikan agar tidak ada bunyi menggantung saat dibuka kembali, dan audio tidak mengkonsumsi CPU

#### Scenario: Audio kembali normal saat aplikasi dibuka lagi
- **WHEN** pemain kembali ke aplikasi VR Kendang
- **THEN** stream audio dilanjutkan kembali dan kendang siap dimainkan

### Requirement: Reconnect otomatis saat perangkat audio berganti
Mesin audio SHALL mendeteksi pemutusan stream yang terjadi akibat perubahan perangkat audio (headphone dicolok atau dicabut) dan secara otomatis membuka kembali stream audio ke output yang baru, tanpa memerlukan tindakan dari pemain.

#### Scenario: Aplikasi kembali berbunyi setelah headphone dicabut
- **WHEN** pemain mencabut headphone saat aplikasi sedang berjalan
- **THEN** audio secara otomatis beralih ke speaker perangkat tanpa pemain harus menutup atau membuka ulang aplikasi

#### Scenario: Aplikasi kembali berbunyi setelah headphone dicolok
- **WHEN** pemain mencolokkan headphone saat aplikasi sedang berjalan
- **THEN** audio secara otomatis beralih ke headphone tanpa gangguan

#### Scenario: Resampling ulang saat sample rate stream berubah setelah reconnect
- **WHEN** stream audio dibuka ulang dengan sample rate berbeda setelah reconnect perangkat audio
- **THEN** sample audio di-resample ulang dari PCM asli ke sample rate stream yang baru dan suara diputar dengan pitch yang tetap benar
