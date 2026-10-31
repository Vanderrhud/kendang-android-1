package com.vrkendang

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
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
import com.vrkendang.audio.KitAudioLoader
import com.vrkendang.kitcore.parser.KitJsonParser
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
 * - Navigasi halaman penuh terpisah:
 *   - Halaman Edit Tata Letak ([CalibrationActivity])
 *   - Halaman Mode Developer (khusus build debug)
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

    private var activeKitId: String = KitRepository.DEFAULT_KIT_ID
    private var usingDevWorkingKit: Boolean = false
    private var lastKnownDevKitModified: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
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

            // 5. Inisialisasi repositori kit
            val assetSource = AssetManagerKitAssetSource(assets)
            val layoutsDir = File(filesDir, "layouts")
            val overrideSource = FileLayoutOverrideSource(layoutsDir)
            kitRepository = KitRepository(assetSource, overrideSource)

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

            val devKitJson = File(filesDir, "dev-kit/kit.json")
            lastKnownDevKitModified = if (devKitJson.isFile) devKitJson.lastModified() else 0L

            // 7. Muat dan aktifkan kit awal bawaan
            loadBuiltInKit(KitRepository.DEFAULT_KIT_ID)
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
     * Membuka Halaman Penuh Kalibrasi Tata Letak ([CalibrationActivity]) (Task 10.1).
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

        // Segarkan kit aktif saat kembali dari halaman CalibrationActivity atau Mode Developer
        runCatching {
            val devKitJson = File(filesDir, "dev-kit/kit.json")
            val currentDevMod = if (devKitJson.isFile) devKitJson.lastModified() else 0L
            if (currentDevMod > lastKnownDevKitModified) {
                lastKnownDevKitModified = currentDevMod
                usingDevWorkingKit = true
            }
            if (usingDevWorkingKit && devKitJson.isFile) {
                loadDevWorkingKit()
            } else if (::kitRepository.isInitialized) {
                loadBuiltInKit(activeKitId)
            }
        }.onFailure { Log.w(TAG, "Gagal menyegarkan kit pada onStart", it) }
    }

    override fun onStop() {
        super.onStop()
        // Hentikan seluruh voice aktif saat masuk background agar tidak ada suara menggantung (Task 5.8)
        runCatching { AudioEngine.pauseStream() }
            .onFailure { Log.e(TAG, "Gagal pause stream audio", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
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
     * Memuat kit bawaan dari KitRepository (termasuk override tata letak pemain bila ada).
     */
    private fun loadBuiltInKit(kitId: String) {
        val kit = try {
            kitRepository.selectKit(kitId)
        } catch (e: Exception) {
            return
        }
        activeKitId = kit.kitId
        usingDevWorkingKit = false

        val soundSource = { path: String ->
            assets.open("${KitRepository.KITS_ASSET_ROOT}/${kit.kitId}/$path")
        }

        val activation = kitAudioLoader.activate(kit, soundSource)
        boardView.kit = kit
        boardView.slotByZoneId = activation.slotByZoneId
        headerView.setActiveKitName(kit.name)
    }

    /**
     * Memuat kit hasil racikan dari halaman Mode Developer (bila ada di filesDir/dev-kit/).
     */
    private fun loadDevWorkingKit() {
        val devKitDir = File(filesDir, "dev-kit")
        val kitJsonFile = File(devKitDir, "kit.json")
        if (!kitJsonFile.isFile) return

        val kit = KitJsonParser.parse(kitJsonFile.readText())
        usingDevWorkingKit = true

        val soundSource = { path: String ->
            File(devKitDir, path).inputStream()
        }
        val activation = kitAudioLoader.activate(kit, soundSource)
        boardView.kit = kit
        boardView.slotByZoneId = activation.slotByZoneId
        headerView.setActiveKitName("${kit.name} (Dev)")
    }

    /**
     * Menampilkan dialog pemilihan kit dari assets/kits/ serta salinan kerja Dev jika tersedia (Task 9.2).
     */
    private fun showKitSelectorDialog() {
        val builtInKits = kitRepository.getKits()
        val devKitFile = File(filesDir, "dev-kit/kit.json")
        val devKit = if (devKitFile.isFile) {
            runCatching { KitJsonParser.parse(devKitFile.readText()) }.getOrNull()
        } else null

        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        for (kit in builtInKits) {
            labels.add("🥁 ${kit.name}")
            actions.add { loadBuiltInKit(kit.kitId) }
        }
        if (devKit != null) {
            labels.add("🛠 ${devKit.name} (Kit Hasil Edit Developer)")
            actions.add { loadDevWorkingKit() }
        }

        if (labels.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Pilih Kit Kendang")
            .setItems(labels.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
