# Spec Delta

## Purpose

Menyediakan pemutaran suara dengan latensi serendah mungkin, mendukung banyak suara aktif secara bersamaan (polyphony), dan mengelola siklus hidup audio agar hemat sumber daya saat aplikasi tidak di foreground.

## ADDED Requirements

### Requirement: Latensi sentuhan ke suara serendah mungkin
Mesin audio SHALL menggunakan jalur audio berperforma rendah-latensi (Fast Audio Path) yang disediakan oleh sistem Android, dengan parameter audio stream (sample rate, buffer size) disesuaikan secara otomatis dengan kemampuan hardware perangkat.

#### Scenario: Aplikasi menggunakan native sample rate perangkat
- **WHEN** aplikasi pertama kali dibuka dan stream audio diinisialisasi
- **THEN** mesin audio membaca sample rate native hardware perangkat dan membuka stream dengan nilai tersebut, bukan nilai tetap yang dikodekan

### Requirement: Polyphony 32 suara simultan
Mesin audio SHALL mampu memainkan hingga 32 suara secara bersamaan tanpa ada suara yang saling memotong satu sama lain.

#### Scenario: Rolling cepat menumpuk suara
- **WHEN** pemain mengetuk zona yang sama berulang kali secara cepat
- **THEN** setiap ketukan menghasilkan suara baru yang terdengar menumpuk di atas suara sebelumnya tanpa ada yang terpotong

#### Scenario: Banyak jari membunyikan zona berbeda bersamaan
- **WHEN** pemain menggunakan banyak jari untuk menyentuh zona-zona berbeda secara bersamaan
- **THEN** semua zona yang tersentuh menghasilkan suara secara bersamaan

### Requirement: Voice Stealing saat kapasitas penuh
Ketika seluruh kapasitas 32 slot suara terisi, mesin audio SHALL secara otomatis mengambil alih slot suara yang paling tua untuk membunyikan suara baru, dengan transisi yang mulus tanpa bunyi pop atau klik.

#### Scenario: Suara tertua diambil alih saat rolling ekstrem
- **WHEN** lebih dari 32 ketukan aktif bersamaan (misalnya rolling sangat cepat)
- **THEN** suara yang paling lama aktif dihentikan secara mulus dan slotnya digunakan untuk suara baru

### Requirement: WAV dimuat ke memori saat kit dibuka
Seluruh file WAV dari kit aktif SHALL dimuat ke dalam memori RAM saat kit dipilih, sehingga pemutaran suara tidak memerlukan pembacaan disk saat jari mengetuk.

#### Scenario: Suara tersedia instan setelah kit dimuat
- **WHEN** pemain memilih kit dan mulai bermain
- **THEN** setiap ketukan membunyikan suara tanpa jeda yang terasa, karena data WAV sudah ada di memori

### Requirement: Audio stream dijeda saat aplikasi masuk background
Mesin audio SHALL menghentikan (pause) stream audio saat aplikasi tidak lagi berada di foreground, dan melanjutkan (resume) stream saat aplikasi kembali ke foreground.

#### Scenario: Audio berhenti saat aplikasi diminimalkan
- **WHEN** pemain menekan tombol Home atau beralih ke aplikasi lain
- **THEN** stream audio Oboe dijeda dan tidak mengkonsumsi sumber daya CPU

#### Scenario: Audio kembali normal saat aplikasi dibuka lagi
- **WHEN** pemain kembali ke aplikasi kendang
- **THEN** stream audio dilanjutkan kembali dan kendang siap dimainkan

### Requirement: Reconnect otomatis saat perangkat audio berganti
Mesin audio SHALL mendeteksi pemutusan stream yang terjadi akibat perubahan perangkat audio (headphone dicolok atau dicabut) dan secara otomatis membuka kembali stream audio ke output yang baru, tanpa memerlukan tindakan dari pemain.

#### Scenario: Aplikasi kembali berbunyi setelah headphone dicabut
- **WHEN** pemain mencabut headphone saat aplikasi sedang berjalan
- **THEN** audio secara otomatis beralih ke speaker perangkat tanpa pemain harus menutup atau membuka ulang aplikasi

#### Scenario: Aplikasi kembali berbunyi setelah headphone dicolok
- **WHEN** pemain mencolokkan headphone saat aplikasi sedang berjalan
- **THEN** audio secara otomatis beralih ke headphone tanpa gangguan
