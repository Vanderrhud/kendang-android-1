# Tasks

> Build dan test tidak dapat dijalankan di VM lokal; verifikasi otomatis dilakukan oleh GitHub Actions CI (unit test `:kit-core` dan build APK). Verifikasi visual/perilaku di HP dilakukan manual oleh pemilik produk.

## 1. Model dan validasi di `:kit-core`

- [x] 1.1 Tambah `image: String? = null` pada `DrumData` dan pastikan `KitJsonParser` mengurai kit lama tanpa `image` serta menulis `"image": null` saat stringify; verifikasi lewat unit test parser (kit lama + kit dengan `image`) lulus di CI.
- [x] 1.2 Tambah aturan validasi `image` di `KitValidator` (persis `images/<drumId>.png`, bukan absolut, tanpa `..`); verifikasi dengan unit test untuk nama tidak identik, path absolut, path traversal, dan `image` null.
- [x] 1.3 Tambah pemeriksaan signature + IHDR PNG di `:kit-core` (tanpa dekode piksel) dan unit test file PNG valid, terpotong, serta bukan PNG.

## 2. Paket `.vrkendang` di `:kit-core`

- [x] 2.1 Tambah `MAX_PNG_SIZE` (2 MB) dan pengenalan entri `images/*.png` di `KitPackageReader` pada tahap validasi struktur sebelum penulisan disk; verifikasi dengan unit test PNG > 2 MB ditolak tanpa file tertulis dan path traversal pada `images/` ditolak.
- [x] 2.2 Perluas `KitPackageWriter` dengan penyedia byte PNG sehingga hanya PNG yang dirujuk `kit.json` ditulis; verifikasi dengan unit test tulis-lalu-baca identik dan PNG yatim tidak ikut terbawa.
- [x] 2.3 Perluas unit test validasi seluruh `assets/kits/*/kit.json` agar memeriksa keberadaan dan validitas PNG bila `image` tidak null; verifikasi kit bawaan `new-metro` (tanpa `image`) tetap lulus di CI.

## 3. Pemuatan dan render di `:app` (main)

- [x] 3.1 Tambah abstraksi sumber byte gambar kit (assets, `filesDir/kits/<id>/`, `dev-kit`) dan pemuat bitmap ber-`inSampleSize` yang menghasilkan `Map<drumId, Bitmap>`; galat dekode menjadi drum tanpa gambar + log. Verifikasi dengan unit test JVM menggunakan sumber palsu (gambar valid, rusak, tidak ada).
- [x] 3.2 Sambungkan pemuat ke aktivasi kit di `KitRepository`/`MainActivity` dan pembebasan bitmap saat kit berganti; verifikasi dengan unit test pergantian kit tidak memakai gambar kit sebelumnya.
- [x] 3.3 Ubah `KendangBoardView.onDraw`: bila drum bergambar, gambar PNG ke kotak `center ± radius` (dengan denyut), lewati badan 3D/kulit/cincin/rim; kendang tanpa gambar memakai jalur lama; Z-order, garis panduan, label, dan highlight tidak berubah. Verifikasi CI build lulus dan uji manual di HP: kendang bulat, oval, geser, pinch, kendang campuran bergambar/tanpa gambar.
- [x] 3.4 Pastikan tidak ada dekode bitmap di `onDraw`/jalur sentuh (tinjauan kode) dan build release tetap lolos pemeriksaan tanpa kode Mode Developer.

## 4. Mode Developer (source set `debug`)

- [x] 4.1 Buat installer PNG sejajar `DevSoundInstaller`: tolak > 2 MB, tolak gambar non-persegi (lebar != tinggi), validasi PNG, dekode aman, downsample sisi terpanjang ≤ 1024 px tanpa memperbesar, tulis `images/<drumId>.png` dan perbarui `image` di `kit.json` secara atomik; kegagalan tidak mengubah file lama. Verifikasi dengan unit test (`testDebug`) untuk sukses, bukan PNG, non-persegi, > 2 MB, dan gagal tulis.
- [x] 4.2 Tambah fungsi sesi: pasang gambar, kosongkan gambar, hapus gambar saat `deleteDrum`, dan rename gambar saat ID kendang berubah, termasuk pembaruan `image`; verifikasi dengan unit test sesi (hapus kendang menghapus PNG, rename memindahkan file dan memperbarui `image`).
- [x] 4.3 Tambah tombol pasang/kosongkan gambar di panel kendang (`DevPickerFragment`/`DevAuthoringActivity`) memakai `ACTION_OPEN_DOCUMENT`, dengan pesan galat jelas dan pembatalan tanpa perubahan; verifikasi manual di HP (pasang PNG, tampil langsung di pratinjau, batal, kosongkan).
- [x] 4.4 Pastikan simpan internal (`✓ Simpan`, `+ Kit Baru`), buka sebagai salinan kerja, ekspor, dan impor `.vrkendang` menyertakan folder `images/` hanya untuk PNG yang dirujuk; verifikasi dengan unit test sesi (simpan lalu muat ulang, ekspor lalu impor) dan uji manual ekspor-impor di HP.

## 5. Verifikasi menyeluruh

- [x] 5.1 Jalankan CI di GitHub Actions: unit test `:kit-core` dan `:app` lulus, APK debug dan kompilasi release berhasil; laporkan hasil CI apa adanya.
- [x] 5.2 Uji manual di HP fisik: kit lama tanpa gambar tampil sama seperti sebelumnya, kendang bergambar dapat dimainkan dengan highlight benar, PNG rusak tidak menyebabkan crash, dan hasil ekspor-impor mempertahankan gambar.
