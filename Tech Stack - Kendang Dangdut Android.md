# Tech Stack: Kendang Dangdut Android

Status: Draf 1, berdasarkan keputusan diskusi. Dokumen ini berpasangan dengan PRD. Nomor versi library, Gradle, NDK, dan lainnya sengaja tidak ditetapkan di sini; cek versi stabil terbaru saat proyek dibuat.

## 1. Keputusan Utama

| Aspek | Keputusan |
| --- | --- |
| Platform | Android saja (HP dan tablet), tanpa iOS |
| Bahasa utama | Kotlin native |
| Versi Android minimal | Android 8.0 (API 26) |
| UI | View tradisional + custom View (bukan Compose, bukan Flutter) |
| Mesin audio | Oboe (C++ lewat NDK) |
| Format suara | WAV |
| Data kit | File JSON di folder assets |
| Build dan CI | GitHub Actions, APK debug di setiap push |
| Alur pengembangan | Opencode di VPS menulis kode, lalu push ke GitHub |
| Orientasi | Landscape saja, dikunci |

## 2. Alasan Keputusan

- **Kotlin native dan Android saja**: kunci produk adalah latensi rendah dan suara yang menumpuk. Android native punya akses langsung ke audio latensi rendah. Tidak ada rencana iOS, jadi keunggulan lintas platform tidak diperlukan.
- **Android 8.0**: dari data pangsa versi Android (StatCounter, Agustus 2026, seluruh dunia), sekitar 89% perangkat memakai Android 11 ke atas. Android 8.0 dipilih supaya HP yang lebih lama masih terjangkau dengan jalur audio latensi rendah tetap tersedia. Data khusus Indonesia tidak ditemukan.
- **View tradisional + custom View**: dipilih pemilik produk. Tiap kendang cocok dibuat sebagai satu custom View yang menggambar dan menangani sentuhannya sendiri.
- **Oboe**: prioritas latensi serendah mungkin, dan mendukung banyak suara aktif bersamaan untuk rolling.
- **WAV**: dimuat ke memori sebagai data mentah tanpa dekode, sehingga lebih sederhana dengan Oboe. Ukuran file tidak jadi masalah untuk 1 kit dan 10 suara.
- **JSON di assets**: sejalan dengan keputusan bahwa tata letak adalah data kit, bukan ditulis mati di kode. Kit baru cukup menambah satu file.

## 3. Arsitektur Tingkat Tinggi

Tiga lapisan:

1. **Lapisan data kit**: membaca file JSON kit dan menghasilkan model kit (kendang, bentuk, posisi, area, nama file suara).
2. **Lapisan tampilan dan sentuhan (Kotlin)**: custom View per kendang. Menggambar kulit dan badan kendang, menentukan area yang tersentuh, dan menjalankan efek denyut. Area sentuh dipisahkan dari gambar, sehingga gambar bisa diganti tanpa mengubah area sentuh.
3. **Lapisan audio (C++/Oboe)**: memuat WAV ke memori dan membunyikan suara saat ada perintah dari lapisan tampilan. Banyak suara bisa aktif bersamaan dan saling menumpuk tanpa saling memotong.

### Aturan penting

- **Hit-testing area** (bulan sabit Bass, garis pemisah, oval) adalah logika hitung murni, dipisah dari tampilan, supaya bisa diuji otomatis tanpa perangkat.
- Suara dibunyikan saat jari menyentuh layar (bukan saat diangkat).
- Setiap sentuhan dihitung tanpa batas jumlah jari dari sisi aplikasi. Batas multitouch mengikuti perangkat.
- Tap di luar kulit (di badan kendang) tidak membunyikan apa pun.

## 4. Data Kit (JSON)

Satu file JSON per kit di assets. Kit awal: New Metro. File ini mendeskripsikan:

- Nama kit
- Daftar kendang: nama kode, bentuk kulit (lingkaran atau oval), ukuran, posisi di layar
- Daftar area tiap kendang: bentuk dan batasnya, serta nama file suara
- Nama kode suara: Bass-1 sampai Bass-5, Sedang-1, Sedang-2, Kecil1-1, Kecil2-1, Kecil2-2

Catatan:

- Area 1 Bass (bulan sabit dengan batas kanan melengkung) dapat dideskripsikan sebagai lingkaran Bass dikurangi lingkaran lain yang bergeser ke kanan. Angka pastinya ditentukan di tahap spek.
- Area 2 sampai 5 Bass dan area atas/bawah di kendang lain berupa pemisah garis lurus mendatar.
- Format JSON dibuat sendiri untuk aplikasi ini. Impor kit dari aplikasi lain tidak termasuk cakupan.

## 5. Aset Suara

- Format WAV, satu rekaman per area.
- Aset dibuat dan diisi sendiri oleh pemilik produk, belum tersedia saat ini.
- Perlu **suara pengganti sementara** (placeholder) agar tata letak dan sentuhan bisa diuji sebelum rekaman asli selesai.
- Detail teknis format WAV (sample rate, channel, bit depth) belum diputuskan dan ditetapkan bersama saat rekaman dibuat. Idealnya seragam untuk semua file.

## 6. Build, CI, dan Pengujian

### Alur kerja

1. Opencode di VPS menulis kode (tanpa menjalankan aplikasi).
2. Push ke GitHub.
3. GitHub Actions membangun **APK debug** di setiap push (tanpa tanda tangan rilis).
4. Pemilik produk memasang APK di HP untuk tes.

### Pengujian

- **Otomatis (di workflow)**: build harus berhasil, dan unit test untuk logika hit-testing area serta pembacaan JSON kit.
- **Manual (di HP)**: bunyi, latensi, multitouch, rolling dengan suara menumpuk, dan efek denyut.

### Catatan untuk opencode

- Opencode tidak bisa mendengar suara atau menjalankan aplikasi, jadi jangan mengklaim fitur audio "sudah teruji". Verifikasi akhir ada pada tes manual.
- Jika Android SDK dan NDK tidak terpasang di VPS, kegagalan kompilasi baru terlihat setelah workflow berjalan. Workflow harus melaporkan kegagalan dengan jelas.
- Proyek memerlukan NDK dan CMake karena ada kode C++ (Oboe).

## 7. Persyaratan Teknis Utama

- Latensi dari sentuhan ke suara serendah mungkin.
- Mendukung banyak suara aktif bersamaan. Batas jumlah suara aktif ditentukan di tahap teknis berikutnya.
- Layout landscape yang terkunci, nyaman di HP dan tablet.

## 8. Belum Diputuskan

- Batas jumlah suara aktif bersamaan
- Spesifikasi teknis WAV (sample rate, channel, bit depth)
- Detail efek denyut dan cara animasinya
- Angka pasti geometri area (misalnya lebar Area 1 Bass)
- Versi pasti Gradle, Android Gradle Plugin, NDK, Oboe, dan JDK (cek versi stabil terbaru saat memulai)
- Penempatan iklan dan library iklan (di luar cakupan versi awal)
- Tanda tangan rilis dan publikasi Play Store (dibahas nanti)
