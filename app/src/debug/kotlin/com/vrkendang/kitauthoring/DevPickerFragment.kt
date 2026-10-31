package com.vrkendang.kitauthoring

import android.app.Fragment
import android.content.Intent
import android.net.Uri

/**
 * Fragment pendamping tanpa UI yang mengelola siklus hidup `startActivityForResult`
 * untuk picker file SAF (Storage Access Framework) di Mode Developer.
 *
 * Menggunakan android.app.Fragment (bukan AndroidX) karena DevKitAuthoringBridge
 * beroperasi dengan Activity.fragmentManager standar, yang sudah cukup untuk kebutuhan
 * MVP ini dan menghindari penambahan dependensi AndroidX Fragment ke build debug.
 *
 * Callback [onFilePicked] dipanggil dari UI thread setelah pengguna memilih file atau
 * membatalkan operasi (null jika dibatalkan).
 */
class DevPickerFragment : Fragment() {

    companion object {
        const val REQUEST_PICK_WAV = 9001
    }

    /** Dipanggil setelah picker selesai: URI file terpilih, atau null jika dibatalkan. */
    var onFilePicked: ((uri: Uri?) -> Unit)? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_WAV) {
            val uri: Uri? = if (resultCode == android.app.Activity.RESULT_OK) data?.data else null
            onFilePicked?.invoke(uri)
            onFilePicked = null
        }
    }
}
