package com.vrkendang.image

import java.io.InputStream

/**
 * Sumber pembacaan file gambar relatif terhadap akar sebuah kit.
 *
 * Kit bawaan membuka file dari `AssetManager`, kit internal dari `filesDir/kits/<kitId>/`,
 * dan salinan kerja Mode Developer dari `filesDir/dev-kit/`.
 * Abstraksi ini memungkinkan [KitImageLoader] diuji unit test pada JVM tanpa Android runtime.
 */
fun interface KitImageSource {
    /**
     * Membuka aliran data gambar relatif terhadap direktori kit, misalnya `images/bass.png`.
     * @throws Exception bila file tidak ada atau tidak dapat dibaca.
     */
    fun open(imagePath: String): InputStream
}
