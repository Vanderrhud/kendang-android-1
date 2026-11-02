# Panduan Repositori

Panduan ini berlaku untuk seluruh pekerjaan di repositori ini, bukan hanya untuk satu change, fitur, atau jenis file tertentu. Saat mengerjakan area tertentu, tetap ikuti konvensi dan struktur yang sudah ada di area tersebut.

## Batas Kerja Agent

- Sebelum mengubah apa pun, periksa struktur repo, `git status`, dan instruksi yang lebih spesifik di direktori terkait.
- Jaga perubahan kecil, dapat ditinjau, dan hanya dalam lingkup permintaan atau task aktif. Jangan memperluas scope, menebak keputusan yang belum ada, atau mengubah dependensi, konfigurasi, maupun artefak lain hanya untuk mengatasi masalah lokal.
- **Lingkungan Build & Test**: Lingkungan lokal VM ini tidak memiliki JDK/Android SDK/NDK dan dilarang mencoba menginstal package sistem (seperti OpenJDK, apt-get, dll). Semua kompilasi build, test otomatis, dan pembuatan APK dilakukan secara terpusat melalui GitHub Actions CI.
- **Larangan Modifikasi Spekulatif**: Dilarang berasumsi, mengubah kode, menambah test, atau membuang perubahan kerja (seperti `git restore`) tanpa instruksi eksplisit pengguna. Jika membutuhkan klarifikasi atau ada beberapa opsi, diskusikan terlebih dahulu sebelum bertindak.
- Jika proyek menggunakan OpenSpec, artefak change aktif adalah sumber kebenaran untuk scope dan kebutuhan perubahan tersebut. `AGENTS.md` ini hanya guardrail umum dan tidak menggantikan atau menambah kebutuhan OpenSpec.
- Bedakan hasil pemeriksaan statis, test, CI, dan uji manual. Laporkan hanya verifikasi yang benar-benar dilakukan; jangan mengklaim perilaku yang belum dibuktikan.

## Batas Percobaan Saat Gagal

- Untuk satu kegagalan atau pendekatan yang sama saat menulis kode, menjalankan perintah, build, test, atau verifikasi, lakukan paling banyak **dua percobaan**: percobaan awal dan satu percobaan ulang setelah membaca serta memperbaiki penyebabnya.
- Jika percobaan kedua masih gagal, hentikan pekerjaan pada jalur tersebut. Jangan melakukan percobaan ketiga, workaround spekulatif, perubahan scope, atau perubahan destruktif.
- Beri laporan kepada pengguna untuk didiskusikan: tujuan yang gagal, dua langkah yang sudah dicoba, hasil/error yang relevan, dugaan penyebab, dampak terhadap task, dan pilihan langkah aman berikutnya.

## Prinsip Komentar Kode

Kode menjelaskan **apa** yang dilakukan. Komentar menjelaskan **mengapa**, **batasannya**, dan **konteks** yang tidak terbaca langsung dari kode.

- Utamakan nama yang jelas dan struktur kode yang sederhana sebelum menambah komentar.
- Tulis komentar untuk keputusan yang tidak jelas, trade-off, invariant, satuan, rentang nilai, asumsi, dan efek samping.
- Dokumentasikan API publik, kontrak input/output, kondisi error, serta kepemilikan resource.
- Jelaskan bagian yang berkaitan dengan lifecycle, threading, concurrency, keamanan, performa, atau workaround.
- Jelaskan rumus atau algoritma yang kompleks beserta satuan dan sumber kebenarannya.
- Pada test, komentari alasan kasus batas yang tidak jelas; tidak perlu mengomentari assertion yang sudah jelas.
- Gunakan bahasa yang konsisten dengan area file dan tim. Jangan mencampur beberapa bahasa dalam satu blok komentar.
- Letakkan komentar dekat dengan kode yang dijelaskan dan perbarui saat kode berubah.

## Yang Harus Dihindari

- Komentar yang hanya mengulang kode.
- Komentar usang, tidak akurat, atau menyesatkan.
- Kode yang di-comment out dan dibiarkan menumpuk.
- `TODO` atau `FIXME` tanpa konteks, alasan, atau pemilik.
- Komentar yang dijadikan pengganti dokumentasi atau spesifikasi.
- Magic number tanpa nama konstanta atau penjelasan.

## Definition of Done untuk Komentar

- API publik dan perilaku non-obvious sudah terdokumentasi.
- Keputusan penting memiliki alasan yang bisa dipahami pembaca berikutnya.
- Tidak ada komentar yang bertentangan dengan perilaku kode.
- Nama, komentar, dan test konsisten dengan perilaku aktual.

## Verifikasi

- Jalankan pemeriksaan yang relevan untuk area yang diubah.
- Periksa diff sebelum commit; pastikan komentar tidak menambah noise atau informasi basi.
