package com.vrkendang

import android.app.Activity
import android.content.Intent
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
import android.widget.ScrollView
import android.widget.TextView
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.repository.AssetManagerKitAssetSource
import com.vrkendang.repository.FileLayoutOverrideSource
import com.vrkendang.repository.KitRepository
import java.io.File

/**
 * Halaman Pilih Kit Layar Penuh untuk Pemain (Task 9.3).
 *
 * Sesuai Desain Penpot ("2. Halaman Pilih Kit (Full Layar)"):
 * - Halaman layar penuh landscape (tanpa popup dialog).
 * - Bilah atas: tombol "← Kembali" dan judul "Pilih Kit".
 * - Daftar kit hanya menampilkan Nama Kit di sisi kiri dan status "Aktif" (untuk 1 kit yang sedang aktif)
 *   atau tombol "Pakai" di sisi kanan.
 * - Tidak menampilkan kalimat teknis developer seperti "Kit Bawaan", "Kit Tersimpan", atau jumlah file suara.
 */
class KitSelectActivity : Activity() {

    companion object {
        const val EXTRA_ACTIVE_KIT_ID = "extra_active_kit_id"
        const val EXTRA_SELECTED_KIT_ID = "extra_selected_kit_id"
        const val PREFS_NAME = "vrkendang_prefs"
        const val KEY_ACTIVE_KIT_ID = "active_kit_id"
    }

    private lateinit var kitRepository: KitRepository
    private lateinit var listContainer: LinearLayout
    private var activeKitId: String = KitRepository.DEFAULT_KIT_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            @Suppress("SourceLockedOrientationActivity")
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        activeKitId = intent?.getStringExtra(EXTRA_ACTIVE_KIT_ID)
            ?: prefs.getString(KEY_ACTIVE_KIT_ID, KitRepository.DEFAULT_KIT_ID)
            ?: KitRepository.DEFAULT_KIT_ID

        val layoutsDir = File(filesDir, "layouts")
        val internalKitsDir = File(filesDir, "kits")
        kitRepository = KitRepository(
            assetSource = AssetManagerKitAssetSource(assets),
            layoutOverrideSource = FileLayoutOverrideSource(layoutsDir),
            internalKitsDir = internalKitsDir
        )

        val kits = kitRepository.loadKits()
        if (kits.none { it.kitId == activeKitId } && kits.isNotEmpty()) {
            activeKitId = kits.first().kitId
        }

        setContentView(buildContentView(kits))
        applyImmersiveStickyMode()
    }

    private fun buildContentView(kits: List<KitData>): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141210"))
        }

        // 1. Bilah Navigasi Atas ("← Kembali" + "Pilih Kit")
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1715"))
            setPadding((14 * density).toInt(), (6 * density).toInt(), (14 * density).toInt(), (6 * density).toInt())
        }

        val backBtn = Button(this).apply {
            text = "← Kembali"
            textSize = 12f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2622"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#443D36"))
            }
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            setOnClickListener {
                finish()
            }
        }
        topBar.addView(
            backBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (32 * density).toInt()
            ).apply {
                marginEnd = (16 * density).toInt()
            }
        )

        val titleText = TextView(this).apply {
            text = "Pilih Kit"
            textSize = 15f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topBar.addView(titleText)

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46 * density).toInt())
        )

        // 2. Daftar Kit di Tengah Layar
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#141210"))
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (18 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }

        populateKitRows(kits)

        scroll.addView(listContainer)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        )

        return root
    }

    private fun populateKitRows(kits: List<KitData>) {
        listContainer.removeAllViews()
        val density = resources.displayMetrics.density

        for (kit in kits) {
            val isActive = (kit.kitId == activeKitId)

            val rowCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1F1C19"))
                    cornerRadius = 12f * density
                    setStroke(
                        ((if (isActive) 2f else 1f) * density).toInt().coerceAtLeast(1),
                        Color.parseColor(if (isActive) "#4CAF50" else "#38322C")
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }

            // Sisi Kiri: HANYA Nama Kit
            val nameText = TextView(this).apply {
                text = kit.name
                textSize = 15f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            rowCard.addView(nameText)

            // Sisi Kanan: Badge "Aktif" atau Tombol "Pakai"
            if (isActive) {
                val activeBadge = TextView(this).apply {
                    text = "Aktif"
                    textSize = 12f
                    setTextColor(Color.parseColor("#81C784"))
                    gravity = Gravity.CENTER
                    paint.isFakeBoldText = true
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1B3B2B"))
                        cornerRadius = 8f * density
                        setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#4CAF50"))
                    }
                    setPadding((18 * density).toInt(), (6 * density).toInt(), (18 * density).toInt(), (6 * density).toInt())
                }
                rowCard.addView(activeBadge)
            } else {
                val pakaiBtn = Button(this).apply {
                    text = "Pakai"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    isAllCaps = false
                    paint.isFakeBoldText = true
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E65100"))
                        cornerRadius = 8f * density
                        setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#FFB74D"))
                    }
                    setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
                    setOnClickListener {
                        selectAndActivateKit(kit.kitId)
                    }
                }
                rowCard.addView(
                    pakaiBtn,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (34 * density).toInt()
                    )
                )
            }

            listContainer.addView(rowCard)
        }
    }

    private fun selectAndActivateKit(kitId: String) {
        activeKitId = kitId
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_KIT_ID, kitId)
            .apply()

        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_KIT_ID, kitId)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveStickyMode()
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
