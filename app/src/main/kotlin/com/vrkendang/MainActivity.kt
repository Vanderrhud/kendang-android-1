package com.vrkendang

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import com.vrkendang.audio.AudioEngine
import com.vrkendang.audio.AudioLifecycleManager
import com.vrkendang.audio.KitAudioLoader
import com.vrkendang.image.KitImageLoader
import com.vrkendang.image.KitImageSource
import com.vrkendang.repository.AssetManagerKitAssetSource
import com.vrkendang.repository.FileLayoutOverrideSource
import com.vrkendang.repository.KitRepository
import com.vrkendang.view.CalibrationOverlayView
import com.vrkendang.view.HeaderView
import com.vrkendang.view.KendangBoardView
import java.io.File

/**
 * Activity utama permainan VR Kendang.
 *
 * Sesuai Desain & Spesifikasi:
 * - ConstraintLayout: HeaderView di atas (48dp), KendangBoardView mengisi sisa layar (Task 9.1).
 * - Navigasi halaman penuh terpisah dari Menu Parent-Child ("☰ Menu"):
 *   - Child 1: "Pilih Kit" -> membuka [KitSelectActivity] (Task 9.2, 9.3)
 *   - Child 2: "Sesuaikan" -> membuka [CalibrationActivity] (Task 9.2, 10.1)
 *   - Kanan Header: "Mode Developer" (khusus build debug)
 * - KeepScreenOn, Landscape, Immersive Sticky, Gesture Exclusion Rects (Task 7.4).
 * - Siklus hidup audio: pauseStream pada onStop, resumeStream pada onStart, closeStream pada onDestroy (Task 5.8).
 */
class MainActivity : Activity() {

    private companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var headerView: HeaderView
    private lateinit var boardView: KendangBoardView
    private lateinit var calibrationOverlay: CalibrationOverlayView
    private lateinit var kitRepository: KitRepository
    private val kitAudioLoader = KitAudioLoader()
    private val kitImageLoader = KitImageLoader()

    private var activeKitId: String = KitRepository.DEFAULT_KIT_ID
    private var currentDrumBitmaps: Map<String, Bitmap> = emptyMap()
    private var currentDrumBodyBitmaps: Map<String, Bitmap> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 0. Pastikan manajemen siklus hidup audio terdaftar ke Application (Task 5.8)
            AudioLifecycleManager.init(application)

            // 1. Kunci layar agar selalu menyala selama aplikasi di foreground (Task 7.4)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // 2. Kunci orientasi landscape secara aman (Task 7.4)
            runCatching {
                @Suppress("SourceLockedOrientationActivity")
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }.onFailure { Log.w(TAG, "Gagal mengunci orientasi secara programatik", it) }

            // 3. Muat layout utama ConstraintLayout terlebih dahulu (Task 9.1)
            setContentView(R.layout.activity_main)

            // 4. Terapkan mode Immersive Sticky setelah content view siap (Task 7.4)
            applyImmersiveStickyMode()

            // 5. Inisialisasi repositori kit (mencakup kit bawaan APK dan kit tersimpan internal filesDir/kits/)
            val assetSource = AssetManagerKitAssetSource(assets)
            val layoutsDir = File(filesDir, "layouts")
            val internalKitsDir = File(filesDir, "kits").apply { mkdirs() }
            val overrideSource = FileLayoutOverrideSource(layoutsDir)
            kitRepository = KitRepository(
                assetSource = assetSource,
                layoutOverrideSource = overrideSource,
                internalKitsDir = internalKitsDir
            )

            headerView = findViewById(R.id.header_view)
            boardView = findViewById(R.id.board_view)
            calibrationOverlay = findViewById(R.id.calibration_overlay)

            headerView.boardView = boardView
            calibrationOverlay.boardView = boardView
            calibrationOverlay.headerView = headerView

            // 6. Hubungkan opsi Child 1 ("Pilih Kit") ke Halaman Pilih Kit Layar Penuh (Task 9.2, 9.3)
            headerView.onKitSelectorClicked = {
                openKitSelectPage()
            }

            // 7. Hubungkan opsi Child 2 ("Sesuaikan") ke Halaman Sesuaikan Layar Penuh (Task 9.2, 10.1)
            headerView.onSettingsClicked = {
                openCalibrationMode()
            }

