package com.example.smart_solar_mgt_app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

/**
 * Pure QR *generation* (ZXing) for the Prosumer's Energy Transfer Pass. Deliberately a different
 * library from the CameraX + ML Kit *scanning* path the Grid Operator uses - generation is a
 * one-shot bitmap render, scanning is a live camera pipeline, no reason to share a dependency.
 */
object QrBitmapEncoder {

    fun encode(content: String, sizePx: Int): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
