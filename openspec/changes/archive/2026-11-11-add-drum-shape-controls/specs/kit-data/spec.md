## MODIFIED Requirements

### Requirement: Aturan validasi identitas dan geometri kit.json
Sistem SHALL memvalidasi identitas dan geometri `kit.json`: `formatVersion` dikenali; `kitId` hanya huruf kecil, angka, dan tanda hubung; semua `id` kendang dan zona unik; `canvasAspect` > 0; `center.x` dan `center.y` di rentang 0.0–1.0 (atau -3.0–3.0 dengan retensi kanvas); `rx` dan `ry` > 0 dan ≤ 5.0; serta medan opsional `rotation` per kendang berada di rentang 0.0–360.0 derajat (diperlakukan `0.0` bila tidak ada pada kit lama).

#### Scenario: Pelanggaran format identitas ditolak
- **WHEN** `kit.json` memiliki `kitId` dengan huruf besar/karakter ilegal, terdapat duplikasi `id`, atau `formatVersion` tidak dikenali
- **THEN** validasi menolak kit dengan pesan galat identitas tidak valid

#### Scenario: Pelanggaran batas koordinat kanvas atau radius ditolak
- **WHEN** `kit.json` memiliki `canvasAspect <= 0`, koordinat `center` di luar batas valid, atau radius `rx`/`ry` ≤ 0 atau > 5.0
- **THEN** validasi menolak kit dengan pesan galat rentang geometri tidak valid

#### Scenario: Pelanggaran rentang rotasi kendang ditolak
- **WHEN** suatu kendang di `kit.json` memiliki nilai `rotation` kurang dari 0.0 atau lebih dari 360.0
- **THEN** validasi menolak kit dengan pesan galat rentang rotasi kendang tidak valid

#### Scenario: Kit lama tanpa medan rotation diperlakukan berotasi 0 derajat
- **WHEN** `kit.json` yang tidak memiliki medan `rotation` pada drum di-parse
- **THEN** setiap drum memiliki nilai `rotation` default `0.0` dan lolos validasi
