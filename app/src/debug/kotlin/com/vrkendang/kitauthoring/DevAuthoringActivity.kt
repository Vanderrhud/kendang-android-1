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
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.repository.KitRepository
import com.vrkendang.view.KendangBoardView
import java.io.File
import kotlin.math.roundToInt

/**
 * Halaman Mode Developer Layar Penuh — Studio Konfigurasi per Kendang (Tahap 11).
 *
 * Sesuai Desain Penpot Terbaru ("4. Mode Developer (Konfigurasi per Kendang)"):
 * - Sisi Kiri (60% lebar layar): Kanvas Preview Kendang Besar & Bersih (tanpa daftar kartu teks yang memakan ruang
 *   dan tanpa tempelan label teks zona di atas kulit kendang), dilengkapi baris tab ringkas `[Bass] [Kecil 1] [Sedang] [Kecil 2]`.
 * - Sisi Kanan (40% lebar layar): Panel Konfigurasi Ringkas yang muat dalam 1 layar tanpa scroll:
 *   1. Baris tombol zona milik kendang terpilih (`bass-1`..`bass-5`, dst.)
 *   2. Kotak Sampel & Volume Zona (`Pilih WAV`, `▶ Tes`, `Hapus`, dan slider `Volume`)
 *   3. Kotak Parameter Busur Bass (`dx`, `dy`, `r`) yang langsung menggerakkan garis busur di kanvas besar sebelah kiri.
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
    private var showGuidesInTestPlay: Boolean = false

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
        // Penjeda audio ditangani oleh AudioLifecycleManager saat seluruh activity di background,
        // sehingga audio tetap hidup saat membuka pemilih berkas SAF (REQ_PICK_WAV) maupun saat kembali ke MainActivity.
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
    // Header Mode Developer
    // =========================================================================

    private fun buildRootLayout(): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#14110F"))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1A17"))
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
        }

        val closeBtn = createHeaderButton("← Tutup", "#2B2520", "#4A3F37") {
            handleExitRequest()
        }
        topBar.addView(closeBtn)

        headerKitLabel = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            setPadding((6 * density).toInt(), 0, (8 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topBar.addView(headerKitLabel)

        val importBtn = createHeaderButton("Impor", "#221D19", "#4A3F37") {
            promptImportPackage()
        }
        topBar.addView(importBtn)

        val exportBtn = createHeaderButton("Ekspor", "#221D19", "#4A3F37") {
            launchExportPackagePicker()
        }
        topBar.addView(exportBtn)

        val newKitBtn = createHeaderButton("+ Kit Baru", "#2B2520", "#D9822B") {
            showSaveAsNewKitDialog()
        }.apply {
            setTextColor(Color.parseColor("#FFB74D"))
        }
        topBar.addView(newKitBtn)

        val saveBtn = createHeaderButton("✓ Simpan", "#2E7D32", "#4CAF50") {
            handleSaveCurrentKitInternal()
        }
        topBar.addView(saveBtn)

        testPlayToggleBtn = createHeaderButton("▶ Uji Main", "#1565C0", "#42A5F5") {
            isTestPlayMode = !isTestPlayMode
            renderCurrentModeContent()
        }
        topBar.addView(testPlayToggleBtn)

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42 * density).toInt())
        )

        contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#14110F"))
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
            headerKitLabel.text = "-"
            return
        }
        val dirtyDot = if (session.hasUnsavedChanges) " •" else ""
        headerKitLabel.text = "${kit.name}$dirtyDot"
        headerKitLabel.setTextColor(
            if (session.hasUnsavedChanges) Color.parseColor("#FFB74D") else Color.WHITE
        )
    }

    private fun renderCurrentModeContent() {
        val density = resources.displayMetrics.density
        refreshHeaderStatus()

        if (isTestPlayMode) {
            testPlayToggleBtn.text = "⚙ Konfigurasi"
            testPlayToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#D9822B"))
                cornerRadius = 8f * density
            }
            testPlayToggleBtn.setTextColor(Color.parseColor("#14110F"))
        } else {
            testPlayToggleBtn.text = "▶ Uji Main"
            testPlayToggleBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1565C0"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#42A5F5"))
            }
            testPlayToggleBtn.setTextColor(Color.WHITE)
        }

        contentFrame.removeAllViews()
        val page = if (isTestPlayMode) buildTestPlayPage() else buildStudioSplitPage()
        contentFrame.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    // =========================================================================
    // Studio Split-Screen: 60% Kanvas Kendang Besar & Bersih (Kiri) + 40% Panel Ringkas (Kanan)
    // =========================================================================

    private fun buildStudioSplitPage(): View {
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
            setBackgroundColor(Color.parseColor("#14110F"))
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
        }

        // SISI KIRI (60% Lebar): Baris Tab Ringkas di Atas + Kanvas Preview Kendang Besar & Bersih
        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 6.0f).apply {
                marginEnd = (8 * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B1613"))
                cornerRadius = 10f * density
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#3E322B"))
            }
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
        }

        val drumTabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        leftColumn.addView(drumTabsRow)

        val rightPanelScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.0f)
            isFillViewport = true
        }

        val rightPanelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1815"))
                cornerRadius = 10f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#332B25"))
            }
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
        }
        rightPanelScroll.addView(rightPanelContainer)

        val previewBoard = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.EDIT_LAYOUT
            showZoneGuides = true
            showZoneTextBadges = false // Kulit kendang 100% bersih tanpa tempelan teks!
            fullScreenEdgeToEdge = false
            fitInsidePanel = true
            this.kit = kit
            this.slotByZoneId = activeSlotMap
            this.selectedDrumId = this@DevAuthoringActivity.selectedDrumId
            this.selectedZoneId = this@DevAuthoringActivity.selectedZoneId
        }

        previewBoard.onDrumSelectedListener = { drum ->
            selectedDrumId = drum.id
            selectedZoneId = drum.zones.first().id
            val updated = session.bringDrumToFront(drum.id)
            previewBoard.kit = updated
            previewBoard.selectedDrumId = selectedDrumId
            previewBoard.selectedZoneId = selectedZoneId
            refreshHeaderStatus()
            populateDrumTabs(drumTabsRow, previewBoard, rightPanelContainer)
            populateCompactRightPanel(rightPanelContainer, previewBoard, drumTabsRow)
        }

        previewBoard.onZoneSelectedListener = { drum, zone ->
            selectedDrumId = drum.id
            selectedZoneId = zone.id
            val updated = session.bringDrumToFront(drum.id)
            previewBoard.kit = updated
            previewBoard.selectedDrumId = selectedDrumId
            previewBoard.selectedZoneId = selectedZoneId
            refreshHeaderStatus()
            populateDrumTabs(drumTabsRow, previewBoard, rightPanelContainer)
            populateCompactRightPanel(rightPanelContainer, previewBoard, drumTabsRow)
        }

        previewBoard.onDrumMovedListener = { drumId, newCenter ->
            val clampedX = newCenter.x.coerceIn(-0.5f, 1.5f)
            val clampedY = newCenter.y.coerceIn(-0.5f, 1.5f)
            val updated = session.updateDrumCenter(drumId, com.vrkendang.kitcore.model.Point(clampedX, clampedY))
            previewBoard.kit = updated
            refreshHeaderStatus()
        }

        previewBoard.onDrumPinchScaleListener = { drumId, scaleFactor ->
            val curDrum = session.workingKit?.drums?.firstOrNull { it.id == drumId }
            if (curDrum != null) {
                val newRx = (curDrum.radius.rx * scaleFactor).coerceIn(0.05f, 1.50f)
                val newRy = (curDrum.radius.ry * scaleFactor).coerceIn(0.05f, 1.50f)
                val updated = session.updateDrumRadius(drumId, com.vrkendang.kitcore.model.Radius(newRx, newRy))
                previewBoard.kit = updated
                refreshHeaderStatus()
            }
        }

        previewBoard.onDrumEditFinishedListener = {
            populateCompactRightPanel(rightPanelContainer, previewBoard, drumTabsRow)
        }

        leftColumn.addView(
            previewBoard,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f).apply {
                topMargin = (4 * density).toInt()
            }
        )

        root.addView(leftColumn)
        root.addView(rightPanelScroll)

        populateDrumTabs(drumTabsRow, previewBoard, rightPanelContainer)
        populateCompactRightPanel(rightPanelContainer, previewBoard, drumTabsRow)

        return root
    }

    private fun populateDrumTabs(
        tabsRow: LinearLayout,
        previewBoard: KendangBoardView,
        rightPanelContainer: LinearLayout
    ) {
        tabsRow.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        for (drum in kit.drums) {
            val isSelected = (drum.id == selectedDrumId)
            val tabBtn = Button(this).apply {
                text = formatDrumDisplayName(drum.id)
                textSize = 11f
                isAllCaps = false
                setTextColor(if (isSelected) Color.parseColor("#14110F") else Color.parseColor("#E0D8D0"))
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelected) "#D9822B" else "#26201B"))
                    cornerRadius = 6f * density
                    if (!isSelected) {
                        setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#4A3F37"))
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (26 * density).toInt()
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
                setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                setOnClickListener {
                    selectedDrumId = drum.id
                    selectedZoneId = drum.zones.first().id
                    previewBoard.selectedDrumId = selectedDrumId
                    previewBoard.selectedZoneId = selectedZoneId
                    populateDrumTabs(tabsRow, previewBoard, rightPanelContainer)
                    populateCompactRightPanel(rightPanelContainer, previewBoard, tabsRow)
                }
            }
            tabsRow.addView(tabBtn)
        }

        val addDrumBtn = Button(this).apply {
            text = "+ Kendang"
            textSize = 10.5f
            isAllCaps = false
            setTextColor(Color.parseColor("#81C784"))
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B3B2B"))
                cornerRadius = 6f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#4CAF50"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (26 * density).toInt()
            )
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                showAddDrumDialog(tabsRow, previewBoard, rightPanelContainer)
            }
        }
        tabsRow.addView(addDrumBtn)
    }

    private fun showAddDrumDialog(
        tabsRow: LinearLayout,
        previewBoard: KendangBoardView,
        rightPanelContainer: LinearLayout
    ) {
        val density = resources.displayMetrics.density
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }

        val nameInputLabel = TextView(this).apply {
            text = "Nama Kendang Baru:"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        dialogLayout.addView(nameInputLabel)

        val nameEditText = EditText(this).apply {
            hint = "Misal: Kecil 3, Dut Tambahan"
            textSize = 12f
            setSingleLine()
        }
        dialogLayout.addView(nameEditText)

        val zoneCountLabel = TextView(this).apply {
            text = "Pilih Jumlah Zona Awal (1 - 4):"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
        }
        dialogLayout.addView(zoneCountLabel)

        var selectedInitialZones = 2
        val zoneButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val zoneButtons = mutableListOf<Button>()
        fun updateZoneButtonStyles() {
            for ((idx, b) in zoneButtons.withIndex()) {
                val count = idx + 1
                val isSel = (count == selectedInitialZones)
                b.setTextColor(Color.parseColor(if (isSel) "#14110F" else "#E0D8D0"))
                b.background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSel) "#FFB74D" else "#2B2520"))
                    cornerRadius = 4f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(if (isSel) "#FFA726" else "#4A3F37"))
                }
            }
        }

        for (z in 1..4) {
            val zBtn = Button(this).apply {
                text = when (z) {
                    1 -> "1"
                    2 -> "2"
                    3 -> "3"
                    else -> "4"
                }
                textSize = 11f
                isAllCaps = false
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, (30 * density).toInt(), 1.0f).apply {
                    marginEnd = (4 * density).toInt()
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    selectedInitialZones = z
                    updateZoneButtonStyles()
                }
            }
            zoneButtons.add(zBtn)
            zoneButtonsRow.addView(zBtn)
        }
        dialogLayout.addView(zoneButtonsRow)
        updateZoneButtonStyles()

        AlertDialog.Builder(this)
            .setTitle("Tambah Kendang Baru")
            .setView(dialogLayout)
            .setPositiveButton("Tambah") { _, _ ->
                val name = nameEditText.text.toString().trim()
                if (name.isNotEmpty()) {
                    runCatching {
                        val (updatedKit, newDrum) = session.addDrum(name, selectedInitialZones)
                        activateKitAudio(updatedKit)
                        selectedDrumId = newDrum.id
                        selectedZoneId = newDrum.zones.first().id
                        previewBoard.kit = updatedKit
                        previewBoard.selectedDrumId = selectedDrumId
                        previewBoard.selectedZoneId = selectedZoneId
                        refreshHeaderStatus()
                        populateDrumTabs(tabsRow, previewBoard, rightPanelContainer)
                        populateCompactRightPanel(rightPanelContainer, previewBoard, tabsRow)
                    }.onFailure { err ->
                        Toast.makeText(this, "Gagal menambah kendang: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun populateCompactRightPanel(
        rightContainer: LinearLayout,
        previewBoard: KendangBoardView,
        tabsRow: LinearLayout
    ) {
        rightContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val kit = session.workingKit ?: return

        val drum = kit.drums.firstOrNull { it.id == selectedDrumId } ?: return
        val zone = drum.zones.firstOrNull { it.id == selectedZoneId } ?: drum.zones.first()
        selectedZoneId = zone.id

        val displayName = formatDrumDisplayName(drum.id).uppercase()

        // Judul Konfigurasi Kendang Terpilih + Tombol Hapus (jika bukan bass)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        val titleTv = TextView(this).apply {
            text = "KONFIGURASI: $displayName"
            textSize = 12f
            setTextColor(Color.parseColor("#FFB74D"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerRow.addView(titleTv)

        if (drum.id != "bass") {
            val deleteDrumBtn = Button(this).apply {
                text = "Hapus Kendang"
                textSize = 9.5f
                isAllCaps = false
                setTextColor(Color.parseColor("#FF8A80"))
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3A1D1D"))
                    cornerRadius = 4f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#D32F2F"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (22 * density).toInt()
                )
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                setOnClickListener {
                    AlertDialog.Builder(this@DevAuthoringActivity)
                        .setTitle("Hapus Kendang")
                        .setMessage("Apakah Anda yakin ingin menghapus kendang '${formatDrumDisplayName(drum.id)}' beserta seluruh zonanya?")
                        .setPositiveButton("Hapus") { _, _ ->
                            runCatching {
                                val updatedKit = session.deleteDrum(drum.id)
                                activateKitAudio(updatedKit)
                                selectedDrumId = updatedKit.drums.firstOrNull()?.id ?: "bass"
                                selectedZoneId = updatedKit.drums.firstOrNull()?.zones?.firstOrNull()?.id ?: "bass-1"
                                previewBoard.kit = updatedKit
                                previewBoard.selectedDrumId = selectedDrumId
                                previewBoard.selectedZoneId = selectedZoneId
                                refreshHeaderStatus()
                                populateDrumTabs(tabsRow, previewBoard, rightContainer)
                                populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
                            }.onFailure { err ->
                                Toast.makeText(this@DevAuthoringActivity, "Gagal menghapus kendang: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            headerRow.addView(deleteDrumBtn)
        }
        rightContainer.addView(headerRow)

        // Pemilih Jumlah Zona (Bass: 1..5, Non-Bass: 1..4)
        val maxZones = if (drum.model == "bass") 5 else 4
        val countRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        val countLabel = TextView(this).apply {
            text = "Jumlah Zona:"
            textSize = 10f
            setTextColor(Color.parseColor("#A89F96"))
            setPadding(0, 0, (6 * density).toInt(), 0)
        }
        countRow.addView(countLabel)

        for (c in 1..maxZones) {
            val isSelectedCount = (drum.zones.size == c)
            val cBtn = Button(this).apply {
                text = "$c"
                textSize = 10f
                paint.isFakeBoldText = true
                setTextColor(Color.parseColor(if (isSelectedCount) "#14110F" else "#E0D8D0"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelectedCount) "#FFB74D" else "#2B2520"))
                    cornerRadius = 4f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(if (isSelectedCount) "#FFA726" else "#4A3F37"))
                }
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (22 * density).toInt()).apply {
                    marginEnd = (4 * density).toInt()
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    if (drum.zones.size != c) {
                        val updated = if (drum.model == "bass") {
                            session.setBassZoneCount(c)
                        } else {
                            session.setDrumZoneCount(drum.id, c)
                        }
                        activateKitAudio(updated)
                        previewBoard.kit = updated
                        val updatedDrum = updated.drums.first { it.id == drum.id }
                        selectedZoneId = updatedDrum.zones.first().id
                        previewBoard.selectedZoneId = selectedZoneId
                        refreshHeaderStatus()
                        populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
                    }
                }
            }
            countRow.addView(cBtn)
        }
        rightContainer.addView(countRow)

        // 1. Baris Chip Zona (1 Baris Rapi)
        val zoneChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (6 * density).toInt())
        }

        for (z in drum.zones) {
            val isZoneSelected = (z.id == selectedZoneId)
            val hasWav = (z.sound != null)

            val bgHex = when {
                isZoneSelected -> "#D9822B"
                hasWav -> "#1B3B2B"
                else -> "#29221C"
            }
            val strokeHex = when {
                isZoneSelected -> "#FFB74D"
                hasWav -> "#4CAF50"
                else -> "#4A3F37"
            }
            val txtColor = when {
                isZoneSelected -> Color.parseColor("#14110F")
                hasWav -> Color.parseColor("#81C784")
                else -> Color.parseColor("#E0D8D0")
            }

            val chip = Button(this).apply {
                text = z.id
                textSize = 10f
                isAllCaps = false
                setTextColor(txtColor)
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = 6f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(strokeHex))
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    (26 * density).toInt(),
                    1.0f
                ).apply {
                    marginEnd = (4 * density).toInt()
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    selectedZoneId = z.id
                    previewBoard.selectedZoneId = z.id
                    populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
                }
            }
            zoneChipsRow.addView(chip)
        }
        rightContainer.addView(zoneChipsRow)

        // 2. Kotak Sampel Audio & Volume Zona (Ringkas)
        val audioBox = createCompactBox().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6 * density).toInt()
            }
        }

        val hasSound = zone.sound != null
        val fileNameOnly = zone.sound?.substringAfterLast('/') ?: "Belum ada suara"
        val fileInfoTv = TextView(this).apply {
            text = "Zona ${zone.id}  •  $fileNameOnly"
            textSize = 11f
            setTextColor(Color.parseColor(if (hasSound) "#81C784" else "#FFB74D"))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        audioBox.addView(fileInfoTv)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * density).toInt())
        }

        val pickWavBtn = createCompactActionButton("Pilih WAV", "#2E7D32", "#4CAF50", 1.2f) {
            launchWavPickerForSelectedZone()
        }
        btnRow.addView(pickWavBtn)

        val testWavBtn = createCompactActionButton("▶ Tes", "#2B2520", "#4A3F37", 1.0f) {
            val curZone = session.workingKit?.drums
                ?.flatMap { it.zones }
                ?.firstOrNull { it.id == zone.id }
            if (curZone?.sound == null) {
                Toast.makeText(this, "Zona ${zone.id} belum memiliki suara", Toast.LENGTH_SHORT).show()
            } else {
                val slot = activeSlotMap[zone.id] ?: 0
                runCatching { AudioEngine.resumeStream() }
                AudioEngine.triggerSound(slot, curZone.gain)
            }
        }
        btnRow.addView(testWavBtn)

        val clearWavBtn = createCompactActionButton("Hapus", "#3A1D1D", "#8E3A3A", 1.0f) {
            val res = session.clearZoneSound(zone.id)
            if (res is DevSoundInstaller.Result.Success) {
                val slot = activeSlotMap[zone.id] ?: 0
                AudioEngine.unloadSound(slot)
                previewBoard.kit = res.updatedKit
                refreshHeaderStatus()
                populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
            }
        }
        btnRow.addView(clearWavBtn)
        audioBox.addView(btnRow)

        val gainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gainLabel = TextView(this).apply {
            text = "Volume:"
            textSize = 10.5f
            setTextColor(Color.parseColor("#C7BEB5"))
        }
        gainRow.addView(gainLabel)

        val gainValueTv = TextView(this).apply {
            text = String.format("%.2fx", zone.gain)
            textSize = 10.5f
            setTextColor(Color.parseColor("#FFB74D"))
            paint.isFakeBoldText = true
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val gainSeek = SeekBar(this).apply {
            max = 200
            progress = (zone.gain * 100f).roundToInt().coerceIn(0, 200)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
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
        audioBox.addView(gainRow)

        rightContainer.addView(audioBox)

        // 3. Kotak Posisi & Ukuran Kendang (Manipulasi Tata Letak)
        val posSizeBox = createCompactBox()
        val posSizeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (2 * density).toInt())
        }
        val posSizeTitle = TextView(this).apply {
            text = "POSISI & UKURAN: $displayName"
            textSize = 10f
            setTextColor(Color.parseColor("#A89F96"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        posSizeHeader.addView(posSizeTitle)

        val bringFrontBtn = Button(this).apply {
            text = "▲ Bawa ke Depan"
            textSize = 9.5f
            setTextColor(Color.parseColor("#FFE082"))
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3E2723"))
                cornerRadius = 4f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#FFA000"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (22 * density).toInt()
            )
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            setOnClickListener {
                val updated = session.bringDrumToFront(drum.id)
                previewBoard.kit = updated
                refreshHeaderStatus()
                populateDrumTabs(tabsRow, previewBoard, rightContainer)
                populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
            }
        }
        posSizeHeader.addView(bringFrontBtn)
        posSizeBox.addView(posSizeHeader)

        posSizeBox.addView(
            buildCompactDividerSlider("Posisi X", drum.center.x, 0.05f, 0.95f) { newX ->
                val curDrum = session.workingKit?.drums?.firstOrNull { it.id == drum.id } ?: return@buildCompactDividerSlider
                val updated = session.updateDrumCenter(drum.id, com.vrkendang.kitcore.model.Point(newX, curDrum.center.y))
                previewBoard.kit = updated
                refreshHeaderStatus()
            }
        )

        posSizeBox.addView(
            buildCompactDividerSlider("Posisi Y", drum.center.y, 0.05f, 0.95f) { newY ->
                val curDrum = session.workingKit?.drums?.firstOrNull { it.id == drum.id } ?: return@buildCompactDividerSlider
                val updated = session.updateDrumCenter(drum.id, com.vrkendang.kitcore.model.Point(curDrum.center.x, newY))
                previewBoard.kit = updated
                refreshHeaderStatus()
            }
        )

        posSizeBox.addView(
            buildCompactDividerSlider("Radius/Ukuran", drum.radius.rx, 0.08f, 0.50f) { newR ->
                val updated = session.updateDrumRadius(drum.id, com.vrkendang.kitcore.model.Radius(newR, newR))
                previewBoard.kit = updated
                refreshHeaderStatus()
            }
        )
        rightContainer.addView(posSizeBox)

        // 4. Kotak Parameter Geometri Eksklusif per Zona Terpilih (Khusus Bass)
        if (drum.model == "bass" && drum.zones.size > 1 && drum.divider != null) {
            val divider = drum.divider!!

            if (selectedZoneId == "bass-1") {
                // Parameter Bentuk Oval untuk Zona Utama (bass-1)
                val bassBox = createCompactBox()

                val bassHeaderRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, (2 * density).toInt())
                }

                val bassTitle = TextView(this).apply {
                    text = "PARAMETER OVAL: BASS-1 (UTAMA)"
                    textSize = 10f
                    setTextColor(Color.parseColor("#A89F96"))
                    paint.isFakeBoldText = true
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                }
                bassHeaderRow.addView(bassTitle)

                val resetArcBtn = Button(this).apply {
                    text = "↺ Reset"
                    textSize = 9.5f
                    setTextColor(Color.parseColor("#E0D8D0"))
                    isAllCaps = false
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#2B2520"))
                        cornerRadius = 5f * density
                        setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#4A3F37"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (22 * density).toInt()
                    )
                    setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                    setOnClickListener {
                        val updated = session.updateBassDivider(
                            dx = -0.30f,
                            dy = 0.0f,
                            r = 0.80f,
                            rx = 0.80f,
                            ry = 0.80f
                        )
                        previewBoard.kit = updated
                        refreshHeaderStatus()
                        populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
                    }
                }
                bassHeaderRow.addView(resetArcBtn)
                bassBox.addView(bassHeaderRow)

                bassBox.addView(
                    buildCompactDividerSlider("Geser X (dx)", divider.dx, -3.0f, 3.0f) { newDx ->
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildCompactDividerSlider
                        runCatching {
                            val updated = session.updateBassDivider(
                                dx = newDx,
                                dy = curDiv.dy,
                                r = curDiv.r,
                                rx = curDiv.effectiveRx,
                                ry = curDiv.effectiveRy
                            )
                            previewBoard.kit = updated
                            refreshHeaderStatus()
                        }
                    }
                )

                bassBox.addView(
                    buildCompactDividerSlider("Geser Y (dy)", divider.dy, -3.0f, 3.0f) { newDy ->
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildCompactDividerSlider
                        runCatching {
                            val updated = session.updateBassDivider(
                                dx = curDiv.dx,
                                dy = newDy,
                                r = curDiv.r,
                                rx = curDiv.effectiveRx,
                                ry = curDiv.effectiveRy
                            )
                            previewBoard.kit = updated
                            refreshHeaderStatus()
                        }
                    }
                )

                bassBox.addView(
                    buildCompactDividerSlider("Lebar (rx)", divider.effectiveRx, 0.10f, 3.0f) { newRx ->
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildCompactDividerSlider
                        runCatching {
                            val updated = session.updateBassDivider(
                                dx = curDiv.dx,
                                dy = curDiv.dy,
                                r = newRx,
                                rx = newRx,
                                ry = curDiv.effectiveRy
                            )
                            previewBoard.kit = updated
                            refreshHeaderStatus()
                        }
                    }
                )

                bassBox.addView(
                    buildCompactDividerSlider("Tinggi (ry)", divider.effectiveRy, 0.10f, 3.0f) { newRy ->
                        val curDiv = session.workingKit?.drums?.firstOrNull { it.model == "bass" }?.divider ?: return@buildCompactDividerSlider
                        runCatching {
                            val updated = session.updateBassDivider(
                                dx = curDiv.dx,
                                dy = curDiv.dy,
                                r = curDiv.effectiveRx,
                                rx = curDiv.effectiveRx,
                                ry = newRy
                            )
                            previewBoard.kit = updated
                            refreshHeaderStatus()
                        }
                    }
                )

                rightContainer.addView(bassBox)
            } else {
                // Parameter Batas Sekat untuk Zona Tepi (bass-2, bass-3, dst.)
                val crescentZones = drum.zones.filter { it.region == "crescent" }
                val cIdx = crescentZones.indexOfFirst { it.id == selectedZoneId }

                if (crescentZones.size > 1 && cIdx >= 0) {
                    val sekatBox = createCompactBox()

                    val sekatHeaderRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, (2 * density).toInt())
                    }

                    val sekatTitle = TextView(this).apply {
                        text = "BATAS SEKAT: ${selectedZoneId.uppercase()}"
                        textSize = 10f
                        setTextColor(Color.parseColor("#A89F96"))
                        paint.isFakeBoldText = true
                    }
                    sekatHeaderRow.addView(sekatTitle)
                    sekatBox.addView(sekatHeaderRow)

                    val defaultSplits = when (crescentZones.size) {
                        2 -> listOf(0.50f)
                        3 -> listOf(0.333f, 0.667f)
                        4 -> listOf(0.25f, 0.50f, 0.75f)
                        else -> emptyList()
                    }
                    val curSplits = divider.splits?.takeIf { it.size == crescentZones.size - 1 } ?: defaultSplits

                    if (cIdx < crescentZones.size - 1) {
                        val currentVal = curSplits.getOrNull(cIdx) ?: 0.5f
                        sekatBox.addView(
                            buildCompactDividerSlider("Posisi Sekat Bawah", currentVal, 0.05f, 0.95f) { newPos ->
                                runCatching {
                                    val updated = session.updateBassSplit(cIdx, newPos)
                                    previewBoard.kit = updated
                                    refreshHeaderStatus()
                                }
                            }
                        )
                    } else if (cIdx > 0) {
                        val currentVal = curSplits.getOrNull(cIdx - 1) ?: 0.5f
                        sekatBox.addView(
                            buildCompactDividerSlider("Posisi Sekat Atas", currentVal, 0.05f, 0.95f) { newPos ->
                                runCatching {
                                    val updated = session.updateBassSplit(cIdx - 1, newPos)
                                    previewBoard.kit = updated
                                    refreshHeaderStatus()
                                }
                            }
                        )
                    }

                    rightContainer.addView(sekatBox)
                }
            }
        }

        // 4. Parameter Putaran Sudut (Khusus Kendang Non-Bass dengan >= 2 Zona)
        if (drum.model != "bass" && drum.zones.size >= 2) {
            val angleBox = createCompactBox()
            val angleHeaderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (2 * density).toInt())
            }
            val angleTitle = TextView(this).apply {
                text = "PARAMETER PUTARAN SUDUT (${drum.zones.size} ZONA)"
                textSize = 10f
                setTextColor(Color.parseColor("#A89F96"))
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            angleHeaderRow.addView(angleTitle)

            val resetAngleBtn = Button(this).apply {
                text = "↺ Reset Sudut"
                textSize = 9.5f
                setTextColor(Color.parseColor("#E0D8D0"))
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2B2520"))
                    cornerRadius = 5f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#4A3F37"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (22 * density).toInt()
                )
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                setOnClickListener {
                    val updated = session.updateDrumAngle(drum.id, 0f)
                    previewBoard.kit = updated
                    refreshHeaderStatus()
                    populateCompactRightPanel(rightContainer, previewBoard, tabsRow)
                }
            }
            angleHeaderRow.addView(resetAngleBtn)
            angleBox.addView(angleHeaderRow)

            val curAngle = drum.divider?.angle ?: 0f
            angleBox.addView(
                buildCompactDividerSlider("Sudut Rotasi (0° - 360°)", curAngle, 0.0f, 360.0f) { newAngle ->
                    runCatching {
                        val updated = session.updateDrumAngle(drum.id, newAngle)
                        previewBoard.kit = updated
                        refreshHeaderStatus()
                    }
                }
            )
            rightContainer.addView(angleBox)
        }
    }

    private fun buildCompactDividerSlider(
        label: String,
        initialValue: Float,
        minVal: Float,
        maxVal: Float,
        onValueChanged: (Float) -> Unit
    ): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val labelTv = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.parseColor("#E0D8D0"))
            layoutParams = LinearLayout.LayoutParams((78 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(labelTv)

        val valueTv = TextView(this).apply {
            text = String.format("%.2f", initialValue)
            textSize = 10f
            setTextColor(Color.parseColor("#00E5FF"))
            paint.isFakeBoldText = true
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val maxSteps = ((maxVal - minVal) * 100f).roundToInt().coerceAtLeast(1)
        val initialProg = ((initialValue - minVal) * 100f).roundToInt().coerceIn(0, maxSteps)

        val seek = SeekBar(this).apply {
            max = maxSteps
            progress = initialProg
            layoutParams = LinearLayout.LayoutParams(0, (24 * density).toInt(), 1.0f)
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
    // Halaman Mode Uji Main Penuh (100% Full Screen di bawah Header)
    // =========================================================================

    private fun buildTestPlayPage(): View {
        val kit = session.workingKit ?: return TextView(this)
        return KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.PLAY
            showZoneGuides = showGuidesInTestPlay
            showZoneTextBadges = false
            fullScreenEdgeToEdge = true
            fitInsidePanel = false
            this.kit = kit
            this.slotByZoneId = activeSlotMap
        }
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
            hint = "Contoh: Koplotan Panggung"
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
            hint = "contoh: koplotan-panggung"
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
                        runCatching { AudioEngine.resumeStream() }
                        AudioEngine.triggerSound(slot, gain)
                        Toast.makeText(
                            this,
                            "✓ WAV dipasang ke $selectedZoneId (${res.sampleRate} Hz)",
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
    // Helper Pembangun Komponen UI Ringkas
    // =========================================================================

    private fun createCompactBox(): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#241E1A"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#38302A"))
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
                (30 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun createCompactActionButton(
        label: String,
        bgHex: String,
        strokeHex: String,
        weight: Float,
        onClick: () -> Unit
    ): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 6f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(strokeHex))
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                (26 * density).toInt(),
                weight
            ).apply {
                marginEnd = (4 * density).toInt()
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun applyImmersiveStickyMode() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
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
