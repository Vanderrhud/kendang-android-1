package com.vrkendang.kitauthoring

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.vrkendang.audio.AudioEngine
import com.vrkendang.audio.KitAudioLoader
import com.vrkendang.dev.DevBridge
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.view.KendangBoardView
import java.io.File

/**
 * Antarmuka dan alat penyusun kit Mode Developer lengkap (Tahap 11).
 *
 * Sesuai Desain D12, Spesifikasi Kit Authoring & Tasks 11.1 s.d. 11.11:
 * - 11.1 Opsi Buat Kit Baru, Buka Kit (.vrkendang), Buka Kit Bawaan di filesDir/dev-kit/
 * - 11.2 Dua mode papan: UJI (bersuara) dan EDIT (seleksi/geser/skala, hening)
 * - 11.3 Kontrol edit zona: slider gain (0.0–2.0), status suara, pasang/kosongkan WAV, uji ketuk
 * - 11.4 Kontrol divider Bass interaktif (dx, dy -3..3, r 0..3) dengan visual real-time
 * - 11.5 Simpan atomik ke file sumber persisten (version +1)
 * - 11.6 Simpan Sebagai ke lokasi baru (version +1)
 * - 11.7 Buka paket .vrkendang via SAF & ekstraksi aman
 * - 11.8 Indikator perubahan belum disimpan & konfirmasi keluar/buka kit
 * - 11.9 Pemuatan sampel aman di background tanpa blokir callback audio
 * - 11.10 Batasi 4 kendang template (tanpa tambah/hapus kendang/zona)
 * - 11.11 Pengubahan urutan array drums (prioritas hit-test)
 */
class DevKitAuthoringBridge : DevBridge {

    private lateinit var session: DevKitAuthoringSession
    private val kitAudioLoader = KitAudioLoader()

    private var selectedDrumIndex = 0
    private var selectedZoneId = "bass-1"
    private var activeSlotMap = mutableMapOf<String, Int>()

    override fun attach(activity: Activity, boardView: KendangBoardView) {
        val devKitDir = File(activity.filesDir, "dev-kit")
        session = DevKitAuthoringSession(devKitDir)

        // Muat atau inisialisasi salinan kerja
        var kit = session.loadExistingWorkingCopy()
        if (kit == null) {
            try {
                kit = session.openBuiltInKit("new-metro", activity.assets)
            } catch (_: Exception) {}
        }

        if (kit != null) {
            applyKitToBoard(kit, boardView, devKitDir)
        }

        addFloatingDevButton(activity, boardView)
    }

