package com.vrkendang.view

import android.app.Activity

/**
 * Implementasi menu pengaturan untuk build release.
 *
 * Pada build release, hanya opsi "Edit Tata Letak" yang disediakan (Task 9.3).
 */
object SettingsMenuProviderImpl : SettingsMenuProvider {

    override fun getMenuItems(): List<SettingsMenuItem> {
        return listOf(
            SettingsMenuItem("edit_layout", "Edit Tata Letak")
        )
    }

    override fun onMenuItemSelected(
        item: SettingsMenuItem,
        activity: Activity,
        boardView: KendangBoardView
    ) {
        if (item.id == "edit_layout") {
            val method = activity.javaClass.methods.firstOrNull { it.name == "openCalibrationMode" }
            if (method != null) {
                method.invoke(activity)
            } else {
                boardView.mode = KendangBoardView.BoardMode.EDIT_LAYOUT
            }
        }
    }
}
