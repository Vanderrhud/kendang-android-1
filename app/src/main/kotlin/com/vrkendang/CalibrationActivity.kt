package com.vrkendang

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.vrkendang.kitcore.layout.LayoutMerger
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.DrumOverride
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.LayoutOverride
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.repository.AssetManagerKitAssetSource
import com.vrkendang.repository.FileLayoutOverrideSource
import com.vrkendang.repository.KitRepository
import com.vrkendang.view.KendangBoardView
import com.vrkendang.view.LayoutOverrideStorage
import java.io.File
import kotlin.math.roundToInt

/**
 * Halaman khusus layar penuh untuk Mode Kalibrasi Tata Letak Pemain (Tahap 10).
 *
 * Dipisahkan menjadi halaman mandiri (bukan overlay yang menumpuk di atas layar bermain)
 * dengan struktur 3 baris tidak saling menutupi:
 * - Baris atas: Navigasi Kembali, judul kit aktif, Reset ke Bawaan, dan Simpan.
 * - Baris tengah: Kanvas [KendangBoardView] dalam mode [KendangBoardView.BoardMode.EDIT_LAYOUT].
 * - Baris bawah: Tombol pemilih cepat kendang dan slider ukuran adaptif (0.50x–1.50x).
 */
class CalibrationActivity : Activity() {

    companion object {
        const val EXTRA_KIT_ID = "extra_kit_id"
    }

    private lateinit var storage: LayoutOverrideStorage
    private lateinit var boardView: KendangBoardView
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var scaleValueText: TextView
    private val drumButtons = mutableMapOf<String, Button>()

    private var baseKit: KitData? = null
    private var workingKit: KitData? = null
    private var selectedDrumId: String = "bass"
    private val scalesByDrumId = mutableMapOf<String, Float>()
    private var hasUnsavedChanges: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            @Suppress("SourceLockedOrientationActivity")
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val layoutsDir = File(filesDir, "layouts")
        storage = LayoutOverrideStorage(layoutsDir)
        val kitRepository = KitRepository(
            AssetManagerKitAssetSource(assets),
            FileLayoutOverrideSource(layoutsDir)
        )

        val kitId = intent?.getStringExtra(EXTRA_KIT_ID) ?: KitRepository.DEFAULT_KIT_ID
        val activeKit = runCatching { kitRepository.selectKit(kitId) }.getOrNull()
        val base = kitRepository.getBaseKit(kitId) ?: activeKit

        if (activeKit == null || base == null) {
            finish()
            return
        }

        baseKit = base
        workingKit = activeKit

        for (drum in activeKit.drums) {
            val baseDrum = base.drums.firstOrNull { it.id == drum.id }
            val scale = if (baseDrum != null && baseDrum.radius.rx > 0f) {
                drum.radius.rx / baseDrum.radius.rx
            } else {
                1.0f
            }
            scalesByDrumId[drum.id] = scale.coerceIn(LayoutMerger.MIN_SCALE, LayoutMerger.MAX_SCALE)
        }

        setContentView(buildContentView(activeKit))
        applyImmersiveStickyMode()

