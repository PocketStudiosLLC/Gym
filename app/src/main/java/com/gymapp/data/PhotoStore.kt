package com.gymapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** Stores exercise photos in app-private storage as square, compressed JPEGs. */
class PhotoStore(private val context: Context) {
    val photoDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** A fresh content Uri the camera app can write a full-size capture into. */
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Reads the image at [uri], fixes its rotation, center-crops it to a square,
     * scales it to [SIZE] px and saves it. Returns the saved file's path, or null on failure.
     */
    fun importPhoto(uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use(::rotationOf) ?: 0f
        val square = cropSquare(decoded, rotation)
        saveBitmap(square)
    }.getOrNull()

    /** Saves raw JPEG bytes (used by backup import). */
    fun saveBytes(bytes: ByteArray): String {
        val file = newPhotoFile()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun delete(path: String?) {
        if (path == null) return
        val f = File(path)
        if (f.parentFile == photoDir) f.delete()
    }

    fun deleteAll() {
        photoDir.listFiles()?.forEach { it.delete() }
    }

    fun clearCameraCache() {
        File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
    }

    private fun rotationOf(stream: InputStream): Float =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

    private fun cropSquare(src: Bitmap, rotation: Float): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val scale = SIZE.toFloat() / max(side, 1)
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        return Bitmap.createBitmap(src, x, y, side, side, m, true)
    }

    private fun saveBitmap(bmp: Bitmap): String {
        val file = newPhotoFile()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
    }

    // Unique names so image caches never show a stale photo after it is replaced.
    private fun newPhotoFile() = File(photoDir, "photo_${System.nanoTime()}.jpg")

    companion object {
        const val SIZE = 512
    }
}
