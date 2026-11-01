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
 * Header tampilan atas untuk layar utama bermain (Tahap 9).
 *
 * Sesuai Desain D6 & Spesifikasi Kendang Board (desain Penpot):
 * - Tinggi tetap 44–48dp, pinned di bagian atas layar.
 * - Sisi kiri (start): Konsep Parent-Child Menu:
 *   - Tombol Parent `"☰ Menu"` (membuka/menutup opsi child di sebelahnya) + label `"Kit: <Nama>"`.
 *   - Opsi Child 1: `"Pilih Kit"` -> membuka Halaman Pilih Kit layar penuh.
 *   - Opsi Child 2: `"Sesuaikan"` -> membuka Halaman Sesuaikan layar penuh.
 * - Sisi tengah (center): kontainer slot iklan yang tersembunyi (GONE) pada MVP (Task 9.1).
 * - Sisi kanan (end): tombol `"Mode Developer"` khusus pada build debug.
 */
class HeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Tombol Parent "☰ Menu" di pojok kiri atas. */
    val menuButton: Button

    /** Kontainer opsi Child ("Pilih Kit" dan "Sesuaikan") yang muncul saat "☰ Menu" diketuk. */
    val childMenuContainer: LinearLayout

    /** Tombol Child "Pilih Kit". */
    val pilihKitButton: Button

    /** Tombol Child "Sesuaikan" (juga diekspos sebagai [settingsButton] untuk kompatibilitas). */
    val settingsButton: Button

    /** Label/tombol indikator kit aktif ("Kit: <Nama>"). */
    val kitSelectorButton: Button

    /** Slot iklan di tengah (tersembunyi/GONE pada MVP). */
    val adSlotContainer: FrameLayout

    /** Callback ketika opsi "Pilih Kit" atau indikator kit ditekan. */
    var onKitSelectorClicked: (() -> Unit)? = null

    /** Callback ketika opsi "Sesuaikan" ditekan. */
    var onSettingsClicked: (() -> Unit)? = null

    /** Referensi ke board view untuk tindakan navigasi. */
    var boardView: KendangBoardView? = null

    init {
        setBackgroundColor(Color.parseColor("#1A1715"))

        val density = resources.displayMetrics.density
        val padHorizontal = (12 * density).toInt()
        val btnHeight = (32 * density).toInt()

        val menuItems = SettingsMenuProviderImpl.getMenuItems()
        val devItem = menuItems.firstOrNull { it.id == "dev_mode" }
        val editLayoutItem = menuItems.firstOrNull { it.id == "edit_layout" }

        // 1. Kelompok Kiri (Parent "☰ Menu" -> Child ["Pilih Kit", "Sesuaikan"] + Label Kit Aktif)
        val leftRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        menuButton = Button(context).apply {
            text = "☰ Menu"
            textSize = 12f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#27221E"))
                cornerRadius = 8f * density
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#FFB300"))
            }
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setOnClickListener {
                toggleChildMenu()
            }
        }
        leftRow.addView(
            menuButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, btnHeight).apply {
                marginEnd = (8 * density).toInt()
            }
        )

        // Kontainer Child Menu (Pilih Kit & Sesuaikan)
        childMenuContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#211D1A"))
                cornerRadius = 8f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#3E352F"))
            }
            setPadding((4 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
        }

        pilihKitButton = Button(context).apply {
            text = "Pilih Kit"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E2823"))
                cornerRadius = 6f * density
            }
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                setChildMenuExpanded(false)
                onKitSelectorClicked?.invoke()
            }
        }
        childMenuContainer.addView(
            pilihKitButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (28 * density).toInt()).apply {
                marginEnd = (6 * density).toInt()
            }
        )

        settingsButton = Button(context).apply {
            text = "Sesuaikan"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            isAllCaps = false
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E2823"))
                cornerRadius = 6f * density
            }
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                setChildMenuExpanded(false)
                if (onSettingsClicked != null) {
                    onSettingsClicked?.invoke()
                } else if (editLayoutItem != null) {
                    val activity = context as? Activity
                    val currentBoardView = boardView
                    if (activity != null && currentBoardView != null) {
                        SettingsMenuProviderImpl.onMenuItemSelected(editLayoutItem, activity, currentBoardView)
                    }
                }
            }
        }
        childMenuContainer.addView(
            settingsButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (28 * density).toInt())
        )

        leftRow.addView(
            childMenuContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            }
        )

        // Indikator Kit Aktif ("Kit: <Nama>")
        kitSelectorButton = Button(context).apply {
            text = "Kit: New Metro"
            textSize = 11.5f
            setTextColor(Color.parseColor("#BDBDBD"))
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 6f * density
            }
            setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            setOnClickListener {
                setChildMenuExpanded(false)
                onKitSelectorClicked?.invoke()
            }
        }
        leftRow.addView(
            kitSelectorButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, btnHeight)
        )

        val leftParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            marginStart = padHorizontal
        }
        addView(leftRow, leftParams)

        // 2. Slot Iklan Tengah (Tengah / Center, GONE di MVP)
        adSlotContainer = FrameLayout(context).apply {
            visibility = View.GONE
        }
        val adParams = LayoutParams(
            (200 * density).toInt(),
            btnHeight
        ).apply {
            gravity = Gravity.CENTER
        }
        addView(adSlotContainer, adParams)

        // 3. Sisi Kanan (End): Tombol "Mode Developer" khusus build debug
        if (devItem != null) {
            val devButton = Button(context).apply {
                text = "Mode Developer"
                textSize = 11.5f
                setTextColor(Color.WHITE)
                isAllCaps = false
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E65100"))
                    cornerRadius = 8f * density
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#FFB74D"))
                }
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setOnClickListener {
                    setChildMenuExpanded(false)
                    val activity = context as? Activity ?: return@setOnClickListener
                    val currentBoardView = boardView ?: return@setOnClickListener
                    SettingsMenuProviderImpl.onMenuItemSelected(devItem, activity, currentBoardView)
                }
            }
            val rightParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                btnHeight
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = padHorizontal
            }
            addView(devButton, rightParams)
        }
    }

    /**
     * Membuka atau menutup tampilan opsi child ("Pilih Kit" dan "Sesuaikan").
     */
    fun setChildMenuExpanded(expanded: Boolean) {
        childMenuContainer.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun toggleChildMenu() {
        val isExpanded = childMenuContainer.visibility == View.VISIBLE
        setChildMenuExpanded(!isExpanded)
    }

    /**
     * Memperbarui nama kit aktif yang ditampilkan pada header (Task 9.2).
     */
    fun setActiveKitName(name: String) {
        kitSelectorButton.text = "Kit: $name"
    }

    /**
     * Menampilkan dialog menu pengaturan sebagai fallback bila diperlukan.
     */
    fun showSettingsMenu() {
        val activity = context as? Activity ?: return
        val currentBoardView = boardView ?: return

        val menuItems = SettingsMenuProviderImpl.getMenuItems()
        val titles = menuItems.map { it.title }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Menu")
            .setItems(titles) { _, which ->
                val selectedItem = menuItems[which]
                SettingsMenuProviderImpl.onMenuItemSelected(selectedItem, activity, currentBoardView)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
