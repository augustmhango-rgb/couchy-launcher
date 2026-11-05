package com.conreo.couchytv.premium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/** Untrusted image input is bounded before allocation; only normalized PNGs reach storage. */
object IconFiles {
    const val MAX_BYTES = 4 * 1024 * 1024
    fun save(context: Context, bytes: ByteArray): String {
        require(bytes.size in 1..MAX_BYTES) { "Choose an image smaller than 4 MB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192) { "Image dimensions must be at most 8192 pixels" }
        require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/webp")) { "Use PNG, JPG or WebP" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })) { "Invalid image" }
        val dir = File(context.filesDir, "custom-icons").apply { mkdirs() }
        val name = UUID.randomUUID().toString() + ".png"
        try { File(dir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return name
    }
    fun file(context: Context, name: String): File? =
        name.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.png")) }?.let { File(context.filesDir, "custom-icons/$it") }
}
