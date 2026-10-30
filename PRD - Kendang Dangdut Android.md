# PRD: Kendang Dangdut Android

Status: Draf 1, berdasarkan hasil diskusi. Bagian "Belum Diputuskan" sengaja dibiarkan terbuka, jangan diisi dengan asumsi.

## 1. Ringkasan

Aplikasi kendang dangdut untuk Android (HP dan tablet). Konsepnya mirip drum kit: setiap "kit" mewakili satu set kendang dari sebuah orkes dangdut, dengan susunan dan karakter suara yang berbeda. Fokusnya murni musik, bukan budaya atau daerah.

## 2. Tujuan

- Pemain bisa memilih kit lalu langsung bermain kendang di layar sentuh.
- Terasa lebih realistis daripada aplikasi "1 pad = 1 suara" dengan membagi satu kulit kendang menjadi beberapa area suara.
- Struktur siap menambah kit baru di versi berikutnya tanpa mengubah arsitektur.

## 3. Target Pengguna

Semua kalangan: dari yang ingin mencoba santai sampai yang ingin bermain kendang. Perangkat: HP dan tablet Android.

## 4. Cakupan Versi Awal

**Termasuk:**

- Satu kit bawaan: **New Metro** (gaya kendang jaipong, dari orkes dangdut New Metro)
- Pilih kit lalu bermain
- Mode landscape saja, orientasi dikunci
- Header untuk navigasi menu, dengan ruang yang disiapkan untuk iklan di masa depan
- Tombol pilih kit tetap ada sejak awal (menampilkan New Metro sebagai satu-satunya pilihan)

**Di luar cakupan (belum untuk versi awal):**

- Rekam permainan
- Metronom
- Musik pengiring
- Pengaturan volume atau karakter suara per kendang
- Gestur selain tap (tahan, geser, dan sebagainya)
- Variasi rekaman per area
- Kit tambahan dan penambahan kit oleh pemain

## 5. Kit New Metro

### 5.1 Isi kit

Empat kendang. Nama di bawah adalah nama kode dan bisa diganti nanti tanpa mengubah struktur.

| Kendang | Ukuran | Bentuk kulit | Jumlah area suara |
| --- | --- | --- | --- |
| Bass | Paling besar, kendang utama | Lingkaran penuh | 5 |
| Sedang | Sedang | Oval | 2 |
| Kecil 1 | Kecil | Oval | 1 |
| Kecil 2 | Kecil | Oval | 2 |

Total: **10 area suara**.

### 5.2 Tata letak

- Bass di sisi kiri.
- Kecil 1 di atas, condong ke kiri (di atas celah antara Bass dan Sedang, tidak menyentuh keduanya).
- Sedang di kanan Bass.
- Kecil 2 di sebelah kanan Sedang, posisinya sedikit lebih rendah.
- Tidak ada kendang yang saling menutupi atau tumpang tindih.
- Tata letak adalah bagian dari data kit, bukan ditulis mati di kode. Kit lain di masa depan boleh punya susunan berbeda (horizontal, bertumpuk, atau campuran), dengan Bass tetap di kiri.

Sketsa kasar (bukan skala):

```
      [Kecil 1]

[  Bass  ]  [Sedang]
                      [Kecil 2]
```

### 5.3 Pembagian area suara

Hanya kulit (oval atau lingkaran) yang bisa disentuh. Badan kendang adalah hiasan visual dan tidak membunyikan apa pun.

**Bass (5 area)**

- Area 1: bulan sabit tebal di sisi kiri lingkaran, batas kanannya melengkung mengikuti kurva (sesuai sketsa tulisan tangan).
- Area 2, 3, 4, 5: sisa lingkaran di kanan Area 1, dipotong mendatar dengan garis lurus berjarak sama (sama tinggi), urut dari atas ke bawah.
- Lebar pasti Area 1: belum diputuskan.

**Sedang (2 area)**

