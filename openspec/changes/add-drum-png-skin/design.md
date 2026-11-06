# Design

## Context

Lihat `proposal.md` (Why) untuk motivasi. Kondisi saat ini yang membentuk pendekatan:

- `KendangBoardView.onDraw` menggambar tiap kendang dari belakang ke depan (`drums.asReversed()`) dalam dua lapis: badan 3D (`drawDrumBody3D`) lalu kulit + cincin dalam + rim (`drawOval`). Geometri sudah dihitung satu kali lewat `mapper` (`center`, `rx`, `ry` dalam piksel) dan dipakai bersama hit-testing.
- Aset kit dibaca oleh `KitRepository` dari tiga sumber: `assets/kits/<id>/` (bawaan), `filesDir/kits/<id>/` (internal), dan `filesDir/dev-kit/` (salinan kerja Mode Developer). Saat ini hanya `kit.json` dan `sounds/*.wav` yang dikenal.
- Pola pasang WAV di Mode Developer (`DevSoundInstaller`): baca stream → validasi → salin ke `sounds/<zoneId>.wav` → perbarui `kit.json` atomik via file sementara. Pemasangan PNG mengikuti pola yang sama.
- `:kit-core` adalah Kotlin/JVM murni (tanpa `android.*`); dekode/resize bitmap tidak boleh berada di sana.
- `DrumData` belum memiliki medan gambar; `kit.json` bawaan (mis. `new-metro`) tidak memiliki medan `image`.
- Lingkungan lokal tidak punya JDK/Android SDK; seluruh build dan test dijalankan di GitHub Actions.

## Goals / Non-Goals

**Goals:**
- Gambar PNG per kendang menjadi bagian modular dari kit (assets, penyimpanan internal, salinan kerja, `.vrkendang`).
- Render gambar murah di `onDraw` dan hemat RAM; fallback aman ke render Canvas bawaan.
- Logika yang tidak membutuhkan Android (skema, validasi, batas paket) berada di `:kit-core` dan teruji JVM.

**Non-Goals:**
- Tidak mengubah hit-testing, audio engine, maupun format zona.
- Tidak membuat aset PNG kit bawaan dan tidak memigrasikan ikon/menu ke SVG.
- Tidak menambah UI untuk mengganti ID kendang; hanya mendefinisikan aturan bila ID berubah.

## Decisions

### D1. Medan `image` eksplisit di `kit.json` (Opsi B), nilai tetap berdasarkan konvensi
`DrumData` mendapat `image: String? = null`. Nilai yang sah hanya `null` atau `images/<drumId>.png`. Alternatif ditolak: *konvensi murni tanpa medan JSON* (Opsi A), karena integritas file tidak dapat divalidasi dari metadata seperti halnya WAV. Karena nilainya sepenuhnya turunan dari `id`, validator menolak nama lain; medan ini tetap berguna sebagai penanda "drum ini bergambar" tanpa memindai disk/ZIP. Default `null` menjaga kit lama tetap terurai tanpa migrasi `formatVersion`.

### D2. Pemetaan gambar ke kotak batas kendang
Gambar digambar dengan `canvas.drawBitmap(bitmap, null, RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)` dengan `Paint` ber-`isFilterBitmap` dan `isAntiAlias`. Karena gambar standar berbentuk bulat dalam kanvas persegi transparan, peregangan ke kotak persegi panjang otomatis menghasilkan oval dan mengikuti perubahan `rx/ry/center` setiap `invalidate()`. Efek denyut (skala ~2,5%) tetap diterapkan pada kotak tujuan. Alternatif ditolak: memisahkan tekstur kulit dan rim vektor (rim sudah menyatu dalam PNG sesuai keputusan produk) serta `drawBitmapMesh` (kompleksitas tidak diperlukan).

### D3. Render bawaan dimatikan per kendang, bukan global
Pada `onDraw`, kendang yang memiliki bitmap termuat melewati `drawDrumBody3D`, kulit, cincin dalam, dan rim; kendang tanpa bitmap memakai jalur lama. Garis panduan zona, label, seleksi, dan highlight tetap digambar sesudahnya sehingga tidak terpengaruh. Urutan `drums.asReversed()` dipertahankan agar Z-order tetap selaras dengan hit-test.

