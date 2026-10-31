package com.vrkendang.dev

import com.vrkendang.kitauthoring.DevKitAuthoringBridge

/**
 * Penyedia DevBridge untuk build debug.
 */
object DevBridgeProvider {
    fun get(): DevBridge = DevKitAuthoringBridge()
}
