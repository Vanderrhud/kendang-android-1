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
import android.widget.TextView
import android.widget.Toast
import com.vrkendang.kitcore.layout.LayoutMerger
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

/**
 * Halaman Sesuaikan Layar Penuh untuk Pemain (Tahap 10 — Geser 1 Jari & Cubit 2 Jari).
 *
 * Sesuai Desain Penpot ("3. Halaman Sesuaikan (Geser & Cubit)") & Spesifikasi Calibration Mode:
 * - Hanya untuk mengatur posisi dan ukuran 4 kendang (`Bass`, `Kecil 1`, `Sedang`, `Kecil 2`).
 * - Bebas dari panel bawah atau slider penutup kanvas.
 * - Geser 1 jari memindahkan posisi kendang terpilih.
 * - Cubit 2 jari mengubah ukuran kendang terpilih secara proporsional (0.5x–1.5x) di dalam batas kanvas.
 * - Header atas: "✕ Batal" (kiri), petunjuk singkat di tengah, serta "↺ Reset" dan "✓ Simpan" (kanan).
 */
class CalibrationActivity : Activity() {

    companion object {
        const val EXTRA_KIT_ID = "extra_kit_id"
    }

    private lateinit var storage: LayoutOverrideStorage
    private lateinit var boardView: KendangBoardView

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
        val internalKitsDir = File(filesDir, "kits")
        storage = LayoutOverrideStorage(layoutsDir)
        val kitRepository = KitRepository(
            assetSource = AssetManagerKitAssetSource(assets),
            layoutOverrideSource = FileLayoutOverrideSource(layoutsDir),
            internalKitsDir = internalKitsDir
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
            setBackgroundColor(Color.parseColor("#141210"))
        }

        // 1. Bilah Navigasi Atas (Header Halaman Sesuaikan sesuai Desain Penpot)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1715"))
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
        }

        val cancelBtn = createActionButton("✕ Batal", "#2A2622", "#443D36") {
            handleExitRequest()
        }
        topBar.addView(cancelBtn)

        val hintText = TextView(this).apply {
            text = "Sesuaikan: Geser kendang untuk pindah posisi • Cubit 2 jari untuk ubah ukuran"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD54F"))
            gravity = Gravity.CENTER
            paint.isFakeBoldText = true
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topBar.addView(hintText)

        val resetBtn = createActionButton("↺ Reset", "#3E2723", "#8D6E63") {
            promptResetToDefault()
        }
        topBar.addView(resetBtn)

        val saveBtn = createActionButton("✓ Simpan", "#2E7D32", "#66BB6A") {
            saveLayoutOverride()
        }
        topBar.addView(saveBtn)

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46 * density).toInt())
        )

        // 2. Kanvas Papan Kendang Penuh (Tanpa bilah bawah / slider penutup)
        boardView = KendangBoardView(this).apply {
            mode = KendangBoardView.BoardMode.EDIT_LAYOUT
            showZoneGuides = false
            kit = activeKit
            onDrumSelectedListener = { drum ->
                selectDrum(drum.id)
            }
            onDrumMovedListener = { drumId, newCenter ->
                onDrumMoved(drumId, newCenter)
            }
            onDrumPinchScaleListener = { drumId, scaleFactor ->
                onDrumPinchScaled(drumId, scaleFactor)
            }
        }
        root.addView(
            boardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )

        return root
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
            textSize = 11.5f
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
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun selectDrum(drumId: String) {
        selectedDrumId = drumId
        boardView.selectedDrumId = drumId
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
    }

    private fun onDrumPinchScaled(drumId: String, scaleFactor: Float) {
        val currentWorking = workingKit ?: return
        val base = baseKit ?: return
        val baseDrum = base.drums.firstOrNull { it.id == drumId } ?: return
        val currentDrum = currentWorking.drums.firstOrNull { it.id == drumId } ?: return

        val previousScale = scalesByDrumId[drumId] ?: 1.0f
        val proposedScale = previousScale * scaleFactor

        val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(
            center = currentDrum.center,
            radius = baseDrum.radius,
            canvasAspect = currentWorking.canvasAspect
        )
        val finalScale = proposedScale.coerceIn(
            LayoutMerger.MIN_SCALE,
            minOf(LayoutMerger.MAX_SCALE, maxAdaptive)
        )

        scalesByDrumId[drumId] = finalScale

        val clampedCenter = LayoutMerger.clampCenter(
            center = currentDrum.center,
            radius = baseDrum.radius,
            canvasAspect = currentWorking.canvasAspect,
            scale = finalScale
        )

        val updatedDrums = currentWorking.drums.map { d ->
            if (d.id == drumId) {
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
            Toast.makeText(this, "✓ Tata letak disimpan", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Gagal Menyimpan")
                .setMessage("Terjadi kendala saat menyimpan tata letak. Perubahan Anda tetap ada di layar ini.")
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
                .setMessage("Simpan perubahan posisi dan ukuran kendang sebelum kembali?")
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