        val firstDrumId = activeKit.drums.firstOrNull()?.id ?: "bass"
        selectDrum(firstDrumId)
    }

    private fun buildContentView(activeKit: KitData): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // 1. Bilah Navigasi Atas (Header Halaman Kalibrasi)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1C1917"))
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
        }

        val backBtn = createActionButton("← Kembali", "#332E2A") {
            handleExitRequest()
        }
        topBar.addView(backBtn)

        val titleText = TextView(this).apply {
            text = "  📐 Edit Tata Letak — ${activeKit.name}"
            textSize = 14f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topBar.addView(titleText)

        val resetBtn = createActionButton("↺ Reset Bawaan", "#4E342E") {
            promptResetToDefault()
        }
        topBar.addView(resetBtn)

        val saveBtn = createActionButton("💾 Simpan Tata Letak", "#2E7D32") {
            saveLayoutOverride()
        }
        topBar.addView(saveBtn)

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).toInt())
        )

        // 2. Kanvas Papan Kendang di Tengah (Bebas Tumpukan)
        boardView = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.EDIT_LAYOUT
            showZoneGuides = true
            kit = activeKit
            onDrumSelectedListener = { drum ->
                selectDrum(drum.id)
            }
            onDrumMovedListener = { drumId, newCenter ->
                onDrumMoved(drumId, newCenter)
            }
        }
        root.addView(
            boardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )

        // 3. Bilah Kontrol Bawah (Pilih Kendang + Slider Ukuran Adaptif)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1C1917"))
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
        }

        val selectLabel = TextView(this).apply {
            text = "Pilih Kendang: "
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        bottomBar.addView(selectLabel)

        drumButtons.clear()
        for (drum in activeKit.drums) {
            val btn = createActionButton(drum.id.uppercase(), "#2B2622") {
                selectDrum(drum.id)
            }
            drumButtons[drum.id] = btn
            bottomBar.addView(btn)
        }

        val scaleLabel = TextView(this).apply {
            text = "   Ukuran:"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        bottomBar.addView(scaleLabel)

        scaleSeekBar = SeekBar(this).apply {
            max = 100
            progress = 50
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = (8 * density).toInt()
                marginEnd = (8 * density).toInt()
            }
        }
        bottomBar.addView(scaleSeekBar)

        scaleValueText = TextView(this).apply {
            text = "1.00x"
            textSize = 13f
            setTextColor(Color.CYAN)
            paint.isFakeBoldText = true
        }
        bottomBar.addView(scaleValueText)

        root.addView(
            bottomBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * density).toInt())
        )

        setupSeekBarListener()
        return root
    }

    private fun createActionButton(label: String, hexColor: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(hexColor))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#55FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (34 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun selectDrum(drumId: String) {
        selectedDrumId = drumId
        boardView.selectedDrumId = drumId

        val density = resources.displayMetrics.density
        for ((id, btn) in drumButtons) {
            val isSelected = id == drumId
            btn.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (isSelected) "#FF8F00" else "#2B2622"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor(if (isSelected) "#FFD54F" else "#55FFFFFF"))
            }
            btn.setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
        }

        val currentWorking = workingKit ?: return
        val drum = currentWorking.drums.firstOrNull { it.id == drumId } ?: return
        updateSliderForSelectedDrum(drum)
    }

    private fun updateSliderForSelectedDrum(drum: DrumData) {
        val base = baseKit ?: return
        val currentWorking = workingKit ?: return
        val baseDrum = base.drums.firstOrNull { it.id == drum.id } ?: return

        val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(
            center = drum.center,
            radius = baseDrum.radius,
            canvasAspect = currentWorking.canvasAspect
        )

        val effectiveMax = minOf(LayoutMerger.MAX_SCALE, maxAdaptive)
        val currentScale = scalesByDrumId[drum.id] ?: 1.0f
        val clampedScale = currentScale.coerceIn(LayoutMerger.MIN_SCALE, effectiveMax)

        scalesByDrumId[drum.id] = clampedScale
        val progressVal = ((clampedScale - LayoutMerger.MIN_SCALE) * 100f).roundToInt()
        val maxProgressVal = ((effectiveMax - LayoutMerger.MIN_SCALE) * 100f).roundToInt().coerceAtLeast(1)

        scaleSeekBar.max = maxProgressVal
        scaleSeekBar.progress = progressVal
        scaleValueText.text = String.format("%.2fx", clampedScale)
    }

    private fun setupSeekBarListener() {
        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val currentWorking = workingKit ?: return
                val base = baseKit ?: return
                val baseDrum = base.drums.firstOrNull { it.id == selectedDrumId } ?: return
                val currentDrum = currentWorking.drums.firstOrNull { it.id == selectedDrumId } ?: return

                val newScale = LayoutMerger.MIN_SCALE + (progress / 100f)
                val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(
                    center = currentDrum.center,
                    radius = baseDrum.radius,
                    canvasAspect = currentWorking.canvasAspect
                )
                val finalScale = newScale.coerceIn(LayoutMerger.MIN_SCALE, minOf(LayoutMerger.MAX_SCALE, maxAdaptive))

                scalesByDrumId[selectedDrumId] = finalScale
                scaleValueText.text = String.format("%.2fx", finalScale)

                val clampedCenter = LayoutMerger.clampCenter(
                    center = currentDrum.center,
                    radius = baseDrum.radius,
                    canvasAspect = currentWorking.canvasAspect,
                    scale = finalScale
                )

                val updatedDrums = currentWorking.drums.map { d ->
                    if (d.id == selectedDrumId) {
                        d.copy(
                            center = clampedCenter,
                            radius = Radius(baseDrum.radius.rx * finalScale, baseDrum.radius.ry * finalScale)
                        )
                    } else d
                }

                val updatedKit = currentWorking.copy(drums = updatedDrums)
                workingKit = updatedKit
                boardView.kit = updatedKit
                hasUnsavedChanges = true
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun onDrumMoved(drumId: String, newCenter: Point) {
        val currentWorking = workingKit ?: return
        val updatedDrums = currentWorking.drums.map { d ->
            if (d.id == drumId) d.copy(center = newCenter) else d
        }
        val updatedKit = currentWorking.copy(drums = updatedDrums)
        workingKit = updatedKit
        boardView.kit = updatedKit
        hasUnsavedChanges = true

        val movedDrum = updatedDrums.firstOrNull { it.id == drumId } ?: return
        updateSliderForSelectedDrum(movedDrum)
    }

    private fun saveLayoutOverride() {
        val currentWorking = workingKit ?: return

        val drumOverrides = currentWorking.drums.associate { drum ->
            val scale = scalesByDrumId[drum.id] ?: 1.0f
            drum.id to DrumOverride(center = drum.center, scale = scale)
        }

        val override = LayoutOverride(
            kitId = currentWorking.kitId,
            baseVersion = currentWorking.version,
            drums = drumOverrides
        )

        if (storage.saveAtomic(override)) {
            hasUnsavedChanges = false
            Toast.makeText(this, "✓ Tata letak berhasil disimpan", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Gagal Menyimpan")
                .setMessage("Terjadi kendala saat menyimpan berkas tata letak. Perubahan Anda tetap ada di layar ini.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun promptResetToDefault() {
        val currentWorking = workingKit ?: return
        val base = baseKit ?: return

        AlertDialog.Builder(this)
            .setTitle("Reset ke Tata Letak Bawaan?")
            .setMessage("Kembalikan posisi dan ukuran seluruh kendang '${currentWorking.name}' ke bawaan?")
            .setPositiveButton("Reset") { _, _ ->
                storage.delete(currentWorking.kitId)
                workingKit = base
                boardView.kit = base
                scalesByDrumId.clear()
                for (drum in base.drums) {
                    scalesByDrumId[drum.id] = 1.0f
                }
                hasUnsavedChanges = false
                selectDrum(selectedDrumId)
                Toast.makeText(this, "Tata letak dikembalikan ke bawaan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun handleExitRequest() {
        if (hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Simpan perubahan posisi dan ukuran kendang sebelum kembali ke layar bermain?")
                .setPositiveButton("Simpan") { _, _ ->
                    saveLayoutOverride()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveStickyMode()
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
