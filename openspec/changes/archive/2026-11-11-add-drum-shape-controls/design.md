# Design

## Context

Lihat `proposal.md` untuk motivasi perubahan. Saat ini `DrumData` di `:kit-core` sudah menyimpan `radius: Radius(rx, ry)` dan `HitTester` serta `KendangBoardView` sudah mendukung elips ortogonal (`rx != ry`). Namun:
1. `DevAuthoringActivity` hanya menyediakan satu slider `Radius/Ukuran` yang menulis `Radius(newR, newR)`, sehingga pembuat kit tidak dapat mengubah bentuk kendang menjadi oval dari UI Mode Developer.
2. `DrumData` belum memiliki atribut orientasi/kemiringan (`rotation`) untuk memutar elips kendang secara utuh guna menciptakan efek perspektif *Point of View* (POV), sementara `divider.angle` hanya memutar garis sekat zona di dalam kendang model `split`.

## Goals / Non-Goals

**Goals:**
- Menyediakan pemetaan dua arah yang deterministik dan tanpa kehilangan presisi antara pasangan slider **(Ukuran $S$, Kelonjongan $E$)** di UI Mode Developer dengan `(rx, ry)` di `kit.json`.
- Mendukung rotasi orientasi kendang (`rotation: Float = 0f` dalam derajat `0.0..360.0`) secara konsisten di model data, validasi, hit-testing aljabar murni (`:kit-core`), dan rendering Canvas (`:app`).
- Mempertahankan pemisahan antara kemiringan orientasi kendang (`drum.rotation`) dan putaran sudut garis sekat zona internal (`drum.divider.angle`).

**Non-Goals:**
- Tidak mengubah format `LayoutOverride` pemain di Mode Sesuaikan (pemain tetap hanya menggeser posisi `center` dan mengubah `scale` seragam; kelonjongan dan kemiringan kendang yang dirancang pembuat kit tetap terjaga).
- Tidak menambahkan gestur putar dua jari (*two-finger twist rotation*) di layar sentuh guna menghindari konflik dengan gestur cubit (*pinch-to-scale*).

## Decisions

### 1. Pemetaan Matematika Slider Ukuran ($S$) dan Kelonjongan ($E \in [-100\%, +100\%]$) ke `(rx, ry)`
- **Keputusan**:
  - **Ukuran ($S$)** merepresentasikan jari-jari terpanjang kendang: $S = \max(rx, ry)$ dengan rentang slider `0.08..0.50`.
  - **Kelonjongan ($E$)** memiliki rentang `-100.0..+100.0` (dalam persen) dengan faktor ketebalan minimum `MIN_ASPECT_RATIO = 0.10f` (10% dari $S$ pada ujung $\pm 100\%$):
    - Jika $E == 0$: $rx = S$, $ry = S$ (bulat sempurna).
    - Jika $E < 0$ (lonjong mendatar / pipih vertikal):
      $$rx = S, \quad ry = S \times \left(1.0 - 0.90 \times \frac{|E|}{100.0}\right)$$
    - Jika $E > 0$ (lonjong tegak / pipih horizontal):
      $$ry = S, \quad rx = S \times \left(1.0 - 0.90 \times \frac{E}{100.0}\right)$$
  - **Invers dari `(rx, ry)` ke `(S, E)`**:
    - $S = \max(rx, ry)$
    - Jika $rx \ge ry$ dan $rx > 0$: $E = -\left(\frac{1.0 - (ry / rx)}{0.90}\right) \times 100.0$ (dijepit ke `-100.0..0.0`).
    - Jika $ry > rx$ dan $ry > 0$: $E = +\left(\frac{1.0 - (rx / ry)}{0.90}\right) \times 100.0$ (dijepit ke `0.0..+100.0`).
- **Alasan**:
  - Tidak memerlukan field baru untuk kelonjongan di `kit.json` karena `(rx, ry)` sudah menyimpan nilai eksaknya.
  - Saat kendang dicubit di layar (`rx' = rx * scaleFactor`, `ry' = ry * scaleFactor`), rasio $ry'/rx'$ tidak berubah sama sekali sehingga nilai slider Kelonjongan ($E$) tetap stabil dan hanya slider Ukuran ($S$) yang bergerak.
  - Ketebalan minimum 10% pada $\pm 100\%$ menjamin `rx > 0` dan `ry > 0` selalu terpenuhi sesuai aturan `KitValidator`.