### D4. Pemuatan bitmap di luar `onDraw`, di-cache per kit
Sebuah pemuat gambar kit (di `:app` main) mendekode `images/<drumId>.png` saat kit diaktifkan atau berubah, menghasilkan `Map<drumId, Bitmap>` yang disuntikkan ke `KendangBoardView` (mengikuti pola `slotByZoneId`). Dekode gagal → drum diperlakukan tanpa gambar + log. Bitmap lama dilepas (`recycle`/dereferensi) saat kit berganti. `onDraw` hanya membaca map. Sumber byte gambar diabstraksi seperti `KitAssetSource` agar `KitRepository`/pemuat dapat diuji tanpa perangkat dan mencakup tiga sumber (assets, internal, dev-kit).

### D5. Validasi dan batas paket di `:kit-core`
- `KitValidator`: aturan nama `images/<drumId>.png`, tanpa path absolut/`..`.
- `KitPackageReader`: konstanta `MAX_PNG_SIZE = 2 MB`, entri `images/*.png` dikenali, diperiksa pada tahap validasi struktur sebelum penulisan disk (sama seperti WAV), tunduk pada batas 100 entri dan 100 MB.
- `KitPackageWriter`: menerima penyedia byte PNG (mirip `wavProvider`) dan hanya menulis PNG yang dirujuk.
- Pemeriksaan "file PNG valid" di `:kit-core` cukup memeriksa signature 8 byte PNG dan chunk IHDR yang utuh (tanpa dekode piksel); dekode penuh dilakukan di `:app`.

### D6. Pasang PNG di Mode Developer: validasi, bentuk persegi, downsample, simpan
Installer PNG baru (source set `debug`, sejajar `DevSoundInstaller`) melakukan: baca stream → tolak > 2 MB → validasi signature PNG → periksa dimensi via `inJustDecodeBounds` (tolak jika `width != height` / non-persegi) → dekode dengan `BitmapFactory` memakai `inSampleSize` → skala ke sisi terpanjang ≤ 1024 px (tanpa memperbesar) → tulis `images/<drumId>.png` (via file sementara) → perbarui `image` di `kit.json` atomik → beri tahu sesi agar pemuat memuat ulang bitmap. Kegagalan di tahap mana pun tidak mengubah file maupun `kit.json` lama.

### D7. Siklus hidup file di sesi authoring
`deleteDrum` menghapus `images/<drumId>.png` (sejajar penghapusan WAV zona). Operasi apa pun yang mengganti `id` kendang me-rename file dan memperbarui `image` dalam satu langkah. Saat ini ID hanya dibuat sekali di `addDrum` (tidak ada UI ganti ID); aturan rename didefinisikan di spec dan diimplementasikan sebagai fungsi sesi yang teruji, tanpa menambah UI baru. Ekspor memakai penulis paket yang hanya menyertakan gambar yang dirujuk sehingga PNG yatim tidak ikut terbawa.

## Risks / Trade-offs

- **[OOM karena foto resolusi tinggi]** → batas 2 MB, dekode ber-`inSampleSize`, downsample 1024 px; bitmap 1024x1024 ARGB ≈ 4 MB per kendang. Kit bawaan/impor yang lolos validasi tetap bisa berisi PNG besar dalam piksel (≤ 2 MB terkompresi) → pemuat juga memakai `inSampleSize` agar dimensi tidak melebihi 1024 px saat dekode.
- **[Gambar non-persegi]** → ditolak saat pemasangan dengan pesan galat jelas (wajib rasio 1:1), menjaga aset tetap proporsional sesuai konvensi.
- **[Kit bawaan/internal yang rusak pada `image`]** → validasi kit bawaan di CI (`kit-core` test) menolak sebelum build lolos; di runtime fallback ke render bawaan.
- **[Latensi sentuh]** → dekode tidak terjadi di `onDraw` atau jalur sentuh; `drawBitmap` ke GPU murah.
- **[Kode Mode Developer bocor ke release]** → installer PNG dan UI pilih gambar berada di source set `debug`; pemuat/render PNG di main karena pemain harus melihat gambar kit.

## Migration Plan

- Tidak ada migrasi data: kit lama tanpa `image` tetap valid dan tampil seperti sebelumnya.
- Kit `new-metro` bawaan tidak berubah dalam change ini (tidak ada PNG bawaan).
- Rollback: mengembalikan kode membuat medan `image` diabaikan oleh parser lama; kit tetap terbaca karena medan opsional dan parser mengabaikan kunci yang tidak dikenal bila `ignoreUnknownKeys` aktif (diperiksa saat implementasi).
