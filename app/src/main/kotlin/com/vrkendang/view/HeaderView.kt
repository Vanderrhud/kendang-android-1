package com.vrkendang.view

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Header tampilan atas untuk pemain (Tahap 9).
 *
 * Sesuai Desain D6 & Spesifikasi Kendang Board:
 * - Tinggi tetap 48dp, pinned di bagian atas layar.
 * - Sisi kiri (start): tombol Kit Selector menampilkan nama kit aktif dan membuka dialog daftar kit (Task 9.2).
 * - Sisi tengah (center): kontainer slot iklan yang tersembunyi (GONE) pada MVP (Task 9.1).
 * - Sisi kanan (end): tombol navigasi langsung ke halaman khusus:
 *   - "📐 Tata Letak" -> membuka halaman penuh CalibrationActivity
 *   - "🛠 Mode Developer" (khusus build debug) -> membuka halaman penuh DevAuthoringActivity
 */
class HeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Tombol pemilih kit di sisi kiri. */
    val kitSelectorButton: Button

    /** Slot iklan di tengah (tersembunyi/GONE pada MVP). */
    val adSlotContainer: FrameLayout

    /** Tombol pengaturan / tata letak di sisi kanan. */
    val settingsButton: Button

    /** Callback ketika tombol pemilih kit ditekan. */
    var onKitSelectorClicked: (() -> Unit)? = null

    /** Callback ketika tombol pengaturan ditekan (opsional). */
    var onSettingsClicked: (() -> Unit)? = null

    /** Referensi ke board view untuk tindakan pengaturan seperti edit tata letak. */
    var boardView: KendangBoardView? = null

    init {
        setBackgroundColor(Color.parseColor("#181614"))

        val density = resources.displayMetrics.density
        val padHorizontal = (12 * density).toInt()

        // 1. Tombol Kit Selector (Kiri / Start)
        kitSelectorButton = Button(context).apply {
            text = "🥁 New Metro ▼"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2622"))
                cornerRadius = 10f * density
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#5D4037"))
            }
            isAllCaps = false
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            setOnClickListener {
                onKitSelectorClicked?.invoke()
            }
        }
        val selectorParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            (36 * density).toInt()
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            marginStart = padHorizontal
        }
        addView(kitSelectorButton, selectorParams)

        // 2. Slot Iklan Tengah (Tengah / Center, GONE di MVP)
        adSlotContainer = FrameLayout(context).apply {
            visibility = View.GONE
        }
        val adParams = LayoutParams(
            (200 * density).toInt(),
            (36 * density).toInt()
        ).apply {
            gravity = Gravity.CENTER
        }
        addView(adSlotContainer, adParams)

        // 3. Baris Tombol Navigasi Halaman di Kanan (End)
        val rightActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val menuItems = SettingsMenuProviderImpl.getMenuItems()
        val devItem = menuItems.firstOrNull { it.id == "dev_mode" }
        val editLayoutItem = menuItems.firstOrNull { it.id == "edit_layout" }

        // Jika build debug menyediakan Mode Developer, tampilkan tombol langsung ke Halaman Mode Developer
        if (devItem != null) {
            val devButton = Button(context).apply {
                text = "🛠 ${devItem.title}"
                textSize = 11.5f
                setTextColor(Color.WHITE)
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E65100"))
                    cornerRadius = 10f * density
                }
                setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                setOnClickListener {
                    val activity = context as? Activity ?: return@setOnClickListener
                    val currentBoardView = boardView ?: return@setOnClickListener
                    SettingsMenuProviderImpl.onMenuItemSelected(devItem, activity, currentBoardView)
                }
            }
            rightActionsRow.addView(
                devButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * density).toInt()
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
            )
        }

        // Tombol Edit Tata Letak / Pengaturan
        settingsButton = Button(context).apply {
            text = "📐 Edit Tata Letak"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2622"))
                cornerRadius = 10f * density
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#443D36"))
            }
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setOnClickListener {
                if (onSettingsClicked != null) {
                    onSettingsClicked?.invoke()
                } else if (editLayoutItem != null) {
                    val activity = context as? Activity
                    val currentBoardView = boardView
                    if (activity != null && currentBoardView != null) {
                        SettingsMenuProviderImpl.onMenuItemSelected(editLayoutItem, activity, currentBoardView)
                    } else {
                        showSettingsMenu()
                    }
                } else {
                    showSettingsMenu()
                }
            }
        }
        rightActionsRow.addView(
            settingsButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * density).toInt()
            )
        )

        val rightParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            marginEnd = padHorizontal
        }
        addView(rightActionsRow, rightParams)
    }

    /**
     * Memperbarui nama kit aktif yang ditampilkan pada tombol pemilih kit (Task 9.2).
     */
    fun setActiveKitName(name: String) {
        kitSelectorButton.text = "🥁 $name ▼"
    }

    /**
     * Menampilkan dialog menu pengaturan (Task 9.3).
     *
     * Di build debug: menampilkan "Edit Tata Letak" dan "Mode Developer".
     * Di build release: hanya menampilkan "Edit Tata Letak".
     */
    fun showSettingsMenu() {
        val activity = context as? Activity ?: return
        val currentBoardView = boardView ?: return

        val menuItems = SettingsMenuProviderImpl.getMenuItems()
        val titles = menuItems.map { it.title }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Pengaturan")
            .setItems(titles) { _, which ->
                val selectedItem = menuItems[which]
                SettingsMenuProviderImpl.onMenuItemSelected(selectedItem, activity, currentBoardView)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
