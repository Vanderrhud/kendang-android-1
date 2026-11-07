## ADDED Requirements

### Requirement: Rotasi orientasi kendang menyelaraskan render dan hit-testing
Bila sebuah kendang memiliki nilai `rotation` tidak nol, papan SHALL memutar seluruh elemen visual kendang tersebut (badan 3D, kulit/rim Canvas atau gambar PNG, garis panduan zona, dan highlight ketukan) sebesar `rotation` derajat searah jarum jam terhadap titik pusat kendang. Mesin hit-testing SHALL memutar balik titik sentuh sebesar `-rotation` derajat sebelum mengevaluasi batas elips `(rx, ry)` dan pembagian zona di dalamnya.

#### Scenario: Gambar PNG dan elips kendang berotasi bersama garis zona dan highlight
- **WHEN** kendang memiliki bentuk oval (`rx != ry`), gambar PNG, dan `rotation` sebesar 30 derajat
- **THEN** gambar PNG, batas elips, garis panduan zona, dan highlight ketukan tampil miring 30 derajat secara selaras

#### Scenario: Hit-testing pada kendang oval yang dimiringkan tetap akurat
- **WHEN** pemain menyentuh titik di dalam area elips kendang yang sudah dimiringkan 45 derajat (atau di luar elips miring tetapi masih di dalam kotak pembatas tanpa rotasi)
- **THEN** sentuhan di dalam elips miring memicu zona yang tepat dan sentuhan di luar elips miring diabaikan

#### Scenario: Rotasi orientasi kendang bekerja berdampingan dengan sudut sekat zona
- **WHEN** kendang model `split` memiliki `rotation` kendang 30 derajat dan `divider.angle` 45 derajat
- **THEN** elips kendang dan gambar PNG berotasi 30 derajat, sedangkan garis sekat zona di dalam elips berotasi 45 derajat relatif terhadap orientasi elips tersebut
