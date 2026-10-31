package com.vrkendang.view

import android.app.Activity
import com.vrkendang.dev.DevBridgeProvider

/**
 * Implementasi menu pengaturan untuk build debug.
 *
 * Pada build debug, tersedia kedua opsi: "Edit Tata Letak" dan "Mode Developer" (Task 9.3).
 */
object SettingsMenuProviderImpl : SettingsMenuProvider {

    override fun getMenuItems(): List<SettingsMenuItem> {
        return listOf(
            SettingsMenuItem("edit_layout", "Edit Tata Letak"),
            SettingsMenuItem("dev_mode", "Mode Developer")
        )
    }

    override fun onMenuItemSelected(
        item: SettingsMenuItem,
        activity: Activity,
        boardView: KendangBoardView
    ) {
        when (item.id) {
            "edit_layout" -> {
                // Panggil method openCalibrationMode pada Activity jika tersedia, atau alihkan mode board
                val method = activity.javaClass.methods.firstOrNull { it.name == "openCalibrationMode" }
                if (method != null) {
                    method.invoke(activity)
                } else {
                    boardView.mode = KendangBoardView.BoardMode.EDIT_LAYOUT
                }
            }
            "dev_mode" -> {
                DevBridgeProvider.get().attach(activity, boardView)
            }
        }
    }
}
