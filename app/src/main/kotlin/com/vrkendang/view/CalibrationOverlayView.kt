package com.vrkendang.view

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
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
import java.io.File
import kotlin.math.roundToInt

/**
 * Tampilan overlay dan pengontrol Mode Kalibrasi Tata Letak Pemain (Tahap 10).
 *
 * Sesuai Spesifikasi Calibration Mode & Desain D11:
 * - Menampilkan garis panduan batas zona akustik transparan presisi (C_div, bulan sabit, split)
 *   yang mengikuti pergerakan dan skala kendang secara real-time (Task 10.1).
 * - Interaksi pemilihan dan penggeseran satu jari dengan penjepitan kanvas, hening total (Task 10.2).
 * - Slider skala adaptif per kendang (0.5x–1.5x) dengan batas maksimum adaptif otomatis (Task 10.3).
 * - Tombol Simpan atomik (.tmp), Batal, dan konfirmasi perubahan belum disimpan (Task 10.4).
 * - Tombol Reset ke Bawaan dengan dialog konfirmasi yang menghapus berkas override (Task 10.5).
 * - Tata letak kontrol ringkas di bilah bawah landscape agar kendang tetap terlihat jelas (Task 10.6).
 */
class CalibrationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Referensi ke board view yang sedang dikalibrasi. */
    var boardView: KendangBoardView? = null

    /** Referensi ke HeaderView untuk menonaktifkan pemilih kit selama kalibrasi. */
    var headerView: HeaderView? = null

    /** Callback saat mode kalibrasi ditutup (simpan atau batal). */
    var onCalibrationClosed: (() -> Unit)? = null

    private val storage = LayoutOverrideStorage(File(context.filesDir, "layouts"))

    // State kalibrasi
    private var baseKit: KitData? = null
    private var initialKit: KitData? = null
    private var workingKit: KitData? = null
    private var selectedDrumId: String? = null
    private val scalesByDrumId = mutableMapOf<String, Float>()
    private var hasUnsavedChanges = false

    // Komponen UI kontrol bawah
    private val selectedDrumText: TextView
    private val scaleSeekBar: SeekBar
    private val scaleValueText: TextView
    private val resetButton: Button
    private val cancelButton: Button
    private val saveButton: Button

    // Paints untuk garis panduan zona akustik transparan
    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 229, 255) // Cyan semi-transparan
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val bandGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 215, 0) // Emas/Kuning semi-transparan
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    init {
        // Izinkan onDraw pada FrameLayout
        setWillNotDraw(false)

        val density = resources.displayMetrics.density

        // Toolbar kontrol bawah (Task 10.6: ringkas, tinggi ~44dp di landscape)
        val controlBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E6181614"))
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
        }

        // Label kendang terpilih
        selectedDrumText = TextView(context).apply {
            text = "Kendang: -"
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            setPadding(0, 0, (12 * density).toInt(), 0)
        }
        controlBar.addView(selectedDrumText)

        // Label Slider Skala
        val sliderLabel = TextView(context).apply {
            text = "Ukuran:"
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }
        controlBar.addView(sliderLabel)

        // SeekBar Skala (0.50x s.d. 1.50x dipetakan ke 50..150)
        scaleSeekBar = SeekBar(context).apply {
            max = 100 // 0 = 0.5x, 100 = 1.5x
            progress = 50 // default 1.0x (50 + 50 = 100 -> 1.0f)
            val seekParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = (6 * density).toInt()
                marginEnd = (6 * density).toInt()
            }
            layoutParams = seekParams
        }
        controlBar.addView(scaleSeekBar)

        scaleValueText = TextView(context).apply {
            text = "1.00x"
            textSize = 12f
            setTextColor(Color.CYAN)
            setPadding(0, 0, (12 * density).toInt(), 0)
        }
        controlBar.addView(scaleValueText)

        // Tombol Reset ke Bawaan (Task 10.5)
        resetButton = createCompactButton(context, "Reset", Color.parseColor("#3E2723"), Color.parseColor("#D7CCC8")) {
            promptResetToDefault()
        }
        controlBar.addView(resetButton)

        // Tombol Batal (Task 10.4)
        cancelButton = createCompactButton(context, "Batal", Color.parseColor("#424242"), Color.WHITE) {
            handleCancel()
        }
        controlBar.addView(cancelButton)

        // Tombol Simpan (Task 10.4)
        saveButton = createCompactButton(context, "Simpan", Color.parseColor("#1B5E20"), Color.WHITE) {
            saveLayoutOverride()
        }
        controlBar.addView(saveButton)

        val barParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            (44 * density).toInt()
        ).apply {
            gravity = Gravity.BOTTOM
        }
        addView(controlBar, barParams)

        setupSeekBarListener()
    }

    private fun createCompactButton(
        context: Context,
        label: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): Button {
        val density = resources.displayMetrics.density
        return Button(context).apply {
            text = label
            textSize = 11f
            setTextColor(textColor)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = (8 * density)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (32 * density).toInt()
            ).apply {
                marginStart = (4 * density).toInt()
            }
            layoutParams = lp
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }

    /**
     * Membuka mode kalibrasi dengan [activeKit] dan [baseKitWithoutOverride].
     */
    fun startCalibration(activeKit: KitData, baseKitWithoutOverride: KitData) {
        this.baseKit = baseKitWithoutOverride
        this.initialKit = activeKit
        this.workingKit = activeKit
        this.hasUnsavedChanges = false

        // Inisialisasi skala per kendang dari kit aktif
        scalesByDrumId.clear()
        for (drum in activeKit.drums) {
            val baseDrum = baseKitWithoutOverride.drums.firstOrNull { it.id == drum.id }
            val scale = if (baseDrum != null && baseDrum.radius.rx > 0f) {
                drum.radius.rx / baseDrum.radius.rx
            } else {
                1.0f
            }
            scalesByDrumId[drum.id] = scale.coerceIn(LayoutMerger.MIN_SCALE, LayoutMerger.MAX_SCALE)
        }

        // Nonaktifkan pemilih kit di header (Task 10.1)
        headerView?.kitSelectorButton?.isEnabled = false

        // Beralih ke mode edit tata letak pada board view
        val board = boardView
        if (board != null) {
            board.mode = KendangBoardView.BoardMode.EDIT_LAYOUT
            board.kit = activeKit

            board.onDrumSelectedListener = { drum ->
                selectDrum(drum.id)
            }

            board.onDrumMovedListener = { drumId, newCenter ->
                onDrumMoved(drumId, newCenter)
            }
        }

        visibility = View.VISIBLE

        // Pilih kendang pertama (default: Bass)
        val firstDrumId = activeKit.drums.firstOrNull()?.id ?: "bass"
        selectDrum(firstDrumId)
        invalidate()
    }

    /**
     * Memilih kendang untuk dikalibrasi (Task 10.2).
     */
    fun selectDrum(drumId: String) {
        selectedDrumId = drumId
        boardView?.selectedDrumId = drumId

        val currentWorking = workingKit ?: return
        val drum = currentWorking.drums.firstOrNull { it.id == drumId } ?: return

        selectedDrumText.text = "Kendang: ${drum.id.uppercase()}"
        updateSliderForSelectedDrum(drum)
        invalidate()
    }

    /**
     * Memperbarui batas adaptif dan nilai slider ukuran untuk kendang terpilih (Task 10.3).
     */
    private fun updateSliderForSelectedDrum(drum: DrumData) {
        val base = baseKit ?: return
        val currentWorking = workingKit ?: return
        val baseDrum = base.drums.firstOrNull { it.id == drum.id } ?: return

        val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(
            center = drum.center,
            radius = baseDrum.radius,
            canvasAspect = currentWorking.canvasAspect
        )

        // Skala 0.50x s.d. maxAdaptive (maks 5.00x)
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

                val drumId = selectedDrumId ?: return
                val currentWorking = workingKit ?: return
                val base = baseKit ?: return
                val baseDrum = base.drums.firstOrNull { it.id == drumId } ?: return

                val newScale = LayoutMerger.MIN_SCALE + (progress / 100f)
                val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(
                    center = currentWorking.drums.first { it.id == drumId }.center,
                    radius = baseDrum.radius,
                    canvasAspect = currentWorking.canvasAspect
                )
                val finalScale = newScale.coerceIn(LayoutMerger.MIN_SCALE, minOf(LayoutMerger.MAX_SCALE, maxAdaptive))

                scalesByDrumId[drumId] = finalScale
                scaleValueText.text = String.format("%.2fx", finalScale)

                // Terapkan perubahan ukuran proporsional rx/ry dan jepit center
                val drum = currentWorking.drums.first { it.id == drumId }
                val clampedCenter = LayoutMerger.clampCenter(
                    center = drum.center,
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

                val newWorkingKit = currentWorking.copy(drums = updatedDrums)
                workingKit = newWorkingKit
                boardView?.kit = newWorkingKit
                hasUnsavedChanges = true

                invalidate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Dipanggil saat posisi kendang digeser oleh pemain di board view (Task 10.2).
     */
    private fun onDrumMoved(drumId: String, newCenter: Point) {
        val currentWorking = workingKit ?: return
        val updatedDrums = currentWorking.drums.map { d ->
            if (d.id == drumId) d.copy(center = newCenter) else d
        }
        val newWorkingKit = currentWorking.copy(drums = updatedDrums)
        workingKit = newWorkingKit
        boardView?.kit = newWorkingKit
        hasUnsavedChanges = true

        // Perbarui batas slider adaptif untuk posisi baru (Task 10.3)
        val drum = updatedDrums.first { it.id == drumId }
        updateSliderForSelectedDrum(drum)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentWorking = workingKit ?: return
        val mapper = CanvasCoordinateMapper(
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            canvasAspect = currentWorking.canvasAspect
        )

        // Gambar garis panduan batas zona akustik transparan dari geometri hit-testing (Task 10.1)
        for (drum in currentWorking.drums) {
            val cx = mapper.toPixelX(drum.center.x)
            val cy = mapper.toPixelY(drum.center.y)
            val rx = mapper.toPixelRadiusX(drum.radius.rx)
            val ry = mapper.toPixelRadiusY(drum.radius.ry)

            if (rx <= 0f || ry <= 0f) continue

            val ovalRect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            val ovalPath = Path().apply { addOval(ovalRect, Path.Direction.CW) }

            when (drum.model) {
                "bass" -> {
                    val divider = drum.divider ?: continue
                    val divCenterX = cx + (divider.dx * rx)
                    val divCenterY = cy + (divider.dy * ry)
                    val divRadius = divider.r * rx

                    canvas.save()
                    canvas.clipPath(ovalPath)
                    canvas.drawCircle(divCenterX, divCenterY, divRadius, guideLinePaint)

                    val top = cy - ry
                    val bandHeight = (2f * ry) / 4f
                    for (i in 1..3) {
                        val y = top + i * bandHeight
                        canvas.drawLine(cx - rx, y, cx + rx, y, bandGuidePaint)
                    }
                    canvas.restore()
                }

                "split" -> {
                    val zoneCount = drum.zones.size
                    val angleOffset = drum.divider?.angle ?: 0f
                    if (zoneCount == 2 && angleOffset == 0f && drum.zones.any { it.region == "upper" }) {
                        canvas.save()
                        canvas.clipPath(ovalPath)
                        canvas.drawLine(cx - rx, cy, cx + rx, cy, guideLinePaint)
                        canvas.restore()
                    } else if (zoneCount > 1) {
                        canvas.save()
                        canvas.clipPath(ovalPath)
                        val sliceDeg = 360f / zoneCount
                        for (i in 0 until zoneCount) {
                            val spokeAngleDeg = angleOffset + (i * sliceDeg)
                            val spokeRad = Math.toRadians(spokeAngleDeg.toDouble())
                            val targetX = cx + (rx * kotlin.math.cos(spokeRad)).toFloat()
                            val targetY = cy + (ry * kotlin.math.sin(spokeRad)).toFloat()
                            canvas.drawLine(cx, cy, targetX, targetY, guideLinePaint)
                        }
                        canvas.restore()
                    }
                }
            }
        }
    }

    /**
     * Menyimpan tata letak baru secara atomik (Task 10.4).
     */
    fun saveLayoutOverride() {
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

        val success = storage.saveAtomic(override)
        if (success) {
            hasUnsavedChanges = false
            Toast.makeText(context, "Tata letak berhasil disimpan", Toast.LENGTH_SHORT).show()
            closeCalibration()
        } else {
            // Kegagalan simpan menampilkan pesan galat tanpa menghilangkan perubahan (Task 10.4)
            AlertDialog.Builder(context)
                .setTitle("Gagal Menyimpan")
                .setMessage("Terjadi kendala saat menyimpan berkas tata letak. Perubahan Anda tetap tersimpan di layar kalibrasi.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Membatalkan perubahan dan mengembalikan tata letak semula (Task 10.4).
     */
    fun handleCancel() {
        if (hasUnsavedChanges) {
            AlertDialog.Builder(context)
                .setTitle("Buang Perubahan?")
                .setMessage("Semua perubahan posisi dan ukuran kendang yang belum disimpan akan dibatalkan.")
                .setPositiveButton("Buang") { _, _ ->
                    revertAndClose()
                }
                .setNegativeButton("Tetap Kalibrasi", null)
                .show()
        } else {
            revertAndClose()
        }
    }

    private fun revertAndClose() {
        val initial = initialKit
        if (initial != null) {
            boardView?.kit = initial
        }
        hasUnsavedChanges = false
        closeCalibration()
    }

    /**
     * Reset tata letak ke bawaan kit (Task 10.5).
     */
    fun promptResetToDefault() {
        val currentWorking = workingKit ?: return

        AlertDialog.Builder(context)
            .setTitle("Reset ke Bawaan?")
            .setMessage("Kembalikan seluruh kendang ke posisi dan ukuran bawaan '${currentWorking.name}'?")
            .setPositiveButton("Reset") { _, _ ->
                storage.delete(currentWorking.kitId)
                val base = baseKit ?: return@setPositiveButton
                workingKit = base
                boardView?.kit = base

                scalesByDrumId.clear()
                hasUnsavedChanges = true

                val drum = base.drums.firstOrNull { it.id == selectedDrumId } ?: base.drums.first()
                selectDrum(drum.id)
                Toast.makeText(context, "Tata letak dikembalikan ke bawaan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Menangani event tombol back sistem (Task 10.4).
     * @return true jika event dikonsumsi oleh mode kalibrasi, false jika tidak aktif.
     */
    fun handleBackPressed(): Boolean {
        if (visibility != View.VISIBLE) return false

        if (hasUnsavedChanges) {
            AlertDialog.Builder(context)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Simpan perubahan tata letak kendang sebelum keluar?")
                .setPositiveButton("Simpan") { _, _ ->
                    saveLayoutOverride()
                }
                .setNeutralButton("Buang") { _, _ ->
                    revertAndClose()
                }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            revertAndClose()
        }
        return true
    }

    private fun closeCalibration() {
        visibility = View.GONE
        headerView?.kitSelectorButton?.isEnabled = true
        val board = boardView
        if (board != null) {
            board.mode = KendangBoardView.BoardMode.PLAY
            board.selectedDrumId = null
        }
        onCalibrationClosed?.invoke()
    }
}
