package com.vrkendang.kitauthoring

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.vrkendang.audio.AudioEngine
import com.vrkendang.audio.KitAudioLoader
import com.vrkendang.kitcore.layout.LayoutMerger
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.kitcore.model.ZoneData
import com.vrkendang.view.KendangBoardView
import java.io.File
import kotlin.math.roundToInt

/**
 * Halaman khusus layar penuh untuk Mode Developer — Studio Penyusun Kit (Tahap 11).
 *
 * Dirancang sebagai Activity mandiri yang bersih (tidak menumpuk dialog di atas layar bermain)
 * dengan 4 sub-halaman (Tab) terstruktur:
 * 1. File Kit: Buat Kit Baru, Buka .vrkendang, Buka Kit Bawaan, Ubah Nama, Simpan / Simpan Sebagai.
 * 2. Isi Sampel WAV (10 Zona): Peta kendang interaktif di kiri + daftar 10 zona & kontrol WAV/Gain di kanan.
 * 3. Posisi & Busur Bass: Kanvas geser kendang live di kiri + slider ukuran, prioritas, dan busur Bass di kanan.
 * 4. Uji Main: Papan kendang penuh untuk menguji ketukan multi-touch & rolling seluruh zona.
 */
class DevAuthoringActivity : Activity() {

    private companion object {
        private const val REQ_PICK_WAV = 2001
        private const val REQ_OPEN_PACKAGE = 2002
        private const val REQ_SAVE_AS_PACKAGE = 2003
        private const val REQ_NEW_KIT_TARGET_URI = 2004
    }

    private enum class DevTab {
        FILE_KIT,
        ZONE_AUDIO,
        GEOMETRY_BASS,
        TEST_PLAY
    }

    private lateinit var session: DevKitAuthoringSession
    private val kitAudioLoader = KitAudioLoader()
    private val activeSlotMap = mutableMapOf<String, Int>()

    private var currentTab: DevTab = DevTab.ZONE_AUDIO
    private var selectedDrumId: String = "bass"
    private var selectedZoneId: String = "bass-1"
    private var showGuidesInTestPlay: Boolean = true

    // Pending state saat membuat kit baru lalu memilih lokasi .vrkendang
    private var pendingNewKitId: String? = null
    private var pendingNewKitName: String? = null

    // Komponen UI utama
    private lateinit var headerStatusText: TextView
    private lateinit var contentFrame: FrameLayout
    private val tabButtons = mutableMapOf<DevTab, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            @Suppress("SourceLockedOrientationActivity")
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val devKitDir = File(filesDir, "dev-kit")
        session = DevKitAuthoringSession(devKitDir)

        var kit = session.loadExistingWorkingCopy()
        if (kit == null) {
            kit = runCatching { session.openBuiltInKit("new-metro", assets) }.getOrNull()
        }
        if (kit != null) {
            activateKitAudio(kit)
        }

