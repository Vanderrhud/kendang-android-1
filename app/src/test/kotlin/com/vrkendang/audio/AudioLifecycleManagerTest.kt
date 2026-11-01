package com.vrkendang.audio

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit test untuk [AudioLifecycleManager].
 *
 * Menguji logika transisi Activity:
 * 1. Startup: Activity pertama start -> resumeStream dipanggil 1x.
 * 2. Transisi internal: Activity B start sebelum Activity A stop -> count tetap >= 1, pauseStream TIDAK dipanggil.
 * 3. Kembali internal: Activity A start sebelum Activity B stop -> count tetap >= 1, pauseStream TIDAK dipanggil.
 * 4. Background penuh: Activity terakhir stop -> pauseStream dipanggil 1x.
 * 5. Re-entry: Activity start kembali dari background -> resumeStream dipanggil 1x.
 */
class AudioLifecycleManagerTest {

    private var resumeCount = 0
    private var pauseCount = 0

    @BeforeTest
    fun setUp() {
        resumeCount = 0
        pauseCount = 0
        AudioLifecycleManager.startedActivityCount = 0
        AudioLifecycleManager.onResumeAction = { resumeCount++ }
        AudioLifecycleManager.onPauseAction = { pauseCount++ }
    }

    @AfterTest
    fun tearDown() {
        AudioLifecycleManager.startedActivityCount = 0
    }

    @Test
    fun `startup activity pertama memicu resume stream`() {
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(1, AudioLifecycleManager.startedActivityCount)
        assertEquals(1, resumeCount)
        assertEquals(0, pauseCount)
    }

    @Test
    fun `transisi internal antar-activity tidak memicu pause stream`() {
        // 1. MainActivity aktif di foreground
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(1, resumeCount)
        assertEquals(0, pauseCount)

        // 2. Navigasi ke DevAuthoringActivity: DevAuthoringActivity.onStart() duluan
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(2, AudioLifecycleManager.startedActivityCount)
        assertEquals(1, resumeCount, "resumeStream tidak boleh dipanggil ulang jika count > 1")
        assertEquals(0, pauseCount)

        // 3. MainActivity.onStop() menyusul
        AudioLifecycleManager.handleActivityStopped()
        assertEquals(1, AudioLifecycleManager.startedActivityCount)
        assertEquals(0, pauseCount, "pauseStream TIDAK boleh dipanggil saat transisi antar-Activity!")

        // 4. Kembali dari DevAuthoringActivity ke MainActivity: MainActivity.onStart() duluan
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(2, AudioLifecycleManager.startedActivityCount)
        assertEquals(1, resumeCount)
        assertEquals(0, pauseCount)

        // 5. DevAuthoringActivity.onStop() menyusul
        AudioLifecycleManager.handleActivityStopped()
        assertEquals(1, AudioLifecycleManager.startedActivityCount)
        assertEquals(0, pauseCount, "pauseStream TIDAK boleh dipanggil saat kembali ke MainActivity!")
    }

    @Test
    fun `seluruh activity masuk background memicu pause stream`() {
        // 1. Activity masuk foreground
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(1, resumeCount)
        assertEquals(0, pauseCount)

        // 2. User menekan Home (masuk background penuh)
        AudioLifecycleManager.handleActivityStopped()
        assertEquals(0, AudioLifecycleManager.startedActivityCount)
        assertEquals(1, pauseCount, "pauseStream harus dipanggil ketika aplikasi di background")

        // 3. User membuka kembali aplikasi dari Recent Apps
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(1, AudioLifecycleManager.startedActivityCount)
        assertEquals(2, resumeCount, "resumeStream harus dipanggil kembali saat masuk foreground")
    }

    @Test
    fun `perubahan konfigurasi layar tidak memicu pause stream`() {
        AudioLifecycleManager.handleActivityStarted()
        assertEquals(1, resumeCount)

        // Stop dengan isChangingConfigurations = true
        AudioLifecycleManager.handleActivityStopped(isChangingConfigurations = true)
        assertEquals(0, AudioLifecycleManager.startedActivityCount)
        assertEquals(0, pauseCount, "Rotasi layar / perubahan konfigurasi tidak boleh menjeda audio")
    }
}
