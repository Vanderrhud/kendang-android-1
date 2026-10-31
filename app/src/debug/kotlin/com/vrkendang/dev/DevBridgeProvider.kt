package com.vrkendang.dev

import com.vrkendang.kitauthoring.DevKitAuthoringBridge

/**
 * Penyedia DevBridge untuk build debug.
 */
object DevBridgeProvider {
    private val instance = DevKitAuthoringBridge()

    fun get(): DevBridge = instance
}
