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
import android.text.Editable
import android.text.TextWatcher
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
import com.vrkendang.KitSelectActivity
import com.vrkendang.audio.AudioEngine
import com.vrkendang.audio.KitAudioLoader
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.ZoneData
import com.vrkendang.repository.KitRepository
import com.vrkendang.view.KendangBoardView
import java.io.File
import kotlin.math.roundToInt

/**
 * Halaman Mode Developer Layar Penuh — Konfigurasi per Kendang (Tahap 11).
 *
 * Sesuai Desain Penpot ("4. Mode Developer (Konfigurasi per Kendang)") & Spesifikasi Kit Authoring:
 * - Header rapi dengan pemisahan tegas:
 *   - Kiri: "← Tutup" + label "Kit: <Nama>"
 *   - Tengah: "Impor" dan "Ekspor" (khusus paket ZIP .vrkendang di penyimpanan HP)
 *   - Kanan: "+ Kit Baru" (simpan menjadi kit baru di dalam aplikasi),
 *            "✓ Simpan" (simpan menimpa kit saat ini di dalam aplikasi tanpa file picker HP),
 *            dan "▶ Uji Main" (beralih ke mode uji ketuk penuh).
 * - Pola Pemilihan per Kendang (Panel Kiri -> Panel Kanan):
 *   - Panel Kiri menampilkan daftar 4 kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`) + pratinjau papan.
 *   - Panel Kanan menampilkan khusus zona milik kendang terpilih, sampel WAV & volume zona,
 *     serta Parameter Busur Bass (`dx`, `dy`, `r`) khusus saat `Bass` dipilih.
 *   - Tanpa tombol urutan prioritas Naik/Turun.
 */
class DevAuthoringActivity : Activity() {

    private companion object {
        private const val REQ_PICK_WAV = 2001
        private const val REQ_IMPORT_PACKAGE = 2002
        private const val REQ_EXPORT_PACKAGE = 2003
    }

    private lateinit var session: DevKitAuthoringSession
    private lateinit var internalKitsDir: File
    private val kitAudioLoader = KitAudioLoader()
    private val activeSlotMap = mutableMapOf<String, Int>()

    private var isTestPlayMode: Boolean = false
    private var selectedDrumId: String = "bass"
    private var selectedZoneId: String = "bass-1"
    private var showGuidesInTestPlay: Boolean = true

    private lateinit var headerKitLabel: TextView
    private lateinit var testPlayToggleBtn: Button
    private lateinit var contentFrame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            @Suppress("SourceLockedOrientationActivity")
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val devKitDir = File(filesDir, "dev-kit")
        internalKitsDir = File(filesDir, "kits").apply { mkdirs() }
        session = DevKitAuthoringSession(devKitDir)

        val prefs = getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
        val activeKitId = prefs.getString(KitSelectActivity.KEY_ACTIVE_KIT_ID, KitRepository.DEFAULT_KIT_ID)
            ?: KitRepository.DEFAULT_KIT_ID

        val kit = runCatching {
            session.openActiveKitAsWorkingCopy(activeKitId, internalKitsDir, assets)
        }.getOrElse {
            session.loadExistingWorkingCopy()
                ?: runCatching { session.openBuiltInKit(KitRepository.DEFAULT_KIT_ID, assets) }.getOrNull()
        }

        if (kit != null) {
            selectedDrumId = kit.drums.firstOrNull()?.id ?: "bass"
            selectedZoneId = kit.drums.firstOrNull()?.zones?.firstOrNull()?.id ?: "bass-1"
            activateKitAudio(kit)
        }

        setContentView(buildRootLayout())
        applyImmersiveStickyMode()
        renderCurrentModeContent()
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

    private fun formatDrumDisplayName(drumId: String): String {
        return when (drumId) {
            "bass" -> "Bass"
            "kecil1" -> "Kecil 1"
            "sedang" -> "Sedang"
            "kecil2" -> "Kecil 2"
            else -> drumId
        }
    }

    // =========================================================================
    // Header Mode Developer (Sesuai Desain Penpot Frame 4)
    // =========================================================================

