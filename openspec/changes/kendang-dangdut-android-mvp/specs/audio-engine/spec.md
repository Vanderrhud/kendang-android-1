# Spec Delta

## Purpose

Menyediakan pemutaran suara dengan latensi serendah mungkin, mendukung banyak suara aktif secara bersamaan (polyphony), menangani zona yang belum memiliki suara secara aman, dan mengelola siklus hidup audio agar hemat sumber daya saat aplikasi tidak di foreground.

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
- **THEN** semua zona yang tersentuh dan telah memiliki file suara menghasilkan suara secara bersamaan

### Requirement: Voice Stealing saat kapasitas penuh
Ketika seluruh kapasitas 32 slot suara terisi, mesin audio SHALL secara otomatis mengambil alih slot suara yang paling tua untuk membunyikan suara baru, dengan transisi yang mulus tanpa bunyi pop atau klik.

#### Scenario: Suara tertua diambil alih saat rolling ekstrem
- **WHEN** lebih dari 32 ketukan aktif bersamaan (misalnya rolling sangat cepat)
- **THEN** suara yang paling lama aktif dihentikan secara mulus dan slotnya digunakan untuk suara baru

### Requirement: Penanganan zona tanpa suara (unassigned) yang aman
Zona kendang yang belum dipasangi file suara SHALL tidak menyebabkan crash atau error apapun saat disentuh. Sentuhan pada zona unassigned diabaikan secara aman oleh audio engine.

#### Scenario: Sentuhan pada zona unassigned tidak menimbulkan error
- **WHEN** pemain atau developer menyentuh zona yang `sound` -nya null atau belum dipasangkan
- **THEN** tidak ada suara yang dibunyikan, tidak ada crash, dan tidak ada pesan error yang muncul ke pengguna

#### Scenario: Zone unassigned masih memberikan visual feedback
- **WHEN** developer menyentuh zona yang belum memiliki suara
- **THEN** visual feedback sentuhan (highlight) tetap tampil untuk mengkonfirmasi hit-testing bekerja benar

### Requirement: Resampling WAV ke native sample rate
Mesin audio C++ SHALL secara otomatis me-resample data PCM WAV yang dimuat ke sample rate native stream Oboe, sehingga file WAV dengan sample rate apapun dapat diputar dengan benar.

#### Scenario: WAV dengan sample rate berbeda dari stream diputar dengan benar
- **WHEN** file WAV dengan sample rate tertentu dimuat ke audio engine
- **THEN** audio engine me-resample data WAV tersebut ke sample rate native stream dan suara diputar dengan pitch yang benar

### Requirement: WAV dimuat ke memori saat diaktifkan
Seluruh file WAV dari kit aktif SHALL dimuat ke dalam memori RAM saat dipasangkan atau saat kit dibuka, sehingga pemutaran suara tidak memerlukan pembacaan disk saat jari mengetuk.

#### Scenario: Suara tersedia instan setelah dipasangkan
- **WHEN** developer memasangkan file WAV ke zona dan mengetuk zona tersebut
- **THEN** suara langsung terdengar tanpa jeda yang terasa

### Requirement: Audio stream dijeda saat aplikasi masuk background
Mesin audio SHALL menghentikan (pause) stream audio saat aplikasi tidak lagi berada di foreground, dan melanjutkan (resume) stream saat aplikasi kembali ke foreground.

#### Scenario: Audio berhenti saat aplikasi diminimalkan
- **WHEN** pemain menekan tombol Home atau beralih ke aplikasi lain
- **THEN** stream audio Oboe dijeda dan tidak mengkonsumsi sumber daya CPU

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
