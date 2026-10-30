# Rilis Kit Bawaan

Dokumen ini menjelaskan langkah manual mengubah kit hasil Mode Developer menjadi kit bawaan di dalam APK. Aplikasi tidak menyediakan fitur ekspor khusus untuk ini; paket `.vrkendang` yang valid dapat diekstrak apa adanya.

## Prasyarat

- Kit sudah selesai diuji di Mode Developer.
- `kitId` kit valid (huruf kecil, angka, tanda hubung) dan tidak sama dengan `kitId` kit bawaan lain.
- Kit disimpan lewat "Simpan" atau "Simpan Sebagai" ke file `.vrkendang`.

## Langkah rilis

1. Pastikan isi paket `.vrkendang` hanya berisi `kit.json` di akar paket dan file WAV yang dirujuk oleh `kit.json` di `sounds/<zoneId>.wav`. File WAV yatim tidak boleh ikut.
2. Ekstrak paket apa adanya ke `app/src/main/assets/kits/<kitId>/`:

   ```bash
   mkdir -p app/src/main/assets/kits/<kitId>
   unzip -o path/to/<kitId>.vrkendang -d app/src/main/assets/kits/<kitId>
   ```

3. Periksa hasil ekstraksi:
   - `app/src/main/assets/kits/<kitId>/kit.json` berada di akar direktori kit.
   - Semua file WAV berada di `app/src/main/assets/kits/<kitId>/sounds/`.
   - Setiap nilai `sound` di `kit.json` menunjuk ke file yang benar-benar ada dan berformat WAV PCM 16-bit mono.
4. Jalankan validasi kit bawaan dan unit test `:kit-core`:

   ```bash
   ./gradlew :kit-core:test
   ```

5. Commit dan push ke branch `main`:

   ```bash
   git add app/src/main/assets/kits/<kitId>
   git commit -m "feat: add <kitId> built-in kit"
   git push origin main
   ```

6. CI akan menjalankan unit test, memvalidasi seluruh kit bawaan, mengompilasi varian release tanpa source set debug, dan membangun APK debug. Kit baru muncul di daftar kit aplikasi.

## Catatan

- Jangan membuat atau menyimpan file cadangan `.bak`; alur simpan kit memang tidak membuat cadangan otomatis.
- `kitId` tidak boleh diubah setelah kit dibuat agar override pemain dan rujukan kit tidak putus.
- Jika validasi kit bawaan gagal, CI harus gagal dan kit tidak boleh dirilis.