    private fun buildRootLayout(): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141210"))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1715"))
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
        }

        // Kiri: Tombol "← Tutup" + Label "Kit: <Nama>"
        val closeBtn = createHeaderButton("← Tutup", "#2A2622", "#443D36") {
            handleExitRequest()
        }
        topBar.addView(closeBtn)

        headerKitLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#FFD54F"))
            paint.isFakeBoldText = true
            setPadding((6 * density).toInt(), 0, (8 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topBar.addView(headerKitLabel)

        // Tengah: Tombol "Impor" & "Ekspor" (khusus paket ZIP .vrkendang di penyimpanan HP)
        val importBtn = createHeaderButton("Impor", "#212529", "#495057") {
            promptImportPackage()
        }
        topBar.addView(importBtn)

        val exportBtn = createHeaderButton("Ekspor", "#212529", "#495057") {
            launchExportPackagePicker()
        }
        topBar.addView(exportBtn)

        // Kanan: "+ Kit Baru", "✓ Simpan", dan "▶ Uji Main"
        val newKitBtn = createHeaderButton("+ Kit Baru", "#1565C0", "#64B5F6") {
            showSaveAsNewKitDialog()
        }
        topBar.addView(newKitBtn)

        val saveBtn = createHeaderButton("✓ Simpan", "#2E7D32", "#66BB6A") {
            handleSaveCurrentKitInternal()
        }
        topBar.addView(saveBtn)

        testPlayToggleBtn = createHeaderButton("▶ Uji Main", "#4A148C", "#BA68C8") {
            isTestPlayMode = !isTestPlayMode
            renderCurrentModeContent()
        }
        topBar.addView(testPlayToggleBtn)

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46 * density).toInt())
        )

        contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#141210"))
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
            headerKitLabel.text = "Kit: -"
            return
        }
        val dirtyDot = if (session.hasUnsavedChanges) " •" else ""
        headerKitLabel.text = "Kit: ${kit.name}$dirtyDot"
    }

    private fun renderCurrentModeContent() {
        val density = resources.displayMetrics.density
        refreshHeaderStatus()

        if (isTestPlayMode) {
            testPlayToggleBtn.text = "⚙ Konfigurasi"
            testPlayToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF8F00"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#FFE082"))
            }
            testPlayToggleBtn.setTextColor(Color.BLACK)
        } else {
            testPlayToggleBtn.text = "▶ Uji Main"
            testPlayToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#4A148C"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#BA68C8"))
            }
            testPlayToggleBtn.setTextColor(Color.WHITE)
        }

        contentFrame.removeAllViews()
        val page = if (isTestPlayMode) buildTestPlayPage() else buildPerDrumConfigPage()
        contentFrame.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    // =========================================================================
    // Halaman Utama Mode Developer: Pemilihan per Kendang (Panel Kiri -> Kanan)
    // =========================================================================

    private fun buildPerDrumConfigPage(): View {
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return TextView(this)

        val currentDrum = kit.drums.firstOrNull { it.id == selectedDrumId } ?: kit.drums.first()
        selectedDrumId = currentDrum.id
        if (currentDrum.zones.none { it.id == selectedZoneId }) {
            selectedZoneId = currentDrum.zones.first().id
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 10.0f
            setBackgroundColor(Color.parseColor("#141210"))
        }

        // PANEL KIRI (36% lebar): PILIH KENDANG + Pratinjau Papan Mini
        val leftPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3.6f)
            setBackgroundColor(Color.parseColor("#181512"))
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        }

        val leftTitle = TextView(this).apply {
            text = "PILIH KENDANG"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            paint.isFakeBoldText = true
        }
        leftPanel.addView(leftTitle)

        val leftSub = TextView(this).apply {
            text = "Ketuk kendang untuk mengatur zona & suara"
            textSize = 10f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, (1 * density).toInt(), 0, (6 * density).toInt())
        }
        leftPanel.addView(leftSub)

        val drumCardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        leftPanel.addView(drumCardsContainer)

        val rightPanelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val previewBoard = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.SELECT_ZONE
            showZoneGuides = true
            this.kit = kit
            this.slotByZoneId = activeSlotMap
            this.selectedDrumId = this@DevAuthoringActivity.selectedDrumId
            this.selectedZoneId = this@DevAuthoringActivity.selectedZoneId
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141210"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#2A241E"))
            }
        }

        previewBoard.onZoneSelectedListener = { drum, zone ->
            selectedDrumId = drum.id
            selectedZoneId = zone.id
            populateLeftDrumCards(drumCardsContainer, previewBoard, rightPanelContainer)
            populateRightDrumConfig(rightPanelContainer, previewBoard, drumCardsContainer)
        }

        leftPanel.addView(
            previewBoard,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                topMargin = (6 * density).toInt()
            }
        )
        root.addView(leftPanel)

        // PANEL KANAN (64% lebar): KONFIGURASI KENDANG TERPILIH
        val rightScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 6.4f)
            setBackgroundColor(Color.parseColor("#141210"))
        }

        val rightPaddingWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
        }
        rightPaddingWrap.addView(rightPanelContainer)
        rightScroll.addView(rightPaddingWrap)
        root.addView(rightScroll)

        populateLeftDrumCards(drumCardsContainer, previewBoard, rightPanelContainer)
        populateRightDrumConfig(rightPanelContainer, previewBoard, drumCardsContainer)

        return root
    }

    private fun populateLeftDrumCards(
        drumCardsContainer: LinearLayout,
        previewBoard: KendangBoardView,
        rightPanelContainer: LinearLayout
    ) {
        drumCardsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        for (drum in kit.drums) {
            val isSelected = (drum.id == selectedDrumId)
            val displayName = formatDrumDisplayName(drum.id)
            val zoneDesc = when (drum.id) {
                "bass" -> "5 Zona (bass-1 s/d bass-5)"
                "kecil1" -> "1 Zona (kecil1-1)"
                "sedang" -> "2 Zona (sedang-1, sedang-2)"
                "kecil2" -> "2 Zona (kecil2-1, kecil2-2)"
                else -> "${drum.zones.size} Zona"
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelected) "#2C2218" else "#1F1C19"))
                    cornerRadius = 8f * density
                    setStroke(
                        ((if (isSelected) 2f else 1f) * density).toInt().coerceAtLeast(1),
                        Color.parseColor(if (isSelected) "#FF8F00" else "#332D28")
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (5 * density).toInt()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedDrumId = drum.id
                    selectedZoneId = drum.zones.first().id
                    previewBoard.selectedDrumId = selectedDrumId
                    previewBoard.selectedZoneId = selectedZoneId
                    populateLeftDrumCards(drumCardsContainer, previewBoard, rightPanelContainer)
                    populateRightDrumConfig(rightPanelContainer, previewBoard, drumCardsContainer)
                }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val nameTv = TextView(this).apply {
                text = displayName
                textSize = 12.5f
                setTextColor(Color.parseColor(if (isSelected) "#FFD54F" else "#E0E0E0"))
                paint.isFakeBoldText = true
            }
            textCol.addView(nameTv)

            val subTv = TextView(this).apply {
                text = zoneDesc
                textSize = 10f
                setTextColor(Color.parseColor(if (isSelected) "#BDBDBD" else "#757575"))
            }
            textCol.addView(subTv)
            card.addView(textCol)

            if (isSelected) {
                val activeBadge = TextView(this).apply {
                    text = "Aktif"
                    textSize = 10.5f
                    setTextColor(Color.parseColor("#FF8F00"))
                    paint.isFakeBoldText = true
                }
                card.addView(activeBadge)
            }

            drumCardsContainer.addView(card)
        }
    }

    private fun populateRightDrumConfig(
        rightContainer: LinearLayout,
        previewBoard: KendangBoardView,
        drumCardsContainer: LinearLayout
    ) {
        rightContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        val drum = kit.drums.firstOrNull { it.id == selectedDrumId } ?: return
        val zone = drum.zones.firstOrNull { it.id == selectedZoneId } ?: drum.zones.first()
        selectedZoneId = zone.id

        val displayName = formatDrumDisplayName(drum.id).uppercase()

        // Judul Konfigurasi Kendang Terpilih
        val headerTitle = TextView(this).apply {
            text = "KONFIGURASI: $displayName"
            textSize = 14f
            setTextColor(Color.parseColor("#FFD54F"))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        rightContainer.addView(headerTitle)

        // 1. Bagian Pilih Zona Milik Kendang Ini
        val zoneSectionLabel = TextView(this).apply {
            text = "ZONA SUARA $displayName (Ketuk zona untuk pilih sampel WAV & volume)"
            textSize = 10.5f
            setTextColor(Color.parseColor("#9E9E9E"))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        rightContainer.addView(zoneSectionLabel)

        val zoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        for (z in drum.zones) {
            val isZoneSelected = (z.id == selectedZoneId)
            val hasWav = (z.sound != null)

            val chipBg = when {
                isZoneSelected -> "#FF8F00"
                hasWav -> "#1B3B2B"
                else -> "#24201C"
            }
            val chipStroke = when {
                isZoneSelected -> "#FFE082"
                hasWav -> "#4CAF50"
                else -> "#3E3731"
            }
            val chipTextColor = when {
                isZoneSelected -> Color.BLACK
                hasWav -> Color.parseColor("#81C784")
                else -> Color.parseColor("#BDBDBD")
            }

            val chip = Button(this).apply {
                text = if (hasWav) "✓ ${z.id}" else z.id
                textSize = 11f
                isAllCaps = false
                setTextColor(chipTextColor)
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(chipBg))
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(chipStroke))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (32 * density).toInt()
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
                setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
                setOnClickListener {
                    selectedZoneId = z.id
                    previewBoard.selectedZoneId = z.id
                    populateRightDrumConfig(rightContainer, previewBoard, drumCardsContainer)
                }
            }
            zoneRow.addView(chip)
        }
        rightContainer.addView(zoneRow)

        // 2. Kartu Sampel Audio & Volume Zona Terpilih
        val audioCard = createCardContainer().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }

        val audioTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val audioInfoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val audioTitle = TextView(this).apply {
            text = "Sampel Audio — Zona ${zone.id}"
            textSize = 13f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
        }
        audioInfoCol.addView(audioTitle)

        val hasSound = zone.sound != null
        val fileStatusTv = TextView(this).apply {
            text = if (hasSound) "File: ${zone.sound}" else "Belum ada suara"
            textSize = 11f
            setTextColor(Color.parseColor(if (hasSound) "#81C784" else "#FFB74D"))
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        audioInfoCol.addView(fileStatusTv)
        audioTopRow.addView(audioInfoCol)

        // Tombol Pilih WAV, ▶ Tes, Hapus
        val pickWavBtn = createActionButton("Pilih WAV", "#1565C0", "#64B5F6") {
            launchWavPickerForSelectedZone()
        }
        audioTopRow.addView(pickWavBtn)

        val testWavBtn = createActionButton("▶ Tes", "#2E7D32", "#66BB6A") {
            val curZone = session.workingKit?.drums
                ?.flatMap { it.zones }
                ?.firstOrNull { it.id == zone.id }
            if (curZone?.sound == null) {
                Toast.makeText(this, "Zona ${zone.id} belum memiliki suara", Toast.LENGTH_SHORT).show()
            } else {
                val slot = activeSlotMap[zone.id] ?: 0
                AudioEngine.triggerSound(slot, curZone.gain)
            }
        }
        audioTopRow.addView(testWavBtn)

        val clearWavBtn = createActionButton("Hapus", "#4A1C1C", "#EF5350") {
            val res = session.clearZoneSound(zone.id)
            if (res is DevSoundInstaller.Result.Success) {
                val slot = activeSlotMap[zone.id] ?: 0
                AudioEngine.unloadSound(slot)
                previewBoard.kit = res.updatedKit
                refreshHeaderStatus()
                populateRightDrumConfig(rightContainer, previewBoard, drumCardsContainer)
                Toast.makeText(this, "Suara zona ${zone.id} dihapus", Toast.LENGTH_SHORT).show()
            }
        }
        audioTopRow.addView(clearWavBtn)
        audioCard.addView(audioTopRow)

        // Baris Volume Zona (Gain)
        val gainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        val gainLabel = TextView(this).apply {
            text = "Volume Zona:"
            textSize = 11.5f
            setTextColor(Color.parseColor("#BDBDBD"))
        }
        gainRow.addView(gainLabel)

        val gainValueTv = TextView(this).apply {
            text = String.format("%.2fx", zone.gain)
            textSize = 11.5f
            setTextColor(Color.parseColor("#FFB300"))
            paint.isFakeBoldText = true
            setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0)
        }

        val gainSeek = SeekBar(this).apply {
            max = 200
            progress = (zone.gain * 100f).roundToInt().coerceIn(0, 200)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = (6 * density).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newGain = progress / 100f
                    val updated = session.updateZoneGain(zone.id, newGain)
                    previewBoard.kit = updated
                    gainValueTv.text = String.format("%.2fx", newGain)
                    refreshHeaderStatus()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        gainRow.addView(gainSeek)
        gainRow.addView(gainValueTv)
        audioCard.addView(gainRow)

        rightContainer.addView(audioCard)

        // 3. Kartu PARAMETER BUSUR BASS (Khusus saat kendang Bass dipilih)
        if (drum.model == "bass" && drum.divider != null) {
            val divider = drum.divider!!
            val bassCard = createCardContainer()

            val bassHeaderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (4 * density).toInt())
            }

            val bassParamTitle = TextView(this).apply {
                text = "PARAMETER BUSUR BASS"
                textSize = 11f
                setTextColor(Color.parseColor("#9E9E9E"))
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            bassHeaderRow.addView(bassParamTitle)

            val resetArcBtn = createActionButton("↺ Reset Busur", "#2A2622", "#443D36") {
                val updated = session.updateBassDivider(-0.30f, 0.0f, 0.80f)
                previewBoard.kit = updated
                refreshHeaderStatus()
                populateRightDrumConfig(rightContainer, previewBoard, drumCardsContainer)
            }
            bassHeaderRow.addView(resetArcBtn)
            bassCard.addView(bassHeaderRow)

            // Slider dx (-3.0 .. 3.0)
            bassCard.addView(
                buildDividerSliderRow(
                    labelPrefix = "Geser X (dx)",
                    initialValue = divider.dx,
                    minVal = -3.0f,
                    maxVal = 3.0f
                ) { newDx ->
                    val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildDividerSliderRow
                    runCatching {
                        val updated = session.updateBassDivider(newDx, curDiv.dy, curDiv.r)
                        previewBoard.kit = updated
                        refreshHeaderStatus()
                    }
                }
            )

            // Slider dy (-3.0 .. 3.0)
            bassCard.addView(
                buildDividerSliderRow(
                    labelPrefix = "Geser Y (dy)",
                    initialValue = divider.dy,
                    minVal = -3.0f,
                    maxVal = 3.0f
                ) { newDy ->
                    val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildDividerSliderRow
                    runCatching {
                        val updated = session.updateBassDivider(curDiv.dx, newDy, curDiv.r)
                        previewBoard.kit = updated
                        refreshHeaderStatus()
                    }
                }
            )

            // Slider r (0.10 .. 3.00)
            bassCard.addView(
                buildDividerSliderRow(
                    labelPrefix = "Jari-jari (r)",
                    initialValue = divider.r,
                    minVal = 0.10f,
                    maxVal = 3.0f
                ) { newR ->
                    val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildDividerSliderRow
                    runCatching {
                        val updated = session.updateBassDivider(curDiv.dx, curDiv.dy, newR)
                        previewBoard.kit = updated
                        refreshHeaderStatus()
                    }
                }
            )

            rightContainer.addView(bassCard)
        }
    }

    private fun buildDividerSliderRow(
        labelPrefix: String,
        initialValue: Float,
        minVal: Float,
        maxVal: Float,
        onValueChanged: (Float) -> Unit
    ): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
        }

        val labelTv = TextView(this).apply {
            text = "$labelPrefix:"
            textSize = 11f
            setTextColor(Color.parseColor("#BDBDBD"))
            layoutParams = LinearLayout.LayoutParams((86 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(labelTv)

        val valueTv = TextView(this).apply {
            text = String.format("%.2f", initialValue)
            textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            paint.isFakeBoldText = true
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val maxSteps = ((maxVal - minVal) * 100f).roundToInt().coerceAtLeast(1)
        val initialProg = ((initialValue - minVal) * 100f).roundToInt().coerceIn(0, maxSteps)

        val seek = SeekBar(this).apply {
            max = maxSteps
            progress = initialProg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newVal = (minVal + progress / 100f).coerceIn(minVal, maxVal)
                    valueTv.text = String.format("%.2f", newVal)
                    onValueChanged(newVal)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(seek)
        row.addView(valueTv)
        return row
    }

    // =========================================================================
    // Halaman Mode Uji Main Penuh
    // =========================================================================

    private fun buildTestPlayPage(): View {
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return TextView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141210"))
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
            setBackgroundColor(Color.parseColor("#181512"))
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
        }

        val lastHitText = TextView(this).apply {
            text = "▶ Uji Main Aktif — Ketuk kendang untuk menguji suara & rolling"
            textSize = 11.5f
            setTextColor(Color.parseColor("#81C784"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        subBar.addView(lastHitText)

        boardView.onZoneHitListener = { drum, zone ->
            val soundInfo = zone.sound ?: "Belum ada suara"
            lastHitText.text = "Ketukan: [${formatDrumDisplayName(drum.id)}] Zona ${zone.id} • Volume ${String.format("%.2fx", zone.gain)} • $soundInfo"
        }

        val toggleGuidesBtn = createActionButton(
            if (showGuidesInTestPlay) "Sembunyikan Garis Zona" else "Tampilkan Garis Zona",
            "#2A2622",
            "#443D36"
        ) {
            showGuidesInTestPlay = !showGuidesInTestPlay
            boardView.showZoneGuides = showGuidesInTestPlay
            renderCurrentModeContent()
        }
        subBar.addView(toggleGuidesBtn)

        root.addView(
            subBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (36 * density).toInt())
        )
        root.addView(
            boardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )
        return root
    }

    // =========================================================================
    // Operasi Simpan Internal ("✓ Simpan" & "+ Kit Baru") serta Impor/Ekspor ZIP
    // =========================================================================

    private fun handleSaveCurrentKitInternal() {
        try {
            val savedKit = session.saveCurrentEditToInternalStorage(internalKitsDir)
            getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KitSelectActivity.KEY_ACTIVE_KIT_ID, savedKit.kitId)
                .apply()
            refreshHeaderStatus()
            Toast.makeText(
                this,
                "✓ Perubahan disimpan ke kit '${savedKit.name}'",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Gagal Menyimpan Kit")
                .setMessage(e.message ?: "Terjadi kesalahan saat menyimpan kit.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showSaveAsNewKitDialog() {
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }

        val nameLabel = TextView(this).apply {
            text = "Nama Kit Baru:"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        layout.addView(nameLabel)

        val nameEdit = EditText(this).apply {
            hint = "Contoh: Kendang Koplo Panggung"
            setSingleLine(true)
        }
        layout.addView(nameEdit)

        val idLabel = TextView(this).apply {
            text = "ID Kit (otomatis, huruf kecil/angka/strip):"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        layout.addView(idLabel)

        val idEdit = EditText(this).apply {
            hint = "contoh: kendang-koplo-panggung"
            setSingleLine(true)
        }
        layout.addView(idEdit)

        nameEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val slug = (s?.toString() ?: "")
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                idEdit.setText(slug)
            }
        })

        AlertDialog.Builder(this)
            .setTitle("Simpan Menjadi Kit Baru")
            .setView(layout)
            .setPositiveButton("Simpan Kit Baru") { _, _ ->
                val newName = nameEdit.text.toString().trim()
                val newId = idEdit.text.toString().trim()
                try {
                    val created = session.saveAsNewInternalKit(newId, newName, internalKitsDir)
                    getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KitSelectActivity.KEY_ACTIVE_KIT_ID, created.kitId)
                        .apply()
                    activateKitAudio(created)
                    renderCurrentModeContent()
                    Toast.makeText(
                        this,
                        "✓ Disimpan sebagai kit baru '${created.name}'",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Membuat Kit Baru")
                        .setMessage(e.message ?: "Pastikan Nama dan ID Kit valid serta belum dipakai kit bawaan.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun launchWavPickerForSelectedZone() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, REQ_PICK_WAV)
    }

    private fun promptImportPackage() {
        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Mengimpor paket .vrkendang akan menggantikan konfigurasi yang sedang diedit. Lanjutkan?")
                .setPositiveButton("Impor") { _, _ -> launchImportPackagePicker() }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            launchImportPackagePicker()
        }
    }

    private fun launchImportPackagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_IMPORT_PACKAGE)
    }

    private fun launchExportPackagePicker() {
        val kit = session.workingKit ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "${kit.kitId}.vrkendang")
        }
        startActivityForResult(intent, REQ_EXPORT_PACKAGE)
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
                        renderCurrentModeContent()
                        val gain = res.updatedKit.drums.flatMap { it.zones }
                            .firstOrNull { it.id == selectedZoneId }?.gain ?: 1.0f
                        AudioEngine.triggerSound(slot, gain)
                        Toast.makeText(
                            this,
                            "✓ WAV dipasang ke ${selectedZoneId} (${res.sampleRate} Hz)",
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

            REQ_IMPORT_PACKAGE -> {
                try {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Tidak dapat membaca berkas yang dipilih")
                    val importedKit = input.use { session.openKitPackage(it, uri, cacheDir) }
                    // Simpan juga hasil impor ke penyimpanan internal agar langsung tersedia di Pilih Kit
                    session.saveCurrentEditToInternalStorage(internalKitsDir)
                    getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KitSelectActivity.KEY_ACTIVE_KIT_ID, importedKit.kitId)
                        .apply()

                    activateKitAudio(importedKit)
                    selectedDrumId = importedKit.drums.firstOrNull()?.id ?: "bass"
                    selectedZoneId = importedKit.drums.firstOrNull()?.zones?.firstOrNull()?.id ?: "bass-1"
                    renderCurrentModeContent()
                    Toast.makeText(
                        this,
                        "✓ Kit '${importedKit.name}' berhasil diimpor",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Mengimpor Paket .vrkendang")
                        .setMessage("Paket tidak valid atau rusak: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            REQ_EXPORT_PACKAGE -> {
                try {
                    val exported = session.saveToPackage(uri, contentResolver, cacheDir)
                    refreshHeaderStatus()
                    Toast.makeText(
                        this,
                        "✓ Kit '${exported.name}' berhasil diekspor ke file .vrkendang",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Mengekspor Paket")
                        .setMessage("Terjadi kesalahan saat mengekspor: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun handleExitRequest() {
        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Simpan perubahan pada kit '${session.workingKit?.name ?: ""}' sebelum menutup Mode Developer?")
                .setPositiveButton("Simpan") { _, _ ->
                    handleSaveCurrentKitInternal()
                    finish()
                }
                .setNeutralButton("Buang Perubahan") { _, _ ->
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
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F1C19"))
                cornerRadius = 10f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#332D28"))
            }
        }
    }

    private fun createHeaderButton(
        label: String,
        bgHex: String,
        strokeHex: String,
        onClick: () -> Unit
    ): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(strokeHex))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (32 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            setPadding((11 * density).toInt(), 0, (11 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun createActionButton(
        label: String,
        bgHex: String,
        strokeHex: String,
        onClick: () -> Unit
    ): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 10.5f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 6f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(strokeHex))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (28 * density).toInt()
            ).apply {
                marginStart = (5 * density).toInt()
            }
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
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
