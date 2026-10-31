package com.vrkendang.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        EDIT_LAYOUT
    }

    /** Mode aktif saat ini (default: PLAY). */
    var mode: BoardMode = BoardMode.PLAY
        set(value) {
            field = value
            activeHighlights.clear()
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

    /** Callback opsional saat suatu zona terkena ketukan pada mode bermain. */
    var onZoneHitListener: ((drum: DrumData, zone: ZoneData) -> Unit)? = null

    /** Callback saat kendang dipilih pada mode edit tata letak. */
    var onDrumSelectedListener: ((drum: DrumData) -> Unit)? = null

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

    private val drumSkinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2622")
        style = Paint.Style.FILL
    }

    private val drumRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A6855")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val drumInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F1B18")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 8f
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

        // 2. Render seluruh kendang (sesuai urutan array drums)
        for (drum in currentKit.drums) {
            val cx = mapper.toPixelX(drum.center.x)
            val cy = mapper.toPixelY(drum.center.y)
            val rx = mapper.toPixelRadiusX(drum.radius.rx)
            val ry = mapper.toPixelRadiusY(drum.radius.ry)

            if (rx <= 0f || ry <= 0f) continue

            val ovalRect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)

            // Isi kulit kendang
            canvas.drawOval(ovalRect, drumSkinPaint)

            // Cincin dalam untuk aksen drumhead
            val innerRect = RectF(cx - rx * 0.92f, cy - ry * 0.92f, cx + rx * 0.92f, cy + ry * 0.92f)
            canvas.drawOval(innerRect, drumInnerRingPaint)

            // Rim / bingkai tepi kendang
            canvas.drawOval(ovalRect, drumRimPaint)

            // Highlight seleksi jika kendang ini sedang dipilih pada mode EDIT_LAYOUT
            if (mode == BoardMode.EDIT_LAYOUT && drum.id == selectedDrumId) {
                canvas.drawOval(ovalRect, selectionPaint)
            }
        }

        // 3. Render garis panduan batas zona jika aktif (Task 7.5)
        zoneGuideOverlay.draw(canvas, currentKit, mapper)

        // 4. Render highlight visual presisi pada zona yang terkena ketukan (Task 7.3)
        renderActiveHighlights(canvas)
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
        }
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
