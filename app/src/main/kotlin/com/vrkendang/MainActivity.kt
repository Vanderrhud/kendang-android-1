package com.vrkendang

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.vrkendang.audio.AudioEngine
import com.vrkendang.audio.KitAudioLoader
import com.vrkendang.dev.DevBridgeProvider
import com.vrkendang.repository.AssetManagerKitAssetSource
import com.vrkendang.repository.FileLayoutOverrideSource
import com.vrkendang.repository.KitRepository
import com.vrkendang.view.CalibrationOverlayView
import com.vrkendang.view.HeaderView
import com.vrkendang.view.KendangBoardView
import java.io.File

/**
 * Activity utama VR Kendang.
 *
 * Sesuai Desain & Spesifikasi:
 * - ConstraintLayout: HeaderView di atas (48dp), KendangBoardView mengisi sisa layar (Task 9.1).
 * - CalibrationOverlayView: Mode kalibrasi tata letak pemain dengan kontrol ringkas (Task 10.1–10.6).
 * - Kit Selector: menampilkan nama kit aktif dan membuka dialog daftar kit dari assets/kits/ (Task 9.2).
 * - Settings Menu: membuka opsi Edit Tata Letak (semua build) dan Mode Developer (khusus build debug) (Task 9.3).
 * - KeepScreenOn, Landscape, Immersive Sticky, Gesture Exclusion Rects (Task 7.4).
 * - Siklus hidup audio: pauseStream pada onStop, resumeStream pada onStart, closeStream pada onDestroy (Task 5.8).
 */
class MainActivity : Activity() {

    private lateinit var headerView: HeaderView
    private lateinit var boardView: KendangBoardView
    private lateinit var calibrationOverlay: CalibrationOverlayView
    private lateinit var kitRepository: KitRepository
    private val kitAudioLoader = KitAudioLoader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Kunci layar agar selalu menyala selama aplikasi di foreground (Task 7.4)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Kunci orientasi landscape (Task 7.4)
        @Suppress("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 3. Terapkan mode Immersive Sticky (Task 7.4)
        applyImmersiveStickyMode()

        // 4. Inisialisasi repositori kit
        val assetSource = AssetManagerKitAssetSource(assets)
        val layoutsDir = File(filesDir, "layouts")
        val overrideSource = FileLayoutOverrideSource(layoutsDir)
        kitRepository = KitRepository(assetSource, overrideSource)

        // 5. Muat layout utama ConstraintLayout (Task 9.1 & 10.1)
        setContentView(R.layout.activity_main)
        headerView = findViewById(R.id.header_view)
        boardView = findViewById(R.id.board_view)
        calibrationOverlay = findViewById(R.id.calibration_overlay)

        headerView.boardView = boardView
        calibrationOverlay.boardView = boardView
        calibrationOverlay.headerView = headerView

        // 6. Hubungkan tombol Kit Selector ke dialog daftar kit (Task 9.2)
        headerView.onKitSelectorClicked = {
            showKitSelectorDialog()
        }

        // 7. Muat dan aktifkan kit awal bawaan
        loadDefaultKit()

        // 8. Pasang fitur developer jika pada build debug (Task 8.1 & CI check 2.4)
        DevBridgeProvider.get().attach(this, boardView)
    }

    /**
     * Membuka Mode Kalibrasi Tata Letak (Task 10.1).
     */
    fun openCalibrationMode() {
        val activeKit = boardView.kit ?: return
        val baseKit = kitRepository.getBaseKit(activeKit.kitId) ?: activeKit
        calibrationOverlay.startCalibration(activeKit, baseKit)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Jika mode kalibrasi aktif, tangani konfirmasi keluar / simpan perubahan (Task 10.4)
        if (calibrationOverlay.handleBackPressed()) {
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()
        // Resume audio stream saat kembali ke foreground (Task 5.8)
        AudioEngine.resumeStream()
    }

    override fun onStop() {
        super.onStop()
        // Hentikan seluruh voice aktif saat masuk background agar tidak ada suara menggantung (Task 5.8)
        AudioEngine.pauseStream()
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioEngine.closeStream()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Pulihkan Immersive Sticky Mode saat window mendapatkan fokus kembali
            applyImmersiveStickyMode()
        }
    }

    /**
     * Mengonfigurasi Immersive Sticky Mode (Task 7.4).
     */
    private fun applyImmersiveStickyMode() {
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

    /**
     * Memuat kit bawaan pertama (new-metro) dan mengaktifkannya di mesin audio serta board view.
     */
    private fun loadDefaultKit() {
        val kit = try {
            kitRepository.selectKit(KitRepository.DEFAULT_KIT_ID)
        } catch (e: Exception) {
            return
        }
        val soundSource = { path: String ->
            assets.open("${KitRepository.KITS_ASSET_ROOT}/${kit.kitId}/$path")
        }

        val activation = kitAudioLoader.activate(kit, soundSource)
        boardView.kit = kit
        boardView.slotByZoneId = activation.slotByZoneId
        headerView.setActiveKitName(kit.name)
    }

    /**
     * Menampilkan dialog pemilihan kit dari assets/kits/ (Task 9.2).
     */
    private fun showKitSelectorDialog() {
        val kits = kitRepository.getKits()
        if (kits.isEmpty()) return

        val kitNames = kits.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih Kit Kendang")
            .setItems(kitNames) { _, which ->
                val chosen = kits[which]
                val activatedKit = kitRepository.selectKit(chosen.kitId)
                val soundSource = { path: String ->
                    assets.open("${KitRepository.KITS_ASSET_ROOT}/${activatedKit.kitId}/$path")
                }
                val activation = kitAudioLoader.activate(activatedKit, soundSource)
                boardView.kit = activatedKit
                boardView.slotByZoneId = activation.slotByZoneId
                headerView.setActiveKitName(activatedKit.name)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