- Dibagi atas dan bawah oleh garis lurus tepat di tengah.
- Bawah = Area 1, atas = Area 2. Penomoran hanya urutan, tanpa makna "utama".

**Kecil 1 (1 area)**

- Satu area untuk seluruh kulit.

**Kecil 2 (2 area)**

- Sama seperti Sedang: atas dan bawah, garis tengah, bawah = 1, atas = 2.

**Kode suara:** Bass-1 sampai Bass-5, Sedang-1, Sedang-2, Kecil1-1, Kecil2-1, Kecil2-2.

## 6. Perilaku Bermain

- Suara berbunyi saat jari menyentuh area (tap), bukan saat jari diangkat.
- Satu sentuhan membunyikan tepat satu area.
- Tidak ada gestur lain. Menggeser jari dari satu area ke area lain tidak membunyikan apa pun.
- Tidak ada batas jumlah jari di sisi aplikasi. Semua sentuhan bersamaan dibunyikan. Batas multitouch layar mengikuti perangkat.
- Satu rekaman per area. Pukulan berulang atau rolling pada area yang sama **menumpuk**, suara lama tidak dipotong.
- Pemain tidak mengatur volume atau karakter suara. Cukup pilih kit lalu main.

## 7. Suara

- Aset suara dibuat dan diisi sendiri oleh pemilik produk, belum tersedia saat ini.
- Format: WAV atau OGG (belum diputuskan).
- Karena aset belum ada, perlu suara pengganti sementara supaya tata letak dan area sentuh bisa diuji lebih dulu.
- Karakter suara tiap area tidak dibahas di dokumen ini.

## 8. Tampilan

- Gaya "jalan tengah": terinspirasi bentuk kendang asli, tetapi area sentuh diatur agar nyaman di layar.
- Tiap kendang ditampilkan satu sisi saja (sisi kulit).
- Bass berupa lingkaran penuh. Kendang lain berupa oval seperti lingkaran yang ditekan dari atas (lebih lebar daripada tinggi), tidak terlalu ekstrem agar area pukul tidak sempit.
- Kesan 3D: badan kendang ditarik dari tepi kiri dan kanan kulit ke arah bawah, seperti kendang berdiri. Semua kendang, termasuk Bass, punya badan. Bentuk badan fleksibel dan bukan fokus utama.
- Area sentuh dipisahkan dari gambar, sehingga gambar bisa diganti tanpa mengubah area sentuh.
- Efek saat dipukul: kulit kendang berdenyut. Detail efek dibahas belakangan.
- Layar punya header untuk navigasi menu dan ruang untuk iklan di kemudian hari.

## 9. Persyaratan Non-Fungsional

- Latensi dari sentuhan ke suara serendah mungkin, karena ini kunci rasa realistis.
- Mendukung banyak suara aktif bersamaan (suara menumpuk, rolling cepat, banyak jari).
- Berjalan di HP dan tablet Android dalam layout landscape.

## 10. Belum Diputuskan

- Format aset suara (WAV atau OGG)
- Gambar atau ilustrasi kendang, warna, latar belakang layar
- Isi menu di header
- Header selalu terlihat atau bisa disembunyikan saat bermain
- Penempatan iklan, dan agar tidak mengganggu tap saat bermain
- Batas jumlah suara aktif bersamaan (ditentukan di tahap teknis)
- Detail efek denyut
- Lebar pasti Area 1 di Bass
- Tech stack (dibahas terpisah)

## 11. Kriteria Keberhasilan Versi Awal

- Kit New Metro tampil dengan 4 kendang sesuai tata letak, tanpa tumpang tindih.
- Tap di tiap dari 10 area membunyikan suara area yang benar dan hanya area itu.
- Tap di badan kendang tidak membunyikan apa-apa.
- Tap berulang atau rolling pada satu area menumpuk tanpa memotong suara sebelumnya.
- Multitouch membunyikan semua sentuhan yang diterima perangkat.
- Kulit berdenyut saat dipukul.
- Aplikasi terkunci landscape dan bisa diuji dengan suara pengganti sementara.
