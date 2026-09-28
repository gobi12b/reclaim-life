package io.github.gobi12b.reclaimlife.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The profile photo lives only in the app's private files, as one small square JPEG. It isn't
 * backed up and never leaves the phone unless the user shares their card.
 */
internal object ProfilePhoto {
    private const val SIZE_PX = 512

    fun file(context: Context): File = File(context.filesDir, "profile/photo.jpg")

    /** Copies the picked image in, centre-cropped to a square and scaled down. False if it couldn't be read. */
    suspend fun save(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    // Decode near the target size; the crop below does the rest.
                    val scale = maxOf(1, minOf(info.size.width, info.size.height) / SIZE_PX)
                    decoder.setTargetSize(info.size.width / scale, info.size.height / scale)
                }
            } else {
                context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it) }
            } ?: return@runCatching false
            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true)
            val out = file(context).apply { parentFile?.mkdirs() }
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        file(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}
