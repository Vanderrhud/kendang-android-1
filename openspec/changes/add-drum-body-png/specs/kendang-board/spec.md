## MODIFIED Requirements

### Requirement: Render bawaan Canvas dimatikan untuk kendang bergambar
Untuk kendang yang memiliki gambar PNG kulit (`image`) yang berhasil dimuat, papan SHALL tidak menggambar kulit, cincin dalam, maupun rim bawaan Canvas. Badan 3D prosedural bawaan Canvas SHALL hanya digambar bila kendang tidak memiliki gambar PNG kulit maupun gambar PNG body yang berhasil dimuat. Kendang tanpa gambar kulit dan tanpa gambar body SHALL tetap memakai render bawaan Canvas seperti sebelumnya.

#### Scenario: Kendang bergambar tidak menampilkan render bawaan
- **WHEN** sebuah kendang memiliki gambar PNG kulit yang berhasil dimuat
- **THEN** tidak ada badan 3D prosedural, kulit, cincin dalam, atau rim bawaan yang tergambar untuk kendang tersebut

#### Scenario: Kendang tanpa gambar tetap memakai render bawaan
- **WHEN** sebuah kendang memiliki `image` null dan `body` null
- **THEN** kendang tampil dengan render bawaan Canvas (badan 3D + kulit oval) seperti sebelumnya, berdampingan dengan kendang lain yang bergambar

#### Scenario: Highlight sentuhan tetap tampil di atas gambar
- **WHEN** pemain menyentuh zona pada kendang bergambar kulit dan/atau body
- **THEN** visual highlight zona tampil presisi di atas permukaan kulit kendang, termasuk pada zona tanpa suara

## ADDED Requirements

### Requirement: Render gambar PNG body kendang dengan transformasi parent-child dan urutan Z-order per kendang
Papan SHALL menggambar setiap kendang dari belakang ke depan (`drums.asReversed()`) dengan merender body terlebih dahulu lalu kulit di atasnya. Bila kendang memiliki PNG `body` valid, papan SHALL menggambarnya dengan transformasi parent-child terhadap pusat dan rotasi kendang induk (`drum.center`, `drum.rotation`) ditambah `offsetX`, `offsetY`, `width`, `height`, dan `body.rotation`. Body bersifat murni visual dan tidak memengaruhi hit-testing maupun denyut kulit.

#### Scenario: Kulit kendang selalu berada di atas body miliknya sendiri dan mengikuti urutan Z-order antar kendang
- **WHEN** Kendang Depan (indeks 0) dan Kendang Belakang (indeks 1) sama-sama memiliki PNG kulit dan PNG body serta saling berdekatan
- **THEN** pada masing-masing kendang kulit selalu tampak di atas body miliknya, dan body maupun kulit Kendang Depan tampak di atas Kendang Belakang

#### Scenario: Body otomatis ikut bergeser dan berputar saat kendang induk digeser atau dirotasi
- **WHEN** posisi `center` atau sudut `rotation` kendang induk diubah
- **THEN** gambar PNG body otomatis ikut bergeser dan berputar searah bersama kendang induk dengan mempertahankan offset dan kemiringan relatifnya

#### Scenario: Sentuhan pada area gambar body di luar elips kulit tidak memicu ketukan
- **WHEN** pemain menyentuh area gambar PNG body yang berada di luar batas elips kulit kendang
- **THEN** sentuhan tidak memicu ketukan suara maupun highlight pada kendang tersebut