- **Alternatif yang dipertimbangkan**:
  - Dua slider `rx` dan `ry` terpisah: ditolak karena menyulitkan developer saat hanya ingin memperbesar kendang bulat tanpa mengubah kelonjongan.

### 2. Urutan Transformasi Rotasi Kendang (`drum.rotation`) pada Render dan Hit-Testing
- **Keputusan**:
  - Tambahkan `@EncodeDefault val rotation: Float = 0f` (atau default `0f`) pada `DrumData` dengan rentang valid `0.0f..360.0f` derajat searah jarum jam.
  - **Pada Render (`KendangBoardView`, `ZoneHighlightDrawer`, `ZoneGuideOverlayProvider`)**:
    Untuk setiap kendang, lakukan `canvas.save()`, lalu jika `drum.rotation != 0f` panggil `canvas.rotate(drum.rotation, cx, cy)` sebelum menggambar badan 3D, kulit/rim Canvas atau gambar PNG, elips seleksi, garis panduan zona, dan highlight ketukan, kemudian `canvas.restore()`. Label teks nama kendang dan lencana teks zona digambar di luar rotasi kanvas (atau titik pusatnya diputar tetapi teks tetap tegak) agar mudah dibaca.
  - **Pada Hit-Testing (`HitTester.findHit`)**:
    Hitung vektor relatif sentuhan terhadap pusat kendang dalam satuan kanvas:
    $$\Delta x = x_u - cx_u, \quad \Delta y = y_u - cy_u$$
    Putar balik vektor tersebut sebesar $-\text{rotation}$ (dalam radian $\theta = -\text{toRadians}(\text{rotation})$):
    $$\Delta x_{rot} = \Delta x \cos\theta - \Delta y \sin\theta$$
    $$\Delta y_{rot} = \Delta x \sin\theta + \Delta y \cos\theta$$
    Gunakan $x'_{u} = cx_u + \Delta x_{rot}$ dan $y'_{u} = cy_u + \Delta y_{rot}$ untuk uji di dalam elips $(\Delta x_{rot}/rx)^2 + (\Delta y_{rot}/ry)^2 \le 1.0$ serta evaluasi zona internal (`findBassZone` dan `findSplitZone`).
- **Alasan**:
  - Dengan memutar balik titik sentuh ke kerangka koordinat lokal kendang, seluruh logika pembagian zona yang sudah ada (`bass` dengan busur bulan sabit `C_div` & sekat `splits`, maupun `split` dengan `divider.angle` 1–4 zona) langsung bekerja secara otomatis dan presisi 100% di dalam elips yang miring tanpa menduplikasi rumus geometri zona.

### 3. Koeksistensi `drum.rotation` dan `drum.divider.angle`
- **Keputusan**:
  - `drum.rotation` berada di kotak **POSISI & UKURAN KENDANG** dan berlaku untuk semua model kendang (`bass`, `split`, `single`), memutar keseluruhan kendang beserta PNG dan sistem koordinat lokalnya.
  - `drum.divider.angle` tetap berada di kotak **PARAMETER PUTARAN SUDUT** khusus kendang non-bass dengan $\ge 2$ zona, yang mengatur sudut garis pembagi zona relatif terhadap sumbu lokal elips kendang.
- **Alasan**:
  - Sesuai keputusan desain pengguna (Opsi B), pembuat kit dapat memiringkan bentuk visual kendang untuk efek perspektif POV sekaligus tetap bebas mengatur sudut pembagian area ketuk di dalam kendang tersebut.

## Risks / Trade-offs

- **[Risk] Badan kayu 3D bawaan Canvas ikut berotasi saat `drum.rotation` diubah pada kendang tanpa PNG** $\rightarrow$ **Mitigation**: Rotasi kanvas diterapkan konsisten sehingga badan 3D tetap menempel pada tepi kiri-kanan elips lokal kendang; untuk ilusi POV kustom tingkat lanjut, pengguna menggunakan aset gambar PNG.
- **[Risk] Pembulatan float saat konversi bolak-balik `(S, E)` dan `(rx, ry)`** $\rightarrow$ **Mitigation**: Nilai slider Kelonjongan dibulatkan ke bilangan bulat persen terdekat saat ditampilkan di label UI, dan tombol `↺ Reset Bentuk` secara eksplisit menyetel `rx = S, ry = S, rotation = 0f`.