            // 8. Muat dan aktifkan kit aktif terakhir (atau default new-metro)
            val prefs = getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
            activeKitId = prefs.getString(KitSelectActivity.KEY_ACTIVE_KIT_ID, KitRepository.DEFAULT_KIT_ID)
                ?: KitRepository.DEFAULT_KIT_ID
            loadActiveKit(activeKitId)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal melakukan inisialisasi MainActivity", t)
            showDiagnosticErrorScreen(t)
        }
    }

    /**
     * Menampilkan layar diagnostik stacktrace apabila terjadi error tak terduga saat inisialisasi.
     */
    private fun showDiagnosticErrorScreen(error: Throwable) {
        runCatching {
            val errorText = TextView(this).apply {
                text = "Gagal memuat VR Kendang:\n\n${error.stackTraceToString()}"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(32, 32, 32, 32)
                setTextIsSelectable(true)
            }
            val scrollView = ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#181614"))
                addView(errorText)
            }
            setContentView(scrollView)
        }
    }

    /**
     * Membuka Halaman Pilih Kit Layar Penuh ([KitSelectActivity]) (Task 9.3).
     */
    fun openKitSelectPage() {
        val intent = Intent(this, KitSelectActivity::class.java).apply {
            putExtra(KitSelectActivity.EXTRA_ACTIVE_KIT_ID, activeKitId)
        }
        startActivity(intent)
    }

    /**
     * Membuka Halaman Sesuaikan Layar Penuh ([CalibrationActivity]) (Task 10.1).
     */
    fun openCalibrationMode() {
        val activeKit = boardView.kit
        val intent = Intent(this, CalibrationActivity::class.java).apply {
            putExtra(CalibrationActivity.EXTRA_KIT_ID, activeKit?.kitId ?: activeKitId)
        }
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::headerView.isInitialized && headerView.childMenuContainer.visibility == View.VISIBLE) {
            headerView.setChildMenuExpanded(false)
            return
        }
        if (::calibrationOverlay.isInitialized && calibrationOverlay.handleBackPressed()) {
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()
        // Resume audio stream saat kembali ke foreground (Task 5.8).
        runCatching { AudioEngine.resumeStream() }
            .onFailure { Log.e(TAG, "Gagal resume stream audio", it) }

        // Segarkan kit aktif saat kembali dari KitSelectActivity, CalibrationActivity, atau Mode Developer
        runCatching {
            if (::kitRepository.isInitialized) {
                val prefs = getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
                val targetKitId = prefs.getString(KitSelectActivity.KEY_ACTIVE_KIT_ID, activeKitId) ?: activeKitId
                kitRepository.loadKits()
                loadActiveKit(targetKitId)
            }
        }.onFailure { Log.w(TAG, "Gagal menyegarkan kit pada onStart", it) }
    }

    override fun onStop() {
        super.onStop()
        // Penjeda audio stream ditangani secara terpusat oleh AudioLifecycleManager
        // agar tidak memutus audio saat bernavigasi ke Mode Developer atau Halaman Pilih Kit.
    }

    override fun onDestroy() {
        super.onDestroy()
        kitImageLoader.releaseBitmaps(currentDrumBitmaps)
        kitImageLoader.releaseBitmaps(currentDrumBodyBitmaps)
        currentDrumBitmaps = emptyMap()
        currentDrumBodyBitmaps = emptyMap()
        runCatching { AudioEngine.closeStream() }
            .onFailure { Log.e(TAG, "Gagal menutup stream audio", it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveStickyMode()
        }
    }

    /**
     * Mengonfigurasi Immersive Sticky Mode (Task 7.4).
     */
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
        }.onFailure {
            Log.w(TAG, "Gagal menerapkan Immersive Sticky Mode", it)
        }
    }

    /**
     * Memuat kit dari KitRepository (mencakup kit tersimpan di filesDir/kits/ maupun kit bawaan APK,
     * serta menerapkan layout override pemain bila ada).
     */
    private fun loadActiveKit(kitId: String) {
        val kit = try {
            kitRepository.selectKit(kitId)
        } catch (e: Exception) {
            return
        }
        activeKitId = kit.kitId
        getSharedPreferences(KitSelectActivity.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KitSelectActivity.KEY_ACTIVE_KIT_ID, activeKitId)
            .apply()

        val soundSource = { path: String ->
            kitRepository.openSoundStream(kit.kitId, path)
        }

        val activation = kitAudioLoader.activate(kit, soundSource)
        boardView.kit = kit
        boardView.slotByZoneId = activation.slotByZoneId

        // Muat gambar PNG kendang (kulit & body)
        kitImageLoader.releaseBitmaps(currentDrumBitmaps)
        kitImageLoader.releaseBitmaps(currentDrumBodyBitmaps)
        val imageSource = KitImageSource { path ->
            kitRepository.openImageStream(kit.kitId, path)
        }
        val loadedBitmaps = kitImageLoader.loadImages(kit, imageSource)
        val loadedBodyBitmaps = kitImageLoader.loadBodyImages(kit, imageSource)
        currentDrumBitmaps = loadedBitmaps
        currentDrumBodyBitmaps = loadedBodyBitmaps
        boardView.drumBitmaps = loadedBitmaps
        boardView.drumBodyBitmaps = loadedBodyBitmaps

        headerView.setActiveKitName(kit.name)
    }
}
