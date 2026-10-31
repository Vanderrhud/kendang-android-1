package com.vrkendang.view

import android.app.Activity

/**
 * Item menu pada tombol Pengaturan (⚙) di header.
 */
data class SettingsMenuItem(
    val id: String,
    val title: String
)

/**
 * Penyedia item menu tombol Pengaturan (Task 9.3).
 *
 * Pada build debug: menampilkan "Edit Tata Letak" dan "Mode Developer".
 * Pada build release: hanya menampilkan "Edit Tata Letak" (memenuhi CI check 2.4).
 */
interface SettingsMenuProvider {
    fun getMenuItems(): List<SettingsMenuItem>

    fun onMenuItemSelected(
        item: SettingsMenuItem,
        activity: Activity,
        boardView: KendangBoardView
    )
}
