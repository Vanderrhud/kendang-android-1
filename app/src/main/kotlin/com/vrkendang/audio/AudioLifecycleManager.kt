package com.vrkendang.audio

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Pengelola siklus hidup audio engine secara terpusat pada tingkat aplikasi.
 *
 * Mencegah tabrakan siklus hidup (lifecycle collision):
 * - Saat berpindah antar-Activity internal (misalnya MainActivity <-> DevAuthoringActivity),
 *   Activity baru melakukan onStart() sebelum Activity sebelumnya memanggil onStop().
 *   Dengan menghitung jumlah Activity aktif (`startedActivityCount`), stream audio
 *   TIDAK dihentikan di tengah transisi antar-Activity.
 * - Stream audio hanya di-pause (pauseStream) ketika SELURUH Activity aplikasi telah
 *   berada di background (`startedActivityCount == 0`), atau di-resume (resumeStream)
 *   saat salah satu Activity kembali ke foreground.
 */
object AudioLifecycleManager : Application.ActivityLifecycleCallbacks {

    private const val TAG = "AudioLifecycleManager"

    @Volatile
    var startedActivityCount = 0
        internal set

    @Volatile
    private var isRegistered = false

    internal var onResumeAction: () -> Unit = {
        runCatching { AudioEngine.resumeStream() }
            .onFailure { Log.e(TAG, "Gagal resume stream audio", it) }
    }

    internal var onPauseAction: () -> Unit = {
        runCatching { AudioEngine.pauseStream() }
            .onFailure { Log.e(TAG, "Gagal pause stream audio", it) }
    }

    /**
     * Mendaftarkan callback siklus hidup ke [Application]. Idempoten dan aman dipanggil berulang kali.
     */
    fun init(app: Application) {
        if (!isRegistered) {
            synchronized(this) {
                if (!isRegistered) {
                    app.registerActivityLifecycleCallbacks(this)
                    isRegistered = true
                    Log.d(TAG, "AudioLifecycleManager berhasil didaftarkan ke Application")
                }
            }
        }
    }

    /**
     * Menandai satu Activity masuk fase started.
     */
    fun handleActivityStarted() {
        startedActivityCount++
        if (startedActivityCount == 1) {
            onResumeAction()
        }
    }

    /**
     * Menandai satu Activity masuk fase stopped.
     */
    fun handleActivityStopped(isChangingConfigurations: Boolean = false) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0 && !isChangingConfigurations) {
            onPauseAction()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d(TAG, "onActivityStarted: ${activity.javaClass.simpleName}, count=${startedActivityCount + 1}")
        handleActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "onActivityStopped: ${activity.javaClass.simpleName}, count=${(startedActivityCount - 1).coerceAtLeast(0)}")
        handleActivityStopped(activity.isChangingConfigurations)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
