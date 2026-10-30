# Proposal

## Why

Belum ada aplikasi kendang dangdut Android yang membagi satu kulit kendang menjadi beberapa zona akustik realistis (seperti karakter pukulan pada instrumen fisik), dengan latensi audio serendah mungkin dan arsitektur kit yang sepenuhnya berbasis data sehingga konten dapat diubah dan dikalibrasi tanpa perlu build ulang. Proyek ini membangun fondasi aplikasi tersebut dari nol, dimulai dengan satu kit bawaan (New Metro) sebagai bukti konsep yang bisa langsung dimainkan.

## What Changes

- Membuat proyek Android baru (Kotlin Native) dari nol dengan struktur Gradle multi-modul yang mendukung kode C++ via NDK.
- Mengimplementasikan mesin audio berbasis **Oboe (C++ NDK)** dengan Voice Pool 32 suara simultan, Voice Stealing, dan Fast Audio Path otomatis sesuai hardware device.
- Mengimplementasikan `KendangBoardView` — satu Custom View tunggal yang menangani rendering seluruh kendang dan semua event multi-touch secara terpusat.
- Mengimplementasikan sistem hit-testing berbasis matematika murni (aljabar, tanpa ketergantungan library Android) yang bisa diuji unit test secara otomatis di CI.
- Merancang format JSON kit yang modular dan data-driven: satu folder per kit berisi `kit.json` dan folder suara WAV.
- Mengimplementasikan **Mode Kalibrasi** (dev/player mode) yang memungkinkan pengaturan posisi dan ukuran kendang langsung di layar HP, dengan ekspor hasilnya ke `kit.json` melalui Android Share Sheet — tanpa perlu build ulang.
- Menyertakan generator suara WAV dummy (10 nada berfrekuensi unik) agar APK dari build pertama CI/CD sudah bisa dibunyikan dan diuji di HP.
- Mengkonfigurasi GitHub Actions untuk build APK debug otomatis setiap push ke branch `main`, menggunakan Oboe via Prefab (bukan git submodule) agar build CI cepat dan andal.
- Mengimplementasikan Scoped Storage Android yang benar: kit dari storage HP diprioritaskan di atas kit bawaan di APK `assets/`, dengan path yang aman dan bebas izin kompleks.
- Mengimplementasikan penanganan siklus hidup audio: pause/resume Oboe stream saat aplikasi masuk background, reconnect otomatis saat headphone dicolok/dicabut.
- Mengimplementasikan `FLAG_KEEP_SCREEN_ON` agar layar tidak mati saat pemain sedang bermain.
- Mengimplementasikan Immersive Sticky Mode untuk mencegah jari pemain tidak sengaja memicu nav bar sistem saat rolling cepat.

## Capabilities

### New Capabilities

- `kendang-board`: Tampilan dan interaksi papan kendang — rendering seluruh kulit kendang sebagai kesatuan visual utuh, penanganan multi-touch terpusat, dan hit-testing zona akustik per kendang.
- `audio-engine`: Mesin audio berbasis Oboe C++ NDK — pemuatan WAV ke memori, Voice Pool 32 slot, Voice Stealing, Fast Audio Path, siklus hidup audio (pause/resume/reconnect).
- `kit-data`: Sistem data kit berbasis JSON — pembacaan `kit.json` dari assets atau storage, model data kit (kendang, bentuk, posisi relatif, area, nama file suara), dukungan Dual-Source (built-in vs override storage).
- `calibration-mode`: Mode kalibrasi tata letak — geser posisi dan atur ukuran kendang langsung di layar HP, simpan dan ekspor hasilnya ke `kit.json` via Android Share Sheet.
- `ci-build`: Infrastruktur build dan CI — konfigurasi GitHub Actions untuk APK debug otomatis setiap push, menggunakan Oboe via Prefab Maven, unit test hit-testing dan pembacaan JSON berjalan di workflow.

### Modified Capabilities

*(Tidak ada — ini adalah proyek baru dari nol, tidak ada spec existing yang dimodifikasi.)*

## Impact

- **Proyek baru dari nol:** Seluruh struktur direktori Android (Gradle, Kotlin, C++ CMake), konfigurasi GitHub Actions, dan aset placeholder dibuat dari awal.
- **Dependensi baru:** Oboe (via `google.oboe:oboe` Maven/Prefab), Android NDK, CMake.
- **Format data:** Skema JSON kit baru dirancang khusus untuk proyek ini (bukan format standar industri mana pun).
- **Minimum API:** Android 8.0 (API 26) — merupakan batas bawah yang mendukung AAudio/Oboe low-latency path.
- **Orientasi:** Landscape saja, dikunci di level Activity.
- **Aset suara:** 10 file WAV dummy di-generate otomatis oleh script build. Aset rekaman asli kendang disediakan oleh pemilik produk secara terpisah dan akan menggantikan file dummy di versi berikutnya.
