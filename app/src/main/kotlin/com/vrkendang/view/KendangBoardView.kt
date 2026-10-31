package com.vrkendang.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.audio.AudioEngineApiImpl
import com.vrkendang.kitcore.hittest.HitTester
import com.vrkendang.kitcore.layout.LayoutMerger
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.ZoneData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Custom View tunggal untuk menampilkan seluruh papan kendang dan memproses sentuhan multi-touch.
 *
 * Sesuai Desain D1, D5, D7, D13 & Spesifikasi Kendang Board:
 * - Menangani seluruh event multi-touch secara terpusat tanpa batas jumlah jari.
 * - Menggunakan sistem aspect-fit berbasis tinggi view dan `canvasAspect` kit.
 * - Lingkaran kendang (rx == ry) selalu lingkaran sejati di rasio layar apapun.
 * - Sentuhan di area margin diabaikan.
 * - Suara dan visual feedback hanya dipicu oleh ACTION_DOWN dan ACTION_POINTER_DOWN.
 * - ACTION_MOVE tidak memicu suara baru; ACTION_UP/POINTER_UP/CANCEL tidak memicu suara dan tidak crash.
 * - Jika `sound != null`, memicu antrean AudioEngine; jika `sound == null`, suara dilewati (graceful silence).
 * - Menampilkan visual feedback presisi per zona (~100 ms) bahkan untuk zona tanpa suara.
 * - Mendukung Mode Bermain (PLAY) dan Mode Edit Tata Letak (EDIT_LAYOUT).
 * - Mendaftarkan System Gesture Exclusion Rects pada Android 10+ (API 29+) di tepi kiri-kanan.
 */
class KendangBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Mode operasi papan kendang (Task 7.6).
     */
    enum class BoardMode {
        /** Mode bermain: sentuhan memicu suara audio dan highlight visual. */
        PLAY,

        /** Mode edit tata letak: manipulasi posisi/ukuran kendang tanpa memicu suara audio. */
        EDIT_LAYOUT,

        /** Mode seleksi zona (Mode Developer): mengetuk zona memilih zona untuk diisi WAV/diatur gain. */
        SELECT_ZONE
    }

    /** Mode aktif saat ini (default: PLAY). */
    var mode: BoardMode = BoardMode.PLAY
        set(value) {
            field = value
            activeHighlights.clear()
            invalidate()
        }

    /** Menampilkan garis batas zona, label zona, dan indikator status WAV di atas kulit kendang. */
    var showZoneGuides: Boolean = false
        set(value) {
            field = value
            zoneGuideOverlay.isEnabled = value
            invalidate()
        }

    /**
     * Data kit aktif yang digambar dan diuji sentuhannya.
     * Harus merupakan KitData efektif (sudah digabung dengan layout override jika ada).
     */
    var kit: KitData? = null
        set(value) {
            field = value
            updateMapper()
            invalidate()
        }

    /**
     * Pemetaan ID zona (`zone.id`) ke slot audio engine (0..31).
     * Diberikan oleh [com.vrkendang.audio.KitAudioLoader] saat aktivasi kit.
     */
    var slotByZoneId: Map<String, Int> = emptyMap()

    /** JNI API bridge ke mesin audio C++ Oboe (dapat disuntikkan fake saat test). */
    var audioEngine: AudioEngineApi = AudioEngineApiImpl

    /** ID kendang yang sedang dipilih pada mode [BoardMode.EDIT_LAYOUT]. */
    var selectedDrumId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** ID zona yang sedang dipilih pada mode [BoardMode.SELECT_ZONE]. */
    var selectedZoneId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Callback opsional saat suatu zona terkena ketukan pada mode bermain. */
    var onZoneHitListener: ((drum: DrumData, zone: ZoneData) -> Unit)? = null

    /** Callback saat kendang dipilih pada mode edit tata letak. */
    var onDrumSelectedListener: ((drum: DrumData) -> Unit)? = null

    /** Callback saat zona dipilih pada mode seleksi zona. */
    var onZoneSelectedListener: ((drum: DrumData, zone: ZoneData) -> Unit)? = null

    /** Callback saat posisi kendang digeser pada mode edit tata letak. */
    var onDrumMovedListener: ((drumId: String, newCenter: Point) -> Unit)? = null

    /** Overlay garis batas zona untuk kebutuhan kalibrasi (Task 7.5). */
    val zoneGuideOverlay: ZoneGuideOverlay = ZoneGuideOverlayProvider.create()

    // Pemetaan koordinat kanvas aspect-fit
    private var mapper = CanvasCoordinateMapper()

    // Daftar highlight visual aktif yang sedang memudar
    private val activeHighlights = mutableListOf<ActiveZoneHighlight>()

    /** Jumlah highlight visual yang sedang aktif (untuk keperluan verifikasi dan testing). */
    val activeHighlightsCount: Int get() = activeHighlights.size

    /**
     * Memperbarui dimensi mapper secara manual untuk pengujian unit test tanpa display pipeline.
     */
    fun setDimensionsForTest(w: Int, h: Int) {
        val aspect = kit?.canvasAspect ?: 2.0f
        mapper = CanvasCoordinateMapper(
            viewWidth = w.toFloat(),
            viewHeight = h.toFloat(),
            canvasAspect = aspect
        )
    }

    // Status drag pada mode EDIT_LAYOUT
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var isDragging: Boolean = false
    private var dragStartCenter: Point = Point(0f, 0f)
    private var dragTouchStart: Point = Point(0f, 0f)

    // Paints untuk rendering kendang
    private val boardBackgroundPaint = Paint().apply {
        color = Color.parseColor("#121212")
        style = Paint.Style.FILL
    }

    private val canvasAreaPaint = Paint().apply {
        color = Color.parseColor("#181614")
        style = Paint.Style.FILL
    }

    private val canvasBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E2925")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val drumBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D2516")
        style = Paint.Style.FILL
    }

    private val drumBodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C3A24")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val drumSkinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9C5A0")
        style = Paint.Style.FILL
    }

    private val drumCenterPatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2622")
        style = Paint.Style.FILL
    }

    private val drumRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D4C33")
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val drumInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C7E5E")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val guideArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B8D4")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val guideBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAB00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val zoneBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val zoneBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val zoneLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val drumTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        // Papan kendang harus menerima sentuhan
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMapper()
        updateSystemGestureExclusionRects(w, h)
    }

    private fun updateMapper() {
        val aspect = kit?.canvasAspect ?: 2.0f
        mapper = CanvasCoordinateMapper(
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            canvasAspect = aspect
        )
    }

    /**
     * Mendaftarkan System Gesture Exclusion Rects pada Android 10+ (API 29+)
     * di tepi kiri dan kanan layar agar rolling kendang tidak memicu gestur back sistem (Task 7.4).
     */
    private fun updateSystemGestureExclusionRects(w: Int, h: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && w > 0 && h > 0) {
            val edgeWidthPx = (48f * resources.displayMetrics.density).toInt().coerceAtLeast(40)
            val leftRect = Rect(0, 0, edgeWidthPx, h)
            val rightRect = Rect(w - edgeWidthPx, 0, w, h)
            systemGestureExclusionRects = listOf(leftRect, rightRect)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Gambar latar belakang gelap papan kendang
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boardBackgroundPaint)

        val currentKit = kit ?: return
        if (mapper.canvasWidth <= 0f || mapper.canvasHeight <= 0f) return

        // Area kanvas aktif (memudahkan orientasi visual di mode edit/kalibrasi)
        val canvasRect = RectF(
            mapper.canvasLeft,
            mapper.canvasTop,
            mapper.canvasRight,
            mapper.canvasBottom
        )
        canvas.drawRect(canvasRect, canvasAreaPaint)
        if (mode == BoardMode.EDIT_LAYOUT || showZoneGuides) {
            canvas.drawRect(canvasRect, canvasBorderPaint)
        }

        val now = SystemClock.uptimeMillis()

        // 2. Render badan kendang 3D terlebih dahulu (di lapisan bawah kulit kendang)
        for (drum in currentKit.drums) {
            val cx = mapper.toPixelX(drum.center.x)
            val cy = mapper.toPixelY(drum.center.y)
            val rx = mapper.toPixelRadiusX(drum.radius.rx)
            val ry = mapper.toPixelRadiusY(drum.radius.ry)
            if (rx <= 0f || ry <= 0f) continue
            drawDrumBody3D(canvas, cx, cy, rx, ry)
        }

        // 3. Render seluruh kulit kendang (sesuai urutan array drums)
        for (drum in currentKit.drums) {
            val cx = mapper.toPixelX(drum.center.x)
            val cy = mapper.toPixelY(drum.center.y)
            val baseRx = mapper.toPixelRadiusX(drum.radius.rx)
            val baseRy = mapper.toPixelRadiusY(drum.radius.ry)

            if (baseRx <= 0f || baseRy <= 0f) continue

            // Efek denyut kulit kendang saat baru dipukul (~100 ms)
            val pulseScale = computeDrumPulseScale(drum.id, now)
            val rx = baseRx * pulseScale
            val ry = baseRy * pulseScale

            val ovalRect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)

            // Isi kulit kendang warna krem/kulit alami
            canvas.drawOval(ovalRect, drumSkinPaint)

            // Aksen hitam tengah khas kendang pada Bass atau titik tengah tipis
            if (drum.model == "bass") {
                val divider = drum.divider
                if (divider != null) {
                    val ovalPath = Path().apply { addOval(ovalRect, Path.Direction.CW) }
                    val divCenterX = cx + (divider.dx * rx)
                    val divCenterY = cy + (divider.dy * ry)
                    val divRadius = divider.r * rx
                    canvas.save()
                    canvas.clipPath(ovalPath)
                    canvas.drawCircle(divCenterX, divCenterY, divRadius, drumCenterPatchPaint)
                    canvas.restore()
                }
            }

            // Cincin dalam untuk aksen drumhead
            val innerRect = RectF(cx - rx * 0.92f, cy - ry * 0.92f, cx + rx * 0.92f, cy + ry * 0.92f)
            canvas.drawOval(innerRect, drumInnerRingPaint)

            // Rim / bingkai kayu tepi kendang
            canvas.drawOval(ovalRect, drumRimPaint)

            // Highlight seleksi jika kendang ini sedang dipilih pada mode EDIT_LAYOUT
            if (mode == BoardMode.EDIT_LAYOUT && drum.id == selectedDrumId) {
                canvas.drawOval(ovalRect, selectionPaint)
            }
        }

        // 4. Render garis panduan batas zona, highlight zona terpilih, dan label zona
        if (showZoneGuides || mode == BoardMode.EDIT_LAYOUT || mode == BoardMode.SELECT_ZONE) {
            drawInteractiveZoneGuidesAndLabels(canvas, currentKit)
        } else {
            zoneGuideOverlay.draw(canvas, currentKit, mapper)
        }

        // 5. Render highlight visual presisi pada zona yang terkena ketukan (Task 7.3)
        renderActiveHighlights(canvas)
    }

    /**
     * Menggambar badan kendang 3D yang ditarik dari tepi kiri-kanan kulit ke arah bawah (PRD Bagian 8).
     * Badan ini murni hiasan visual dan tidak merespons sentuhan.
     */
    private fun drawDrumBody3D(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        val bodyDepth = min(ry * 0.48f, 64f)
        val bottomHalfWidth = rx * 0.84f
        val bottomY = cy + ry * 0.65f + bodyDepth

        val bodyPath = Path().apply {
            moveTo(cx - rx, cy)
            lineTo(cx - bottomHalfWidth, bottomY)
            quadTo(cx, bottomY + ry * 0.45f, cx + bottomHalfWidth, bottomY)
            lineTo(cx + rx, cy)
            close()
        }
        canvas.drawPath(bodyPath, drumBodyPaint)
        canvas.drawPath(bodyPath, drumBodyStrokePaint)
    }

    /**
     * Menghitung faktor skala denyut halus (~2.5%) saat kendang baru saja dipukul.
     */
    private fun computeDrumPulseScale(drumId: String, now: Long): Float {
        var maxPulse = 0f
        for (h in activeHighlights) {
            if (h.drum.id == drumId) {
                val elapsed = now - h.startTimeMs
                if (elapsed in 0 until h.durationMs) {
                    val progress = elapsed.toFloat() / h.durationMs.toFloat()
                    val weight = 1f - progress
                    if (weight > maxPulse) maxPulse = weight
                }
            }
        }
        return 1.0f + (maxPulse * 0.025f)
    }

    /**
     * Menggambar batas zona akustik, highlight zona yang sedang dipilih, serta lencana nama & status WAV per zona.
     */
    private fun drawInteractiveZoneGuidesAndLabels(canvas: Canvas, currentKit: KitData) {
        val density = resources.displayMetrics.density
        zoneLabelPaint.textSize = 10.5f * density
        drumTitlePaint.textSize = 11f * density

        for (drum in currentKit.drums) {
            val cx = mapper.toPixelX(drum.center.x)
            val cy = mapper.toPixelY(drum.center.y)
            val rx = mapper.toPixelRadiusX(drum.radius.rx)
            val ry = mapper.toPixelRadiusY(drum.radius.ry)
            if (rx <= 0f || ry <= 0f) continue

            // Jika ada zona dari kendang ini yang sedang dipilih di SELECT_ZONE, beri highlight kuning transparan
            val chosenZone = drum.zones.firstOrNull { it.id == selectedZoneId }
            if (chosenZone != null) {
                ZoneHighlightDrawer.drawHighlight(canvas, drum, chosenZone, 110, mapper)
            }

            val ovalRect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            val ovalPath = Path().apply { addOval(ovalRect, Path.Direction.CW) }

            when (drum.model) {
                "bass" -> {
                    val divider = drum.divider
                    if (divider != null) {
                        val divCenterX = cx + (divider.dx * rx)
                        val divCenterY = cy + (divider.dy * ry)
                        val divRadius = divider.r * rx

                        canvas.save()
                        canvas.clipPath(ovalPath)

                        // Garis pemisah 4 bilah bulan sabit terlebih dahulu (di luar C_div)
                        val circlePath = Path().apply {
                            addCircle(divCenterX, divCenterY, divRadius, Path.Direction.CW)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            canvas.save()
                            canvas.clipOutPath(circlePath)
                            val top = cy - ry
                            val bandHeight = (2f * ry) / 4f
                            for (i in 1..3) {
                                val y = top + i * bandHeight
                                canvas.drawLine(cx - rx, y, cx + rx, y, guideBandPaint)
                            }
                            canvas.restore()
                        } else {
                            val top = cy - ry
                            val bandHeight = (2f * ry) / 4f
                            for (i in 1..3) {
                                val y = top + i * bandHeight
                                canvas.drawLine(cx - rx, y, cx + rx, y, guideBandPaint)
                            }
                        }

                        // Garis busur C_div utama
                        canvas.drawCircle(divCenterX, divCenterY, divRadius, guideArcPaint)
                        canvas.restore()
                    }
                }

                "split" -> {
                    canvas.save()
                    canvas.clipPath(ovalPath)
                    canvas.drawLine(cx - rx, cy, cx + rx, cy, guideArcPaint)
                    canvas.restore()
                }
            }

            // Gambar label nama kendang di atas rim jika di mode EDIT_LAYOUT
            if (mode == BoardMode.EDIT_LAYOUT) {
                canvas.drawText(
                    drum.id.uppercase(),
                    cx,
                    (cy - ry - 8f * density).coerceAtLeast(16f * density),
                    drumTitlePaint
                )
            }

            // Gambar lencana (badge) untuk setiap zona di titik tengah geometrisnya
            if (showZoneGuides || mode == BoardMode.SELECT_ZONE) {
                for (zone in drum.zones) {
                    val pos = computeZoneLabelPosition(drum, zone, cx, cy, rx, ry)
                    drawZoneBadge(canvas, zone, pos.first, pos.second, density)
                }
            }
        }
    }

    private fun computeZoneLabelPosition(
        drum: DrumData,
        zone: ZoneData,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float
    ): Pair<Float, Float> {
        return when (drum.model) {
            "single" -> Pair(cx, cy)
            "split" -> {
                if (zone.region == "upper") {
                    Pair(cx, cy - ry * 0.45f)
                } else {
                    Pair(cx, cy + ry * 0.45f)
                }
            }
            "bass" -> {
                val divider = drum.divider ?: return Pair(cx, cy)
                val divCx = cx + divider.dx * rx
                val divCy = cy + divider.dy * ry
                val divR = divider.r * rx

                if (zone.region == "main") {
                    // Titik tengah irisan oval Bass dan lingkaran C_div pada garis horizontal cy
                    val ovalLeft = cx - rx * 0.85f
                    val ovalRight = cx + rx * 0.85f
                    val dyDiv = cy - divCy
                    val halfChord = if (kotlin.math.abs(dyDiv) < divR) {
                        sqrt(max(0f, divR * divR - dyDiv * dyDiv))
                    } else {
                        divR * 0.5f
                    }
                    val left = max(ovalLeft, divCx - halfChord)
                    val right = min(ovalRight, divCx + halfChord)
                    val midX = if (right > left) (left + right) * 0.5f else cx - rx * 0.25f
                    Pair(midX, cy)
                } else {
                    val bandIndex = (zone.band ?: 0).coerceIn(0, 3)
                    val bandHeight = (2f * ry) / 4f
                    val bandCy = (cy - ry) + (bandIndex + 0.5f) * bandHeight
                    val dyNorm = ((bandCy - cy) / ry).coerceIn(-0.95f, 0.95f)
                    val ovalHalfW = rx * sqrt(max(0f, 1f - dyNorm * dyNorm))
                    val ovalLeft = cx - ovalHalfW
                    val ovalRight = cx + ovalHalfW

                    val dyDiv = bandCy - divCy
                    if (kotlin.math.abs(dyDiv) < divR) {
                        val divHalfW = sqrt(max(0f, divR * divR - dyDiv * dyDiv))
                        val divLeft = divCx - divHalfW
                        val divRight = divCx + divHalfW
                        val leftSegWidth = max(0f, min(ovalRight, divLeft) - ovalLeft)
                        val rightSegWidth = max(0f, ovalRight - max(ovalLeft, divRight))
                        if (rightSegWidth >= leftSegWidth && rightSegWidth > 4f) {
                            val segLeft = max(ovalLeft, divRight)
                            Pair((segLeft + ovalRight) * 0.5f, bandCy)
                        } else if (leftSegWidth > 4f) {
                            val segRight = min(ovalRight, divLeft)
                            Pair((ovalLeft + segRight) * 0.5f, bandCy)
                        } else {
                            Pair(cx + rx * 0.55f, bandCy)
                        }
                    } else {
                        Pair(cx + rx * 0.45f, bandCy)
                    }
                }
            }
            else -> Pair(cx, cy)
        }
    }

    private fun drawZoneBadge(
        canvas: Canvas,
        zone: ZoneData,
        x: Float,
        y: Float,
        density: Float
    ) {
        val hasSound = zone.sound != null
        val isSelected = zone.id == selectedZoneId
        val label = if (hasSound) "✓ ${zone.id}" else zone.id

        val textWidth = zoneLabelPaint.measureText(label)
        val padX = 6f * density
        val padY = 4f * density
        val halfW = textWidth / 2f + padX
        val halfH = 7f * density + padY
        val rect = RectF(x - halfW, y - halfH, x + halfW, y + halfH)

        zoneBadgeBgPaint.color = when {
            isSelected -> Color.parseColor("#E6FF8F00")
            hasSound -> Color.parseColor("#D91B5E20")
            else -> Color.parseColor("#CC26221E")
        }
        zoneBadgeStrokePaint.color = when {
            isSelected -> Color.parseColor("#FFFF00")
            hasSound -> Color.parseColor("#66BB6A")
            else -> Color.parseColor("#8D6E63")
        }

        canvas.drawRoundRect(rect, 6f * density, 6f * density, zoneBadgeBgPaint)
        canvas.drawRoundRect(rect, 6f * density, 6f * density, zoneBadgeStrokePaint)

        val textY = y - (zoneLabelPaint.descent() + zoneLabelPaint.ascent()) / 2f
        canvas.drawText(label, x, textY, zoneLabelPaint)
    }

    private fun renderActiveHighlights(canvas: Canvas) {
        if (activeHighlights.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        val iterator = activeHighlights.iterator()
        var hasActive = false

        while (iterator.hasNext()) {
            val highlight = iterator.next()
            val elapsed = now - highlight.startTimeMs

            if (elapsed >= highlight.durationMs) {
                iterator.remove()
            } else {
                hasActive = true
                val progress = elapsed.toFloat() / highlight.durationMs.toFloat()
                // Pudar dari alpha 140 (semi-transparan putih) hingga 0
                val alpha = ((1.0f - progress) * 140f).toInt().coerceIn(0, 255)
                ZoneHighlightDrawer.drawHighlight(canvas, highlight.drum, highlight.zone, alpha, mapper)
            }
        }

        if (hasActive) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentKit = kit ?: return super.onTouchEvent(event)

        return when (mode) {
            BoardMode.PLAY -> {
                val action = event.actionMasked
                when (action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val index = event.actionIndex
                        handleTouchAt(event.getX(index), event.getY(index), action)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // ACTION_MOVE tidak memicu suara baru saat jari bergeser
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        // Tidak ada aksi suara dan tidak crash
                    }
                }
                true
            }
            BoardMode.EDIT_LAYOUT -> handleEditLayoutTouchEvent(event, currentKit)
            BoardMode.SELECT_ZONE -> handleSelectZoneTouchEvent(event, currentKit)
        }
    }

    private fun handleSelectZoneTouchEvent(event: MotionEvent, currentKit: KitData): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y
            if (mapper.isInsideCanvas(touchX, touchY)) {
                val canvasPt = mapper.toCanvasCoordinates(touchX, touchY)
                if (canvasPt != null) {
                    val hit = HitTester.findHit(currentKit, canvasPt.x, canvasPt.y)
                    if (hit != null) {
                        selectedDrumId = hit.drum.id
                        selectedZoneId = hit.zone.id
                        onZoneTapped(hit.drum, hit.zone)
                        onZoneSelectedListener?.invoke(hit.drum, hit.zone)
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    /**
     * Memproses satu titik sentuhan pada mode bermain (Task 7.2).
     *
     * Aturan:
     * - Hanya ACTION_DOWN dan ACTION_POINTER_DOWN yang memicu suara dan highlight.
     * - ACTION_MOVE tidak memicu suara baru.
     * - Sentuhan di area margin diabaikan (mengembalikan false).
     * - Jika `sound != null`, kirim trigger ke AudioEngine; jika `sound == null`, lewati suara.
     * - Highlight visual tetap menyala meskipun `sound == null`.
     *
     * @return true jika titik sentuhan mengenai suatu zona di kanvas, false jika di luar atau diabaikan.
     */
    fun handleTouchAt(pixelX: Float, pixelY: Float, actionMasked: Int): Boolean {
        val currentKit = kit ?: return false
        if (mode != BoardMode.PLAY) return false

        if (actionMasked != MotionEvent.ACTION_DOWN && actionMasked != MotionEvent.ACTION_POINTER_DOWN) {
            return false
        }

        if (!mapper.isInsideCanvas(pixelX, pixelY)) {
            return false
        }

        val canvasPt = mapper.toCanvasCoordinates(pixelX, pixelY) ?: return false
        val hit = HitTester.findHit(currentKit, canvasPt.x, canvasPt.y) ?: return false

        onZoneTapped(hit.drum, hit.zone)
        return true
    }

    /**
     * Dipanggil saat suatu zona berhasil terdeteksi pada mode bermain.
     */
    private fun onZoneTapped(drum: DrumData, zone: ZoneData) {
        // 1. Picu visual feedback presisi per bentuk zona (selalu menyala, bahkan saat sound null)
        activeHighlights.add(
            ActiveZoneHighlight(
                drum = drum,
                zone = zone,
                startTimeMs = SystemClock.uptimeMillis(),
                durationMs = 100L
            )
        )
        postInvalidateOnAnimation()

        // 2. Picu audio engine jika zona memiliki file suara
        if (zone.sound != null) {
            val slotIndex = slotByZoneId[zone.id]
            if (slotIndex != null) {
                audioEngine.triggerSound(slotIndex, zone.gain)
            }
        }

        // 3. Panggil listener opsional
        onZoneHitListener?.invoke(drum, zone)
    }

    /**
     * Memproses sentuhan pada mode edit tata letak (Task 7.6).
     *
     * Aturan:
     * - Nol pemanggilan ke AudioEngine (hening total).
     * - Mengetuk kendang memilih kendang (mempertahankan pilihan jika ketuk di luar).
     * - Menggeser kendang dengan satu jari (jari tambahan diabaikan).
     * - Posisi dijepit real-time ke batas kanvas melalui [LayoutMerger.clampCenter].
     */
    private fun handleEditLayoutTouchEvent(event: MotionEvent, currentKit: KitData): Boolean {
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y

                if (mapper.isInsideCanvas(touchX, touchY)) {
                    val canvasPt = mapper.toCanvasCoordinates(touchX, touchY)
                    if (canvasPt != null) {
                        val hit = HitTester.findHit(currentKit, canvasPt.x, canvasPt.y)
                        if (hit != null) {
                            selectedDrumId = hit.drum.id
                            onDrumSelectedListener?.invoke(hit.drum)

                            // Mulai sesi drag dengan jari ini
                            activePointerId = event.getPointerId(0)
                            isDragging = true
                            dragStartCenter = hit.drum.center
                            dragTouchStart = canvasPt
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex >= 0) {
                        val currentX = event.getX(pointerIndex)
                        val currentY = event.getY(pointerIndex)
                        val currentCanvasPt = mapper.toCanvasCoordinates(currentX, currentY)

                        val currentSelectedId = selectedDrumId
                        val selectedDrum = currentKit.drums.firstOrNull { it.id == currentSelectedId }

                        if (currentCanvasPt != null && selectedDrum != null) {
                            val deltaX = currentCanvasPt.x - dragTouchStart.x
                            val deltaY = currentCanvasPt.y - dragTouchStart.y

                            val proposedCenter = Point(
                                x = dragStartCenter.x + deltaX,
                                y = dragStartCenter.y + deltaY
                            )

                            // Jepit posisi center agar oval tetap berada di dalam kanvas
                            val clampedCenter = LayoutMerger.clampCenter(
                                center = proposedCenter,
                                radius = selectedDrum.radius,
                                canvasAspect = currentKit.canvasAspect,
                                scale = 1.0f
                            )

                            onDrumMovedListener?.invoke(selectedDrum.id, clampedCenter)
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Sesuai requirement: jari tambahan selama penggeseran diabaikan
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }

        return true
    }
}
