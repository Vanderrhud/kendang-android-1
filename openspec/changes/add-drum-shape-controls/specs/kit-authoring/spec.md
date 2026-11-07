## ADDED Requirements

### Requirement: Kontrol Ukuran, Kelonjongan, dan Kemiringan Kendang di Mode Developer
Pada kotak Posisi dan Ukuran Kendang di Mode Developer, sistem SHALL menyediakan slider Ukuran (0.08–0.50 untuk jari-jari terpanjang), slider Kelonjongan (-100% sampai +100%, dengan 0% bulat sempurna, nilai negatif melebar mendatar, nilai positif meninggi tegak, dan ketebalan minimum 10% pada ujung ekstrem), slider Kemiringan/Rotasi (0–360 derajat), serta tombol Reset Bentuk yang mengembalikan kelonjongan ke 0% dan kemiringan ke 0 derajat.

#### Scenario: Menggeser slider kelonjongan ke kiri atau kanan membentuk kendang oval
- **WHEN** developer menggeser slider Kelonjongan ke nilai negatif (misal -50%) atau positif (misal +50%)
- **THEN** nilai `rx` dan `ry` kendang diperbarui membentuk elips mendatar atau tegak secara real-time di pratinjau

#### Scenario: Menggeser slider ukuran mempertahankan persentase kelonjongan
- **WHEN** kendang memiliki kelonjongan -50% lalu developer menggeser slider Ukuran
- **THEN** ukuran kendang membesar atau mengecil dengan rasio kelonjongan tetap -50%

#### Scenario: Mencubit kendang di papan pratinjau mempertahankan rasio kelonjongan
- **WHEN** developer mencubit (pinch) kendang oval di papan pratinjau
- **THEN** `rx` dan `ry` dikalikan faktor skala yang sama sehingga rasio kelonjongan tidak berubah

#### Scenario: Menggeser slider kemiringan memutar orientasi kendang secara real-time
- **WHEN** developer menggeser slider Kemiringan/Rotasi ke 35 derajat
- **THEN** `rotation` kendang di `kit.json` menjadi 35.0 dan kendang beserta gambar PNG serta garis zonanya tampak miring 35 derajat di pratinjau

#### Scenario: Menekan tombol Reset Bentuk mengembalikan kendang ke bulat tegak
- **WHEN** developer menekan tombol `↺ Reset Bentuk` pada kendang yang lonjong dan miring
- **THEN** kelonjongan kembali ke 0% (`rx == ry` mengikuti Ukuran saat ini) dan `rotation` kembali ke 0 derajat

### Requirement: Pemisahan rotasi orientasi kendang dan sudut sekat zona
Pada kendang non-bass yang memiliki dua zona atau lebih, Mode Developer SHALL tetap menampilkan kotak Parameter Putaran Sudut (`divider.angle`) secara terpisah dari slider Kemiringan/Rotasi kendang, sehingga developer dapat mengatur kemiringan elips kendang sekaligus memutar garis sekat zona di dalam elips secara independen.

#### Scenario: Mengubah sudut sekat zona tidak mengubah kemiringan elips kendang
- **WHEN** developer mengubah slider pada kotak Parameter Putaran Sudut untuk kendang `split` yang sudah dimiringkan
- **THEN** hanya garis sekat zona di dalam elips yang berputar (`divider.angle`), sedangkan kemiringan elips dan gambar PNG kendang (`rotation`) tetap pada sudutnya