        setContentView(buildRootLayout())
        applyImmersiveStickyMode()
        switchTab(DevTab.ZONE_AUDIO)
    }

    override fun onStart() {
        super.onStart()
        runCatching { AudioEngine.resumeStream() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { AudioEngine.pauseStream() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveStickyMode()
        }
    }

    private fun activateKitAudio(kit: KitData) {
        activeSlotMap.clear()
        var slotIdx = 0
        for (drum in kit.drums) {
            for (zone in drum.zones) {
                activeSlotMap[zone.id] = slotIdx++
            }
        }
        val soundSource = { soundPath: String ->
            File(session.devKitDir, soundPath).inputStream()
        }
        kitAudioLoader.activate(kit, soundSource)
    }

    // =========================================================================
    // Kerangka Halaman & Bilah Navigasi Tab Atas
    // =========================================================================

    private fun buildRootLayout(): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Bilah Navigasi Atas (Header Mode Developer)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1C1917"))
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
        }

        val exitBtn = createSmallButton("← Keluar", "#332E2A") {
            handleExitRequest()
        }
        topBar.addView(exitBtn)

        headerStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            setPadding((6 * density).toInt(), 0, (8 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topBar.addView(headerStatusText)

        // 4 Tombol Tab Halaman
        val tabDefs = listOf(
            DevTab.FILE_KIT to "1. File Kit",
            DevTab.ZONE_AUDIO to "2. Isi WAV (10 Zona)",
            DevTab.GEOMETRY_BASS to "3. Posisi & Busur",
            DevTab.TEST_PLAY to "4. Uji Main"
        )
        tabButtons.clear()
        for ((tab, title) in tabDefs) {
            val btn = createSmallButton(title, "#2B2622") {
                switchTab(tab)
            }
            tabButtons[tab] = btn
            topBar.addView(btn)
        }

        // Tombol Simpan Cepat di Kanan Atas
        val quickSaveBtn = createSmallButton("💾 Simpan", "#2E7D32") {
            handleSaveAction()
        }
        topBar.addView(quickSaveBtn)

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).toInt())
        )

        // Area Konten Tab (Memenuhi sisa layar)
        contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }
        root.addView(
            contentFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )

        refreshHeaderStatus()
        return root
    }

    private fun refreshHeaderStatus() {
        val kit = session.workingKit
        if (kit == null) {
            headerStatusText.text = "Mode Developer"
            return
        }
        val assignedCount = kit.drums.sumOf { d -> d.zones.count { it.sound != null } }
        val totalZones = kit.drums.sumOf { it.zones.size }
        val dirtyBadge = if (session.hasUnsavedChanges) " ● [Belum Disimpan]" else ""
        headerStatusText.text = "${kit.name} ($assignedCount/$totalZones WAV)$dirtyBadge"
        headerStatusText.setTextColor(
            if (session.hasUnsavedChanges) Color.parseColor("#FFB300") else Color.WHITE
        )
    }

    private fun switchTab(tab: DevTab) {
        currentTab = tab
        val density = resources.displayMetrics.density

        for ((t, btn) in tabButtons) {
            val active = (t == tab)
            btn.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (active) "#FF8F00" else "#2B2622"))
                cornerRadius = 8f * density
                setStroke(
                    (1 * density).toInt().coerceAtLeast(1),
                    Color.parseColor(if (active) "#FFE082" else "#44FFFFFF")
                )
            }
            btn.setTextColor(if (active) Color.BLACK else Color.WHITE)
        }

        refreshHeaderStatus()
        contentFrame.removeAllViews()

        val pageView = when (tab) {
            DevTab.FILE_KIT -> buildFileKitPage()
            DevTab.ZONE_AUDIO -> buildZoneAudioPage()
            DevTab.GEOMETRY_BASS -> buildGeometryAndBassPage()
            DevTab.TEST_PLAY -> buildTestPlayPage()
        }
        contentFrame.addView(
            pageView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    // =========================================================================
    // TAB 1: Halaman Kelola File Kit
    // =========================================================================

    private fun buildFileKitPage(): View {
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return TextView(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
        }

        // Baris 2 Kolom untuk Kartu Identitas Kit (Kiri) & Kartu Aksi Berkas (Kanan)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2.0f
        }

        // Kolom Kiri: Identitas Kit Aktif
        val leftCard = createCardContainer().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginEnd = (10 * density).toInt()
            }
        }

        leftCard.addView(createSectionTitle("Identitas Kit yang Sedang Diedit"))

        val assignedCount = kit.drums.sumOf { d -> d.zones.count { it.sound != null } }
        val totalZones = kit.drums.sumOf { it.zones.size }
        val infoText = TextView(this).apply {
            text = "ID Kit (tetap): ${kit.kitId}\n" +
                "Versi Paket: v${kit.version}\n" +
                "Sampel Terisi: $assignedCount dari $totalZones zona\n" +
                "File Sumber: ${session.sourceUri?.lastPathSegment ?: "Belum disimpan ke file .vrkendang"}"
            textSize = 12.5f
            setTextColor(Color.LTGRAY)
            setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
        }
        leftCard.addView(infoText)

        val nameLabel = TextView(this).apply {
            text = "Ubah Nama Tampilan Kit:"
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        leftCard.addView(nameLabel)

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        val nameEdit = EditText(this).apply {
            setText(kit.name)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        nameRow.addView(nameEdit)

        val applyNameBtn = createSmallButton("Perbarui Nama", "#1565C0") {
            val newName = nameEdit.text.toString().trim()
            if (newName.isNotEmpty()) {
                session.updateKitName(newName)
                refreshHeaderStatus()
                Toast.makeText(this, "✓ Nama kit diperbarui menjadi '$newName'", Toast.LENGTH_SHORT).show()
            }
        }
        nameRow.addView(applyNameBtn)
        leftCard.addView(nameRow)
        row.addView(leftCard)

        // Kolom Kanan: Buka / Buat / Simpan Paket .vrkendang
        val rightCard = createCardContainer().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = (10 * density).toInt()
            }
        }

        rightCard.addView(createSectionTitle("Buat, Buka, atau Simpan Kit (.vrkendang)"))

        val descText = TextView(this).apply {
            text = "Pilih aksi di bawah untuk memulai kit baru, membuka file .vrkendang dari HP, atau menyimpan hasil penyuntingan:"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, (4 * density).toInt(), 0, (10 * density).toInt())
        }
        rightCard.addView(descText)

        val btnNew = createFullWidthButton("➕ Buat Kit Baru (Kosong dari Template)", "#2E7D32") {
            promptCreateNewKit()
        }
        rightCard.addView(btnNew)

        val btnOpenPkg = createFullWidthButton("📂 Buka File Paket .vrkendang dari HP", "#1565C0") {
            promptOpenPackage()
        }
        rightCard.addView(btnOpenPkg)

        val btnOpenBuiltIn = createFullWidthButton("📦 Muat Ulang Template Bawaan (New Metro)", "#424242") {
            promptOpenBuiltIn()
        }
        rightCard.addView(btnOpenBuiltIn)

        val btnSave = createFullWidthButton("💾 Simpan ke File Sumber (.vrkendang)", "#00695C") {
            handleSaveAction()
        }
        rightCard.addView(btnSave)

        val btnSaveAs = createFullWidthButton("💾 Simpan Sebagai File .vrkendang Baru...", "#00838F") {
            launchSaveAsPicker()
        }
        rightCard.addView(btnSaveAs)

        row.addView(rightCard)
        container.addView(row)
        scroll.addView(container)
        return scroll
    }

    // =========================================================================
    // TAB 2: Halaman Isi Sampel WAV & Gain (10 Zona)
    // =========================================================================

    private fun buildZoneAudioPage(): View {
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return TextView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 10.0f
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Pastikan selectedZoneId valid
        val allZones = kit.drums.flatMap { d -> d.zones.map { z -> d to z } }
        if (allZones.none { it.second.id == selectedZoneId }) {
            selectedZoneId = allZones.first().second.id
            selectedDrumId = allZones.first().first.id
        }

        // KOLOM KIRI (55% lebar): Peta Kendang Interaktif (Ketuk zona langsung di kendang!)
        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5.5f)
            setPadding((8 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
        }

        val mapHint = TextView(this).apply {
            text = "💡 Ketuk langsung zona pada gambar kendang di bawah (atau pilih tombol zona di kanan):"
            textSize = 11.5f
            setTextColor(Color.parseColor("#FFD54F"))
            setPadding((6 * density).toInt(), 0, 0, (4 * density).toInt())
        }
        leftColumn.addView(mapHint)

        // Kontainer kanan perlu direferensikan agar klik di boardView memperbarui panel kanan
        val rightDetailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val zoneChipsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val boardView = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.SELECT_ZONE
            showZoneGuides = true
            this.kit = kit
            this.slotByZoneId = activeSlotMap
            this.selectedDrumId = this@DevAuthoringActivity.selectedDrumId
            this.selectedZoneId = this@DevAuthoringActivity.selectedZoneId
        }

        boardView.onZoneSelectedListener = { drum, zone ->
            selectedDrumId = drum.id
            selectedZoneId = zone.id
            populateZoneChips(zoneChipsContainer, boardView, rightDetailContainer)
            populateSelectedZoneEditor(rightDetailContainer, boardView, zoneChipsContainer)
        }

        leftColumn.addView(
            boardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )
        root.addView(leftColumn)

        // KOLOM KANAN (45% lebar): Daftar Cepat 10 Zona + Panel Editor Zona Terpilih
        val rightScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.5f)
            setBackgroundColor(Color.parseColor("#181614"))
        }

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (14 * density).toInt())
        }

        rightColumn.addView(createSectionTitle("Daftar 10 Zona Suara (Klik untuk Pilih)"))
        rightColumn.addView(zoneChipsContainer)
        rightColumn.addView(rightDetailContainer)

        populateZoneChips(zoneChipsContainer, boardView, rightDetailContainer)
        populateSelectedZoneEditor(rightDetailContainer, boardView, zoneChipsContainer)

        rightScroll.addView(rightColumn)
        root.addView(rightScroll)
        return root
    }

    private fun populateZoneChips(
        chipsContainer: LinearLayout,
        boardView: KendangBoardView,
        detailContainer: LinearLayout
    ) {
        chipsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        // Tampilkan tombol-tombol zona dikelompokkan per kendang secara rapi
        for (drum in kit.drums) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
            }

            val drumLabel = TextView(this).apply {
                text = "${drum.id.uppercase()}:"
                textSize = 11f
                setTextColor(Color.LTGRAY)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams((58 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(drumLabel)

            val buttonsWrap = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            for (zone in drum.zones) {
                val isSelected = (zone.id == selectedZoneId)
                val hasWav = (zone.sound != null)
                val shortLabel = if (hasWav) "✓ ${zone.id}" else zone.id

                val bgHex = when {
                    isSelected -> "#FF8F00"
                    hasWav -> "#1B5E20"
                    else -> "#2B2622"
                }

                val chip = Button(this).apply {
                    text = shortLabel
                    textSize = 10f
                    isAllCaps = false
                    setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(bgHex))
                        cornerRadius = 6f * density
                        setStroke(
                            (1 * density).toInt().coerceAtLeast(1),
                            Color.parseColor(if (isSelected) "#FFF59D" else if (hasWav) "#66BB6A" else "#55FFFFFF")
                        )
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (28 * density).toInt()
                    ).apply {
                        marginEnd = (4 * density).toInt()
                    }
                    setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                    setOnClickListener {
                        selectedDrumId = drum.id
                        selectedZoneId = zone.id
                        boardView.selectedDrumId = drum.id
                        boardView.selectedZoneId = zone.id
                        populateZoneChips(chipsContainer, boardView, detailContainer)
                        populateSelectedZoneEditor(detailContainer, boardView, chipsContainer)
                    }
                }
                buttonsWrap.addView(chip)
            }

            row.addView(buttonsWrap)
            chipsContainer.addView(row)
        }
    }

    private fun populateSelectedZoneEditor(
        detailContainer: LinearLayout,
        boardView: KendangBoardView,
        chipsContainer: LinearLayout
    ) {
        detailContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        val pair = kit.drums.flatMap { d -> d.zones.map { z -> d to z } }
            .firstOrNull { it.second.id == selectedZoneId }
            ?: return
        val drum = pair.first
        val zone = pair.second

        val card = createCardContainer().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * density).toInt()
            }
        }

        val regionDesc = when {
            drum.model == "bass" && zone.region == "main" -> "Area Utama Tengah/Kiri (di dalam busur)"
            drum.model == "bass" && zone.region == "crescent" -> "Bilah Bulan Sabit #${(zone.band ?: 0) + 1} (urutan atas ke bawah)"
            drum.model == "split" && zone.region == "upper" -> "Bagian Atas (Upper)"
            drum.model == "split" && zone.region == "lower" -> "Bagian Bawah (Lower)"
            else -> "Seluruh Kulit Kendang"
        }

        val zoneHeader = TextView(this).apply {
            text = "Zona Terpilih: ${zone.id.uppercase()}"
            textSize = 15f
            setTextColor(Color.parseColor("#FFD54F"))
            paint.isFakeBoldText = true
        }
        card.addView(zoneHeader)

        val subHeader = TextView(this).apply {
            text = "Kendang ${drum.id.uppercase()} • $regionDesc"
            textSize = 11.5f
            setTextColor(Color.LTGRAY)
            setPadding(0, (2 * density).toInt(), 0, (8 * density).toInt())
        }
        card.addView(subHeader)

        // Status File WAV saat ini
        val hasWav = zone.sound != null
        val statusBox = TextView(this).apply {
            text = if (hasWav) {
                "✓ Sampel Terpasang: ${zone.sound}"
            } else {
                "○ Belum Ada Sampel Suara (Zona Hening)"
            }
            textSize = 12.5f
            setTextColor(if (hasWav) Color.parseColor("#81C784") else Color.parseColor("#FFB74D"))
            paint.isFakeBoldText = true
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (hasWav) "#1A2E1A" else "#2A221B"))
                cornerRadius = 6f * density
            }
        }
        card.addView(statusBox)

        // Tombol Utama: Pilih File WAV dari HP
        val pickBtn = createFullWidthButton(
            if (hasWav) "📂 Ganti File WAV untuk ${zone.id.uppercase()}..." else "📂 Pilih File WAV dari HP untuk ${zone.id.uppercase()}...",
            "#2E7D32"
        ) {
            launchWavPickerForSelectedZone()
        }
        card.addView(pickBtn)

        // Baris Tombol Tes Bunyi & Kosongkan Suara
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * density).toInt(), 0, (10 * density).toInt())
        }

        val testBtn = createSmallButton("▶ Tes Bunyi Zona Ini", "#1565C0") {
            val currentZone = session.workingKit?.drums
                ?.flatMap { it.zones }
                ?.firstOrNull { it.id == zone.id }
            if (currentZone?.sound == null) {
                Toast.makeText(this, "Zona ${zone.id} belum diisi file WAV", Toast.LENGTH_SHORT).show()
            } else {
                val slot = activeSlotMap[zone.id] ?: 0
                AudioEngine.triggerSound(slot, currentZone.gain)
            }
        }
        actionRow.addView(testBtn)

        val clearBtn = createSmallButton("🗑 Kosongkan WAV", "#C62828") {
            val res = session.clearZoneSound(zone.id)
            if (res is DevSoundInstaller.Result.Success) {
                val slot = activeSlotMap[zone.id] ?: 0
                AudioEngine.unloadSound(slot)
                boardView.kit = res.updatedKit
                refreshHeaderStatus()
                populateZoneChips(chipsContainer, boardView, detailContainer)
                populateSelectedZoneEditor(detailContainer, boardView, chipsContainer)
                Toast.makeText(this, "Sampel zona ${zone.id} dikosongkan", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow.addView(clearBtn)
        card.addView(actionRow)

        // Slider Volume / Gain (0.00x - 2.00x)
        val gainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gainTitle = TextView(this).apply {
            text = "Volume (Gain): ${String.format("%.2fx", zone.gain)}"
            textSize = 12.5f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        gainRow.addView(gainTitle)

        val resetGainBtn = createSmallButton("1.0x Normal", "#424242") {
            val updated = session.updateZoneGain(zone.id, 1.0f)
            boardView.kit = updated
            refreshHeaderStatus()
            populateSelectedZoneEditor(detailContainer, boardView, chipsContainer)
        }
        gainRow.addView(resetGainBtn)
        card.addView(gainRow)

        val gainSeek = SeekBar(this).apply {
            max = 200
            progress = (zone.gain * 100f).roundToInt().coerceIn(0, 200)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newGain = progress / 100f
                    val updated = session.updateZoneGain(zone.id, newGain)
                    boardView.kit = updated
                    gainTitle.text = "Volume (Gain): ${String.format("%.2fx", newGain)}"
                    refreshHeaderStatus()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        card.addView(gainSeek)

        detailContainer.addView(card)
    }

    // =========================================================================
    // TAB 3: Halaman Posisi, Ukuran, Prioritas & Busur Bass (Live Split-Screen)
    // =========================================================================

    private fun buildGeometryAndBassPage(): View {
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return TextView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 10.0f
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // KOLOM KIRI (55% lebar): Kanvas Edit Posisi & Busur Bass Secara Real-Time
        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5.5f)
            setPadding((8 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
        }

        val dragHint = TextView(this).apply {
            text = "💡 Geser kendang dengan 1 jari di kanvas ini. Perubahan slider di kanan langsung terlihat real-time:"
            textSize = 11.5f
            setTextColor(Color.parseColor("#FFD54F"))
            setPadding((6 * density).toInt(), 0, 0, (4 * density).toInt())
        }
        leftColumn.addView(dragHint)

        val rightControlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val boardView = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.EDIT_LAYOUT
            showZoneGuides = true
            this.kit = kit
            this.selectedDrumId = this@DevAuthoringActivity.selectedDrumId
        }

        boardView.onDrumSelectedListener = { drum ->
            selectedDrumId = drum.id
            populateGeometryControls(rightControlsContainer, boardView)
        }

        boardView.onDrumMovedListener = { drumId, newCenter ->
            val updated = session.updateDrumCenter(drumId, newCenter)
            boardView.kit = updated
            refreshHeaderStatus()
        }

        leftColumn.addView(
            boardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )
        root.addView(leftColumn)

        // KOLOM KANAN (45% lebar): Panel Kontrol Ukuran, Urutan & Busur Bass
        val rightScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.5f)
            setBackgroundColor(Color.parseColor("#181614"))
        }

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (14 * density).toInt())
        }

        rightColumn.addView(rightControlsContainer)
        populateGeometryControls(rightControlsContainer, boardView)

        rightScroll.addView(rightColumn)
        root.addView(rightScroll)
        return root
    }

    private fun populateGeometryControls(
        container: LinearLayout,
        boardView: KendangBoardView
    ) {
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        val selectedIndex = kit.drums.indexOfFirst { it.id == selectedDrumId }.coerceAtLeast(0)
        val drum = kit.drums[selectedIndex]
        selectedDrumId = drum.id
        boardView.selectedDrumId = drum.id

        // 1. Kartu Pilih Kendang & Urutan Prioritas Hit-Test
        val drumCard = createCardContainer()
        drumCard.addView(createSectionTitle("Pilih Kendang & Urutan Prioritas"))

        val drumSelectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt())
        }
        for (d in kit.drums) {
            val active = (d.id == selectedDrumId)
            val btn = createSmallButton(d.id.uppercase(), if (active) "#FF8F00" else "#2B2622") {
                selectedDrumId = d.id
                boardView.selectedDrumId = d.id
                populateGeometryControls(container, boardView)
            }
            btn.setTextColor(if (active) Color.BLACK else Color.WHITE)
            drumSelectRow.addView(btn)
        }
        drumCard.addView(drumSelectRow)

        // Urutan Prioritas (Task 11.11)
        val orderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val orderLabel = TextView(this).apply {
            text = "Urutan Prioritas: #${selectedIndex + 1} dari ${kit.drums.size}"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        orderRow.addView(orderLabel)

        val upBtn = createSmallButton("▲ Naik", "#37474F") {
            if (selectedIndex > 0) {
                val updated = session.moveDrumOrder(selectedIndex, selectedIndex - 1)
                boardView.kit = updated
                refreshHeaderStatus()
                populateGeometryControls(container, boardView)
            }
        }.apply { isEnabled = selectedIndex > 0 }
        orderRow.addView(upBtn)

        val downBtn = createSmallButton("▼ Turun", "#37474F") {
            if (selectedIndex < kit.drums.size - 1) {
                val updated = session.moveDrumOrder(selectedIndex, selectedIndex + 1)
                boardView.kit = updated
                refreshHeaderStatus()
                populateGeometryControls(container, boardView)
            }
        }.apply { isEnabled = selectedIndex < kit.drums.size - 1 }
        orderRow.addView(downBtn)

        drumCard.addView(orderRow)
        container.addView(drumCard)

        // 2. Kartu Ukuran Radius Kendang Terpilih (rx & ry)
        val sizeCard = createCardContainer().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        }
        sizeCard.addView(createSectionTitle("Ukuran Kendang: ${drum.id.uppercase()}"))

        val maxRx = minOf(
            0.55f,
            drum.center.x * kit.canvasAspect,
            (1.0f - drum.center.x) * kit.canvasAspect
        ).coerceAtLeast(0.08f)
        val maxRy = minOf(
            0.48f,
            drum.center.y,
            1.0f - drum.center.y
        ).coerceAtLeast(0.08f)

        val rxLabel = TextView(this).apply {
            text = "Lebar Horizontal (rx): ${String.format("%.2f", drum.radius.rx)}"
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        sizeCard.addView(rxLabel)

        val rxSeek = SeekBar(this).apply {
            val maxProg = ((maxRx - 0.08f) * 100f).roundToInt().coerceAtLeast(1)
            max = maxProg
            progress = ((drum.radius.rx - 0.08f) * 100f).roundToInt().coerceIn(0, maxProg)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val curDrum = session.workingKit?.drums?.firstOrNull { it.id == drum.id } ?: return
                    val newRx = (0.08f + progress / 100f).coerceIn(0.08f, maxRx)
                    val newRy = if (curDrum.model == "bass") {
                        newRx.coerceIn(0.08f, maxRy)
                    } else {
                        curDrum.radius.ry
                    }
                    val updated = session.updateDrumRadius(drum.id, Radius(newRx, newRy))
                    boardView.kit = updated
                    rxLabel.text = "Lebar Horizontal (rx): ${String.format("%.2f", newRx)}"
                    refreshHeaderStatus()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        sizeCard.addView(rxSeek)

        if (drum.model != "bass") {
            val ryLabel = TextView(this).apply {
                text = "Tinggi Vertikal (ry): ${String.format("%.2f", drum.radius.ry)}"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(0, (6 * density).toInt(), 0, 0)
            }
            sizeCard.addView(ryLabel)

            val rySeek = SeekBar(this).apply {
                val maxProg = ((maxRy - 0.08f) * 100f).roundToInt().coerceAtLeast(1)
                max = maxProg
                progress = ((drum.radius.ry - 0.08f) * 100f).roundToInt().coerceIn(0, maxProg)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val curDrum = session.workingKit?.drums?.firstOrNull { it.id == drum.id } ?: return
                        val newRy = (0.08f + progress / 100f).coerceIn(0.08f, maxRy)
                        val updated = session.updateDrumRadius(drum.id, Radius(curDrum.radius.rx, newRy))
                        boardView.kit = updated
                        ryLabel.text = "Tinggi Vertikal (ry): ${String.format("%.2f", newRy)}"
                        refreshHeaderStatus()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            sizeCard.addView(rySeek)
        }
        container.addView(sizeCard)

        // 3. Kartu Busur Pembatas Bass (Selalu dapat diakses dan bergerak Live di kanvas kiri!)
        val bassDrum = kit.drums.firstOrNull { it.model == "bass" }
        val divider = bassDrum?.divider
        if (bassDrum != null && divider != null) {
            val divCard = createCardContainer().apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * density).toInt() }
            }

            val divHeaderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val divTitle = createSectionTitle("Kurva Busur Pembatas Bass (Live)").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            divHeaderRow.addView(divTitle)

            val resetDivBtn = createSmallButton("↺ Reset Busur", "#424242") {
                val updated = session.updateBassDivider(-0.30f, 0.0f, 0.80f)
                boardView.kit = updated
                refreshHeaderStatus()
                populateGeometryControls(container, boardView)
            }
            divHeaderRow.addView(resetDivBtn)
            divCard.addView(divHeaderRow)

            // Slider dx (-3.0 .. 3.0)
            val dxLabel = TextView(this).apply {
                text = "Geser Kiri/Kanan (dx): ${String.format("%.2f", divider.dx)}"
                textSize = 11.5f
                setTextColor(Color.WHITE)
            }
            divCard.addView(dxLabel)

            val dxBar = SeekBar(this).apply {
                max = 600
                progress = ((divider.dx + 3.0f) * 100f).roundToInt().coerceIn(0, 600)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return
                        val newDx = (progress / 100f) - 3.0f
                        runCatching {
                            val updated = session.updateBassDivider(newDx, curDiv.dy, curDiv.r)
                            boardView.kit = updated
                            dxLabel.text = "Geser Kiri/Kanan (dx): ${String.format("%.2f", newDx)}"
                            refreshHeaderStatus()
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            divCard.addView(dxBar)

            // Slider dy (-3.0 .. 3.0)
            val dyLabel = TextView(this).apply {
                text = "Geser Atas/Bawah (dy): ${String.format("%.2f", divider.dy)}"
                textSize = 11.5f
                setTextColor(Color.WHITE)
            }
            divCard.addView(dyLabel)

            val dyBar = SeekBar(this).apply {
                max = 600
                progress = ((divider.dy + 3.0f) * 100f).roundToInt().coerceIn(0, 600)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return
                        val newDy = (progress / 100f) - 3.0f
                        runCatching {
                            val updated = session.updateBassDivider(curDiv.dx, newDy, curDiv.r)
                            boardView.kit = updated
                            dyLabel.text = "Geser Atas/Bawah (dy): ${String.format("%.2f", newDy)}"
                            refreshHeaderStatus()
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            divCard.addView(dyBar)

            // Slider r (0.10 .. 3.00)
            val rLabel = TextView(this).apply {
                text = "Jari-jari Lengkungan (r): ${String.format("%.2f", divider.r)}"
                textSize = 11.5f
                setTextColor(Color.WHITE)
            }
            divCard.addView(rLabel)

            val rBar = SeekBar(this).apply {
                max = 300
                progress = (divider.r * 100f).roundToInt().coerceIn(10, 300)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return
                        val newR = (progress / 100f).coerceIn(0.10f, 3.0f)
                        runCatching {
                            val updated = session.updateBassDivider(curDiv.dx, curDiv.dy, newR)
                            boardView.kit = updated
                            rLabel.text = "Jari-jari Lengkungan (r): ${String.format("%.2f", newR)}"
                            refreshHeaderStatus()
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            divCard.addView(rBar)
            container.addView(divCard)
        }
    }

    // =========================================================================
    // TAB 4: Halaman Uji Main Penuh
    // =========================================================================

    private fun buildTestPlayPage(): View {
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return TextView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val boardView = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.PLAY
            showZoneGuides = showGuidesInTestPlay
            this.kit = kit
            this.slotByZoneId = activeSlotMap
        }

        val subBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#181614"))
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
        }

        val lastHitText = TextView(this).apply {
            text = "▶ Mode Uji Main Aktif — Ketuk kendang dengan banyak jari untuk menguji suara & rolling"
            textSize = 12f
            setTextColor(Color.parseColor("#81C784"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        subBar.addView(lastHitText)

        boardView.onZoneHitListener = { drum, zone ->
            val soundInfo = zone.sound ?: "Tanpa Suara (sound: null)"
            lastHitText.text = "Ketukan Terakhir: [${drum.id.uppercase()}] Zona ${zone.id} • Gain ${String.format("%.2fx", zone.gain)} • $soundInfo"
        }

        val toggleGuidesBtn = createSmallButton(
            if (showGuidesInTestPlay) "👁 Sembunyikan Garis Zona" else "👁 Tampilkan Garis Zona",
            "#37474F"
        ) {
            showGuidesInTestPlay = !showGuidesInTestPlay
            boardView.showZoneGuides = showGuidesInTestPlay
            switchTab(DevTab.TEST_PLAY)
        }
        subBar.addView(toggleGuidesBtn)

        root.addView(
            subBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (38 * density).toInt())
        )
        root.addView(
            boardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )
        return root
    }

    // =========================================================================
    // File Picker & Operasi Berkas Kit (.vrkendang / WAV)
    // =========================================================================

    private fun launchWavPickerForSelectedZone() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, REQ_PICK_WAV)
    }

    private fun promptCreateNewKit() {
        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Salinan kerja saat ini memiliki perubahan yang belum disimpan. Lanjutkan membuat kit baru?")
                .setPositiveButton("Lanjutkan") { _, _ -> showNewKitDialog() }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            showNewKitDialog()
        }
    }

    private fun showNewKitDialog() {
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }

        val idEdit = EditText(this).apply {
            hint = "ID Kit (huruf kecil/angka/strip, misal: koplo-asli)"
        }
        val nameEdit = EditText(this).apply {
            hint = "Nama Tampilan Kit (misal: Kendang Koplo Asli)"
        }
        layout.addView(idEdit)
        layout.addView(nameEdit)

        AlertDialog.Builder(this)
            .setTitle("Buat Kit Baru dari Template")
            .setView(layout)
            .setPositiveButton("Buat & Pilih Lokasi File") { _, _ ->
                val id = idEdit.text.toString().trim()
                val name = nameEdit.text.toString().trim()
                try {
                    val newKit = session.createNewKit(id, name, null)
                    activateKitAudio(newKit)
                    pendingNewKitId = newKit.kitId
                    pendingNewKitName = newKit.name
                    switchTab(currentTab)

                    // Sesuai Spesifikasi Kit Authoring: minta lokasi file .vrkendang sebagai file sumber
                    val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, "${newKit.kitId}.vrkendang")
                    }
                    startActivityForResult(createIntent, REQ_NEW_KIT_TARGET_URI)
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("ID atau Nama Kit Tidak Valid")
                        .setMessage(e.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun promptOpenPackage() {
        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Membuka paket lain akan menggantikan salinan kerja saat ini. Lanjutkan?")
                .setPositiveButton("Buka Paket") { _, _ -> launchOpenPackagePicker() }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            launchOpenPackagePicker()
        }
    }

    private fun launchOpenPackagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_OPEN_PACKAGE)
    }

    private fun promptOpenBuiltIn() {
        val action = {
            val kit = session.openBuiltInKit("new-metro", assets)
            activateKitAudio(kit)
            switchTab(DevTab.ZONE_AUDIO)
            Toast.makeText(this, "✓ Template bawaan 'New Metro' dimuat ke salinan kerja", Toast.LENGTH_SHORT).show()
        }

        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Muat ulang template New Metro dan buang perubahan yang belum disimpan?")
                .setPositiveButton("Muat Ulang") { _, _ -> action() }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            action()
        }
    }

    private fun handleSaveAction() {
        val targetUri = session.sourceUri
        if (targetUri != null) {
            try {
                val updated = session.saveToPackage(targetUri, contentResolver, cacheDir)
                refreshHeaderStatus()
                switchTab(currentTab)
                Toast.makeText(
                    this,
                    "✓ Kit berhasil disimpan ke berkas sumber (v${updated.version})",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                AlertDialog.Builder(this)
                    .setTitle("Gagal Menulis ke Berkas Sumber")
                    .setMessage("Tidak dapat menimpa berkas sumber (${e.message}). Pilih lokasi baru lewat Simpan Sebagai?")
                    .setPositiveButton("Simpan Sebagai") { _, _ -> launchSaveAsPicker() }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        } else {
            launchSaveAsPicker()
        }
    }

    private fun launchSaveAsPicker() {
        val kit = session.workingKit ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "${kit.kitId}.vrkendang")
        }
        startActivityForResult(intent, REQ_SAVE_AS_PACKAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uri: Uri = data.data ?: return

        when (requestCode) {
            REQ_PICK_WAV -> {
                val inputStream = runCatching { contentResolver.openInputStream(uri) }.getOrNull() ?: return
                val slot = activeSlotMap[selectedZoneId] ?: 0
                val res = session.installZoneWav(selectedZoneId, inputStream, slot)
                when (res) {
                    is DevSoundInstaller.Result.Success -> {
                        refreshHeaderStatus()
                        switchTab(currentTab)
                        // Langsung bunyikan suara yang baru dipasang sebagai konfirmasi instan
                        val gain = res.updatedKit.drums.flatMap { it.zones }
                            .firstOrNull { it.id == selectedZoneId }?.gain ?: 1.0f
                        AudioEngine.triggerSound(slot, gain)
                        Toast.makeText(
                            this,
                            "✓ WAV terpasang ke ${selectedZoneId.uppercase()} (${res.sampleRate} Hz)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is DevSoundInstaller.Result.Failure -> {
                        AlertDialog.Builder(this)
                            .setTitle("File WAV Ditolak")
                            .setMessage(res.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }

            REQ_OPEN_PACKAGE -> {
                try {
                    takePersistablePermissionIfPossible(uri, data.flags)
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Tidak dapat membaca berkas yang dipilih")
                    val openedKit = input.use { session.openKitPackage(it, uri, cacheDir) }
                    activateKitAudio(openedKit)
                    selectedDrumId = openedKit.drums.firstOrNull()?.id ?: "bass"
                    selectedZoneId = openedKit.drums.firstOrNull()?.zones?.firstOrNull()?.id ?: "bass-1"
                    switchTab(DevTab.ZONE_AUDIO)
                    Toast.makeText(
                        this,
                        "✓ Kit '${openedKit.name}' (v${openedKit.version}) berhasil dibuka!",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Membuka Paket .vrkendang")
                        .setMessage("Paket tidak valid atau rusak: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            REQ_SAVE_AS_PACKAGE, REQ_NEW_KIT_TARGET_URI -> {
                try {
                    takePersistablePermissionIfPossible(uri, data.flags)
                    val updated = session.saveToPackage(uri, contentResolver, cacheDir)
                    refreshHeaderStatus()
                    switchTab(if (requestCode == REQ_NEW_KIT_TARGET_URI) DevTab.ZONE_AUDIO else currentTab)
                    Toast.makeText(
                        this,
                        "✓ Kit '${updated.name}' disimpan ke berkas .vrkendang (v${updated.version})",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Menyimpan Paket")
                        .setMessage("Terjadi kesalahan saat menyimpan: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun takePersistablePermissionIfPossible(uri: Uri, flags: Int) {
        runCatching {
            val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (takeFlags != 0) {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        }
    }

    private fun handleExitRequest() {
        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan ke .vrkendang")
                .setMessage("Salinan kerja kit memiliki perubahan yang belum disimpan ke paket .vrkendang. Simpan terlebih dahulu?")
                .setPositiveButton("Simpan") { _, _ ->
                    handleSaveAction()
                }
                .setNeutralButton("Keluar Tanpa Simpan Paket") { _, _ ->
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleExitRequest()
    }

    // =========================================================================
    // Helper Pembangun Komponen UI
    // =========================================================================

    private fun createCardContainer(): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#211E1B"))
                cornerRadius = 10f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#38322C"))
            }
        }
    }

    private fun createSectionTitle(title: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = title
            textSize = 13.5f
            setTextColor(Color.parseColor("#FFB300"))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (6 * density).toInt())
        }
    }

    private fun createSmallButton(label: String, bgHex: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#44FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (34 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun createFullWidthButton(label: String, bgHex: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#55FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * density).toInt()
            ).apply {
                topMargin = (6 * density).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun applyImmersiveStickyMode() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        }
    }
}