    private fun addFloatingDevButton(activity: Activity, boardView: KendangBoardView) {
        val decorView = activity.window.decorView as? ViewGroup ?: return

        // Periksa apakah tombol sudah pernah dipasang agar tidak ganda
        if (decorView.findViewWithTag<View>("DEV_FLOATING_BUTTON") != null) return

        val button = Button(activity).apply {
            tag = "DEV_FLOATING_BUTTON"
            text = "⚙ DEV"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC1F1F1F"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#FFAA00"))
            }
            setPadding(24, 12, 24, 12)
            setOnClickListener {
                showFullDevAuthoringDialog(activity, boardView)
            }
        }

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 16
            rightMargin = 16
        }

        decorView.addView(button, layoutParams)
    }

    private fun applyKitToBoard(kit: KitData, boardView: KendangBoardView, devKitDir: File) {
        boardView.kit = kit

        var slotIndex = 0
        activeSlotMap.clear()
        for (drum in kit.drums) {
            for (zone in drum.zones) {
                activeSlotMap[zone.id] = slotIndex++
            }
        }
        boardView.slotByZoneId = activeSlotMap

        val soundSource = { soundPath: String ->
            File(devKitDir, soundPath).inputStream()
        }
        kitAudioLoader.activate(kit, soundSource)
    }

    /**
     * Menampilkan dialog Mode Developer Lengkap (Tahap 11).
     */
    fun showFullDevAuthoringDialog(activity: Activity, boardView: KendangBoardView) {
        val context = activity
        val kit = session.workingKit ?: return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#181614"))
        }

        // 1. Header & Indikator Perubahan (Task 11.8)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dirtyIndicator = if (session.hasUnsavedChanges) " ● [Belum Disimpan]" else ""
        val titleText = TextView(context).apply {
            text = "Mode Developer (Alat Penyusun Kit)$dirtyIndicator"
            textSize = 16f
            setTextColor(if (session.hasUnsavedChanges) Color.parseColor("#FFAA00") else Color.WHITE)
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerRow.addView(titleText)

        // Toggle Mode UJI vs EDIT (Task 11.2)
        val modeToggleButton = Button(context).apply {
            text = if (session.mode == DevKitAuthoringSession.BoardMode.UJI) "Mode: UJI (Bunyi)" else "Mode: EDIT (Hening)"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (session.mode == DevKitAuthoringSession.BoardMode.UJI) Color.parseColor("#1B5E20") else Color.parseColor("#E65100"))
                cornerRadius = 12f
            }
            setOnClickListener {
                if (session.mode == DevKitAuthoringSession.BoardMode.UJI) {
                    session.mode = DevKitAuthoringSession.BoardMode.EDIT
                    boardView.mode = KendangBoardView.BoardMode.EDIT_LAYOUT
                    text = "Mode: EDIT (Hening)"
                    (background as? GradientDrawable)?.setColor(Color.parseColor("#E65100"))
                    Toast.makeText(context, "Mode EDIT aktif: sentuhan memilih/menggeser kendang tanpa suara", Toast.LENGTH_SHORT).show()
                } else {
                    session.mode = DevKitAuthoringSession.BoardMode.UJI
                    boardView.mode = KendangBoardView.BoardMode.PLAY
                    text = "Mode: UJI (Bunyi)"
                    (background as? GradientDrawable)?.setColor(Color.parseColor("#1B5E20"))
                    Toast.makeText(context, "Mode UJI aktif: sentuhan membunyikan suara zona", Toast.LENGTH_SHORT).show()
                }
            }
        }
        headerRow.addView(modeToggleButton)
        container.addView(headerRow)

        // 2. Info Kit & Nama (Task 11.10)
        val kitInfoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val nameLabel = TextView(context).apply {
            text = "Nama: "
            setTextColor(Color.LTGRAY)
            textSize = 13f
        }
        kitInfoRow.addView(nameLabel)

        val nameEdit = EditText(context).apply {
            setText(kit.name)
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && text.isNotBlank() && text.toString() != session.workingKit?.name) {
                    val updated = session.updateKitName(text.toString())
                    boardView.kit = updated
                }
            }
        }
        kitInfoRow.addView(nameEdit)

        val versionBadge = TextView(context).apply {
            text = " ID: ${kit.kitId} (v${kit.version}) "
            textSize = 11f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 8f
            }
        }
        kitInfoRow.addView(versionBadge)
        container.addView(kitInfoRow)

        // 3. Tombol Alur Kit: Buat Baru, Buka Paket, Buka Bawaan (Task 11.1 & 11.7)
        val fileActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 16)
        }

        val btnNewKit = createSmallButton(context, "+ Buat Kit Baru", "#2E7D32") {
            promptCreateNewKit(activity, boardView)
        }
        fileActionsRow.addView(btnNewKit)

        val btnOpenPackage = createSmallButton(context, "📂 Buka .vrkendang", "#1565C0") {
            promptOpenPackage(activity, boardView)
        }
        fileActionsRow.addView(btnOpenPackage)

        val btnOpenBuiltIn = createSmallButton(context, "📦 Buka Kit Bawaan", "#424242") {
            promptOpenBuiltIn(activity, boardView)
        }
        fileActionsRow.addView(btnOpenBuiltIn)
        container.addView(fileActionsRow)

        // 4. Pengubah Urutan Kendang / Prioritas Hit-Test (Task 11.11)
        val orderHeader = TextView(context).apply {
            text = "Urutan Array Kendang (Prioritas Hit-Test, Task 11.11):"
            textSize = 13f
            setTextColor(Color.parseColor("#FFAA00"))
            paint.isFakeBoldText = true
        }
        container.addView(orderHeader)

        val drumOrderContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }
        populateDrumOrderList(context, drumOrderContainer, boardView)
        container.addView(drumOrderContainer)

        // 5. Parameter Divider Bass (Jika kendang Bass dipilih) (Task 11.4)
        val dividerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }
        populateDividerControls(context, dividerContainer, boardView)
        container.addView(dividerContainer)

        // 6. Kontrol Edit Zona Aktif & Pasang WAV (Task 11.3)
        val zoneEditHeader = TextView(context).apply {
            text = "Editor Zona Suara & Gain (Task 11.3):"
            textSize = 13f
            setTextColor(Color.parseColor("#FFAA00"))
            paint.isFakeBoldText = true
        }
        container.addView(zoneEditHeader)

        val zoneEditContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }
        populateZoneEditor(activity, zoneEditContainer, boardView)
        container.addView(zoneEditContainer)

        // 7. Tombol Simpan & Simpan Sebagai (Task 11.5 & 11.6)
        val saveActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val btnSave = createSmallButton(context, "💾 Simpan", "#2E7D32") {
            handleSaveAction(activity, boardView)
        }
        saveActionsRow.addView(btnSave)

        val btnSaveAs = createSmallButton(context, "💾 Simpan Sebagai...", "#00695C") {
            launchSaveAsPicker(activity, boardView)
        }
        saveActionsRow.addView(btnSaveAs)
        container.addView(saveActionsRow)

        val scrollView = ScrollView(context).apply {
            addView(container)
        }

        AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("Tutup Panel / Uji Papan") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun populateDrumOrderList(
        context: Context,
        container: LinearLayout,
        boardView: KendangBoardView
    ) {
        container.removeAllViews()
        val kit = session.workingKit ?: return

        for ((index, drum) in kit.drums.withIndex()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val label = TextView(context).apply {
                text = "${index + 1}. [${drum.id.uppercase()}] (${drum.model}) center: (${String.format("%.2f", drum.center.x)}, ${String.format("%.2f", drum.center.y)})"
                textSize = 12f
                setTextColor(if (index == selectedDrumIndex) Color.CYAN else Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                setOnClickListener {
                    selectedDrumIndex = index
                    boardView.selectedDrumId = drum.id
                    populateDrumOrderList(context, container, boardView)
                }
            }
            row.addView(label)

            // Tombol Naik
            val btnUp = Button(context).apply {
                text = "▲"
                textSize = 10f
                isEnabled = index > 0
                setOnClickListener {
                    val updated = session.moveDrumOrder(index, index - 1)
                    selectedDrumIndex = index - 1
                    boardView.kit = updated
                    populateDrumOrderList(context, container, boardView)
                }
            }
            row.addView(btnUp)

            // Tombol Turun
            val btnDown = Button(context).apply {
                text = "▼"
                textSize = 10f
                isEnabled = index < kit.drums.size - 1
                setOnClickListener {
                    val updated = session.moveDrumOrder(index, index + 1)
                    selectedDrumIndex = index + 1
                    boardView.kit = updated
                    populateDrumOrderList(context, container, boardView)
                }
            }
            row.addView(btnDown)

            container.addView(row)
        }
    }

    private fun populateDividerControls(
        context: Context,
        container: LinearLayout,
        boardView: KendangBoardView
    ) {
        container.removeAllViews()
        val kit = session.workingKit ?: return
        val bassDrum = kit.drums.firstOrNull { it.model == "bass" } ?: return
        val divider = bassDrum.divider ?: return

        val header = TextView(context).apply {
            text = "Parameter Busur Divider Bass (dx: ${divider.dx}, dy: ${divider.dy}, r: ${divider.r}):"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        container.addView(header)

        // Slider dx (-3.0 .. 3.0 dipetakan ke 0..600)
        val dxLabel = TextView(context).apply { text = "dx (-3.0 .. 3.0): ${divider.dx}"; textSize = 11f; setTextColor(Color.WHITE) }
        container.addView(dxLabel)
        val dxBar = SeekBar(context).apply {
            max = 600
            progress = ((divider.dx + 3.0f) * 100f).toInt().coerceIn(0, 600)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newDx = (progress / 100f) - 3.0f
                    try {
                        val updated = session.updateBassDivider(newDx, divider.dy, divider.r)
                        boardView.kit = updated
                        dxLabel.text = "dx (-3.0 .. 3.0): ${String.format("%.2f", newDx)}"
                    } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(dxBar)

        // Slider dy (-3.0 .. 3.0)
        val dyLabel = TextView(context).apply { text = "dy (-3.0 .. 3.0): ${divider.dy}"; textSize = 11f; setTextColor(Color.WHITE) }
        container.addView(dyLabel)
        val dyBar = SeekBar(context).apply {
            max = 600
            progress = ((divider.dy + 3.0f) * 100f).toInt().coerceIn(0, 600)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newDy = (progress / 100f) - 3.0f
                    try {
                        val updated = session.updateBassDivider(divider.dx, newDy, divider.r)
                        boardView.kit = updated
                        dyLabel.text = "dy (-3.0 .. 3.0): ${String.format("%.2f", newDy)}"
                    } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(dyBar)

        // Slider r (0.1 .. 3.0 dipetakan ke 10..300)
        val rLabel = TextView(context).apply { text = "r (0.1 .. 3.0): ${divider.r}"; textSize = 11f; setTextColor(Color.WHITE) }
        container.addView(rLabel)
        val rBar = SeekBar(context).apply {
            max = 300
            progress = (divider.r * 100f).toInt().coerceIn(10, 300)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newR = (progress / 100f).coerceAtLeast(0.1f)
                    try {
                        val updated = session.updateBassDivider(divider.dx, divider.dy, newR)
                        boardView.kit = updated
                        rLabel.text = "r (0.1 .. 3.0): ${String.format("%.2f", newR)}"
                    } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(rBar)
    }

    private fun populateZoneEditor(
        activity: Activity,
        container: LinearLayout,
        boardView: KendangBoardView
    ) {
        container.removeAllViews()
        val kit = session.workingKit ?: return
        val currentDrum = kit.drums.getOrNull(selectedDrumIndex) ?: kit.drums.first()

        val allZones = currentDrum.zones

        val zoneSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                allZones.map { "${it.id} (${it.region}${if (it.band != null) " band ${it.band}" else ""})" }
            )
            val currentIdx = allZones.indexOfFirst { it.id == selectedZoneId }.coerceAtLeast(0)
            setSelection(currentIdx)
        }
        container.addView(zoneSpinner)

        val activeZone = allZones.firstOrNull { it.id == selectedZoneId } ?: allZones.first()

        // Status Suara
        val soundStatusText = TextView(activity).apply {
            text = "File WAV: ${activeZone.sound ?: "Tanpa Suara (sound: null)"}"
            textSize = 12f
            setTextColor(if (activeZone.sound != null) Color.GREEN else Color.LTGRAY)
            setPadding(0, 8, 0, 8)
        }
        container.addView(soundStatusText)

        // Slider Gain (0.0 .. 2.0 dipetakan ke 0..200)
        val gainLabel = TextView(activity).apply {
            text = "Gain Volume: ${String.format("%.2f", activeZone.gain)}x"
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        container.addView(gainLabel)

        val gainBar = SeekBar(activity).apply {
            max = 200
            progress = (activeZone.gain * 100f).toInt().coerceIn(0, 200)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newGain = progress / 100f
                    val updated = session.updateZoneGain(activeZone.id, newGain)
                    boardView.kit = updated
                    gainLabel.text = "Gain Volume: ${String.format("%.2f", newGain)}x"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(gainBar)

        // Tombol Pasang WAV / Kosongkan / Uji
        val zoneButtonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val btnPickWav = createSmallButton(activity, "📂 Pasang WAV", "#2E7D32") {
            launchWavPickerForZone(activity, activeZone.id, boardView, soundStatusText)
        }
        zoneButtonsRow.addView(btnPickWav)

        val btnClear = createSmallButton(activity, "✕ Kosongkan", "#C62828") {
            val res = session.clearZoneSound(activeZone.id)
            if (res is DevSoundInstaller.Result.Success) {
                boardView.kit = res.updatedKit
                soundStatusText.text = "File WAV: Tanpa Suara (sound: null)"
                soundStatusText.setTextColor(Color.LTGRAY)
                val slot = activeSlotMap[activeZone.id] ?: 0
                AudioEngine.unloadSound(slot)
                Toast.makeText(activity, "Suara zona dikosongkan", Toast.LENGTH_SHORT).show()
            }
        }
        zoneButtonsRow.addView(btnClear)

        val btnTest = createSmallButton(activity, "▶ Uji", "#1565C0") {
            val slot = activeSlotMap[activeZone.id] ?: 0
            AudioEngine.triggerSound(slot, activeZone.gain)
        }
        zoneButtonsRow.addView(btnTest)

        container.addView(zoneButtonsRow)

        zoneSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = allZones[position]
                selectedZoneId = chosen.id
                populateZoneEditor(activity, container, boardView)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun launchWavPickerForZone(
        activity: Activity,
        zoneId: String,
        boardView: KendangBoardView,
        statusText: TextView
    ) {
        val fragmentManager = activity.fragmentManager
        var pickerFragment = fragmentManager.findFragmentByTag("DevPickerFragment") as? DevPickerFragment
        if (pickerFragment == null) {
            pickerFragment = DevPickerFragment()
            fragmentManager.beginTransaction().add(pickerFragment, "DevPickerFragment").commitAllowingStateLoss()
            fragmentManager.executePendingTransactions()
        }

        pickerFragment.onFilePicked = { uri ->
            if (uri != null) {
                val inputStream = activity.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val slot = activeSlotMap[zoneId] ?: 0
                    val res = session.installZoneWav(zoneId, inputStream, slot)
                    if (res is DevSoundInstaller.Result.Success) {
                        boardView.kit = res.updatedKit
                        statusText.text = "File WAV: sounds/$zoneId.wav (${res.sampleRate} Hz)"
                        statusText.setTextColor(Color.GREEN)
                        Toast.makeText(activity, "✓ WAV dipasang ke $zoneId", Toast.LENGTH_SHORT).show()
                    } else if (res is DevSoundInstaller.Result.Failure) {
                        AlertDialog.Builder(activity)
                            .setTitle("File Ditolak")
                            .setMessage(res.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        pickerFragment.startActivityForResult(intent, DevPickerFragment.REQUEST_PICK_WAV)
    }

    private fun handleSaveAction(activity: Activity, boardView: KendangBoardView) {
        val targetUri = session.sourceUri
        if (targetUri != null) {
            try {
                val updatedKit = session.saveToPackage(targetUri, activity.contentResolver, activity.cacheDir)
                boardView.kit = updatedKit
                Toast.makeText(activity, "✓ Kit berhasil disimpan ke berkas sumber (v${updatedKit.version})", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Izin hilang atau gagal: buka Simpan Sebagai
                AlertDialog.Builder(activity)
                    .setTitle("Gagal Menimpa Berkas")
                    .setMessage("Gagal menulis ke berkas sumber: ${e.message}\nPilih lokasi baru untuk Simpan Sebagai.")
                    .setPositiveButton("Simpan Sebagai") { _, _ ->
                        launchSaveAsPicker(activity, boardView)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        } else {
            launchSaveAsPicker(activity, boardView)
        }
    }

    private fun launchSaveAsPicker(activity: Activity, boardView: KendangBoardView) {
        val kit = session.workingKit ?: return
        val fragmentManager = activity.fragmentManager
        var pickerFragment = fragmentManager.findFragmentByTag("DevPickerFragment") as? DevPickerFragment
        if (pickerFragment == null) {
            pickerFragment = DevPickerFragment()
            fragmentManager.beginTransaction().add(pickerFragment, "DevPickerFragment").commitAllowingStateLoss()
            fragmentManager.executePendingTransactions()
        }

        pickerFragment.onFilePicked = { uri ->
            if (uri != null) {
                try {
                    val updated = session.saveToPackage(uri, activity.contentResolver, activity.cacheDir)
                    boardView.kit = updated
                    Toast.makeText(activity, "✓ Berhasil disimpan sebagai '${kit.name}.vrkendang' (v${updated.version})", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(activity, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "${kit.kitId}.vrkendang")
        }
        pickerFragment.startActivityForResult(intent, DevPickerFragment.REQUEST_PICK_WAV)
    }

    private fun promptCreateNewKit(activity: Activity, boardView: KendangBoardView) {
        if (session.hasUnsavedChanges) {
            AlertDialog.Builder(activity)
                .setTitle("Perubahan Belum Disimpan")
                .setMessage("Salinan kerja memiliki perubahan yang belum disimpan. Lanjutkan membuat kit baru?")
                .setPositiveButton("Lanjutkan") { _, _ ->
                    showNewKitDialog(activity, boardView)
                }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            showNewKitDialog(activity, boardView)
        }
    }

    private fun showNewKitDialog(activity: Activity, boardView: KendangBoardView) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val idEdit = EditText(activity).apply { hint = "kitId (misal: new-metro-v2)" }
        val nameEdit = EditText(activity).apply { hint = "Nama Kit (misal: New Metro 2)" }
        layout.addView(idEdit)
        layout.addView(nameEdit)

        AlertDialog.Builder(activity)
            .setTitle("Buat Kit Baru")
            .setView(layout)
            .setPositiveButton("Buat") { _, _ ->
                val id = idEdit.text.toString().trim()
                val name = nameEdit.text.toString().trim()
                try {
                    val newKit = session.createNewKit(id, name, null)
                    applyKitToBoard(newKit, boardView, session.devKitDir)
                    Toast.makeText(activity, "Kit baru '$id' berhasil dibuat!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(activity)
                        .setTitle("Pembuatan Ditolak")
                        .setMessage(e.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun promptOpenPackage(activity: Activity, boardView: KendangBoardView) {
        val fragmentManager = activity.fragmentManager
        var pickerFragment = fragmentManager.findFragmentByTag("DevPickerFragment") as? DevPickerFragment
        if (pickerFragment == null) {
            pickerFragment = DevPickerFragment()
            fragmentManager.beginTransaction().add(pickerFragment, "DevPickerFragment").commitAllowingStateLoss()
            fragmentManager.executePendingTransactions()
        }

        pickerFragment.onFilePicked = { uri ->
            if (uri != null) {
                try {
                    val input = activity.contentResolver.openInputStream(uri)
                    if (input != null) {
                        val openedKit = session.openKitPackage(input, uri, activity.cacheDir)
                        applyKitToBoard(openedKit, boardView, session.devKitDir)
                        Toast.makeText(activity, "✓ Kit '${openedKit.name}' berhasil dibuka!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    AlertDialog.Builder(activity)
                        .setTitle("Gagal Membuka Paket")
                        .setMessage("Paket tidak valid atau rusak: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        pickerFragment.startActivityForResult(intent, DevPickerFragment.REQUEST_PICK_WAV)
    }

    private fun promptOpenBuiltIn(activity: Activity, boardView: KendangBoardView) {
        val kit = session.openBuiltInKit("new-metro", activity.assets)
        applyKitToBoard(kit, boardView, session.devKitDir)
        Toast.makeText(activity, "Kit bawaan 'new-metro' dimuat ke salinan kerja", Toast.LENGTH_SHORT).show()
    }

    private fun createSmallButton(
        context: Context,
        label: String,
        bgColorHex: String,
        onClick: () -> Unit
    ): Button {
        val density = context.resources.displayMetrics.density
        return Button(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColorHex))
                cornerRadius = (8 * density)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            layoutParams = lp
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setOnClickListener { onClick() }
        }
    }
}
