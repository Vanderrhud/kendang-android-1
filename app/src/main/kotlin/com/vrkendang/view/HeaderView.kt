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
import android.widget.TextView

/**
 * Header tampilan atas untuk pemain (Tahap 9).
 *
 * Sesuai Desain D6 & Spesifikasi Kendang Board:
 * - Tinggi tetap 48dp, pinned di bagian atas layar.
 * - Sisi kiri (start): tombol Kit Selector menampilkan nama kit aktif dan membuka dialog daftar kit (Task 9.2).
 * - Sisi tengah (center): kontainer slot iklan yang tersembunyi (GONE) pada MVP (Task 9.1).
 * - Sisi kanan (end): tombol Pengaturan (⚙) membuka menu:
 *   - "Edit Tata Letak" (semua build)
 *   - "Mode Developer" (khusus build debug) (Task 9.3).
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

    /** Tombol pengaturan di sisi kanan. */
    val settingsButton: Button

    /** Callback ketika tombol pemilih kit ditekan. */
    var onKitSelectorClicked: (() -> Unit)? = null

    /** Callback ketika tombol pengaturan ditekan (opsional, default membuka dialog menu pengaturan). */
    var onSettingsClicked: (() -> Unit)? = null

    /** Referensi ke board view untuk tindakan pengaturan seperti edit tata letak. */
    var boardView: KendangBoardView? = null

    private val buttonBg = GradientDrawable().apply {
        setColor(Color.parseColor("#2A2622"))
        cornerRadius = 12f
        setStroke(2, Color.parseColor("#443D36"))
    }

    init {
        // Latar belakang header gelap elegan dengan pembatas bawah
        setBackgroundColor(Color.parseColor("#181614"))

        val density = resources.displayMetrics.density
        val padHorizontal = (12 * density).toInt()
        val padVertical = (6 * density).toInt()

        // 1. Tombol Kit Selector (Kiri / Start)
        kitSelectorButton = Button(context).apply {
            text = "🥁 New Metro ▼"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = buttonBg.constantState?.newDrawable() ?: buttonBg
            isAllCaps = false
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
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
            (240 * density).toInt(),
            (36 * density).toInt()
        ).apply {
            gravity = Gravity.CENTER
        }
        addView(adSlotContainer, adParams)

        // 3. Tombol Settings ⚙ (Kanan / End)
        settingsButton = Button(context).apply {
            text = "⚙"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = buttonBg.constantState?.newDrawable() ?: buttonBg
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setOnClickListener {
                if (onSettingsClicked != null) {
                    onSettingsClicked?.invoke()
                } else {
                    showSettingsMenu()
                }
            }
        }
        val settingsParams = LayoutParams(
            (44 * density).toInt(),
            (36 * density).toInt()
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            marginEnd = padHorizontal
        }
        addView(settingsButton, settingsParams)
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
