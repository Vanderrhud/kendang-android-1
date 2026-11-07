# Proposal

## Why

Tampilan kendang saat ini digambar sepenuhnya oleh kode Canvas (oval kulit, cincin dalam, rim, dan badan 3D), sehingga setiap kit tampil sama dan tidak bisa mengikuti rupa instrumen asli. Karena arsitektur VR Kendang berbasis data dan modular (suara per zona sudah menjadi bagian kit), tampilan kendang juga perlu menjadi bagian kit: satu gambar PNG per kendang yang dipasang developer dari HP, tanpa build ulang.

## What Changes

- Setiap kendang dalam kit dapat memiliki **satu gambar PNG** (persegi, latar transparan, kendang berbentuk bulat penuh dengan rim sudah menyatu di dalam gambar). Gambar dipetakan ke kotak batas kendang (`center ± radius`), sehingga bentuk oval (`rx != ry`) dan perubahan posisi/ukuran (Mode Developer maupun Mode Sesuaikan) langsung diikuti gambar.
- Kolom opsional baru `image` pada tiap drum di `kit.json`: `"images/<drumId>.png"` atau `null` bila belum dipasang. Nama file **wajib identik** dengan `id` kendang; tidak boleh nama bebas.
- Bila kendang memiliki gambar yang valid, **seluruh render bawaan Canvas untuk kendang itu dimatikan** (badan 3D, kulit, cincin dalam, rim). Hanya PNG, garis panduan/label sesuai mode, dan highlight sentuhan yang tampil. Bila `image` null atau gambar gagal dimuat, render bawaan Canvas tetap dipakai (*graceful fallback*, tanpa crash).
- Mode Developer: tombol pasang gambar PNG per kendang lewat Android System File Picker (`ACTION_OPEN_DOCUMENT`), pola yang sama dengan pemasangan WAV. Gambar divalidasi (PNG valid, maks 2 MB), di-*downsample* ke sisi terpanjang maks 1024 px, lalu disimpan ke `images/<drumId>.png` di salinan kerja dan `kit.json` diperbarui.
- Siklus hidup file terikat pada kendang: hapus kendang menghapus `images/<drumId>.png`; bila ID kendang berubah, file PNG ikut di-rename.
- Paket `.vrkendang` memuat `images/*.png` yang dirujuk (tanpa PNG yatim). Batas keamanan baru: maks 2 MB per file PNG, tetap tunduk pada batas 100 entri dan 100 MB total, serta perlindungan path traversal.
- Validator `:kit-core` memeriksa `image`: harus tepat `images/<drumId>.png`, bukan path absolut, tanpa `..`; bila tidak null, file harus ada dan berformat PNG valid.

**Di luar cakupan**: migrasi ikon/menu/header ke SVG (ditunda; iterasi ini hanya aset PNG kendang), pembuatan aset PNG kit bawaan *New Metro*, tekstur/animasi deformasi kulit, dan perubahan hit-testing atau audio engine.

## Capabilities

### New Capabilities

*(Tidak ada. Perubahan memperluas kapabilitas yang sudah ada.)*

### Modified Capabilities

- `kit-data`: skema `kit.json` mendapat kolom opsional `image` per drum; aturan validasi gambar; paket `.vrkendang` memuat dan memvalidasi `images/*.png` dengan batas 2 MB per file; kit lama tanpa `image` tetap valid.
- `kendang-board`: render kendang memakai PNG bila tersedia (dipetakan ke kotak batas, mengikuti posisi/ukuran/oval, urutan Z-order tetap), mematikan render bawaan Canvas untuk kendang bergambar, dan fallback aman ke render bawaan.
- `kit-authoring`: pasang gambar PNG per kendang dari penyimpanan HP, validasi + downsample, hapus/ganti gambar, serta pengikatan siklus hidup PNG pada hapus/rename kendang; ekspor/impor `.vrkendang` menyertakan gambar.

## Impact

- **`:kit-core`**: `DrumData` (+ `image`), `KitValidator`, `KitPackageReader` (batas PNG 2 MB, entri `images/`), `KitPackageWriter` (menulis PNG yang dirujuk), serta unit test terkait (termasuk validasi seluruh `kit.json` bawaan).
- **`:app` (main)**: `KendangBoardView` (render PNG + fallback), pemuat/cache bitmap gambar kit, `KitRepository` (sumber gambar dari assets, `filesDir/kits/`, dan `dev-kit`).
- **`:app` (debug)**: `DevKitAuthoringSession`, `DevPickerFragment`, dan installer PNG baru (validasi, downsample, salin, perbarui `kit.json`); kode ini tetap hanya di source set `debug`.
- **Memori**: bitmap dimuat di luar `onDraw` dan di-*downsample* (maks 1024 px per sisi) agar konsumsi RAM seluruh kit tetap kecil.
- **Kompatibilitas**: kit lama tanpa `image` tidak berubah tampilannya; tidak ada dependensi library baru.
