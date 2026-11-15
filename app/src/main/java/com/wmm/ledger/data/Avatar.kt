package com.wmm.ledger.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

object Avatar {
    fun import(resolver: ContentResolver, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (largestSide / sample > 1024) sample *= 2
        val bitmap = requireNotNull(resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        })
        var avatar = bitmap
        try {
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
            }.getOrNull() ?: 1
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val side = minOf(bitmap.width, bitmap.height)
            val scale = (384f / side).coerceAtMost(1f)
            matrix.postScale(scale, scale)
            avatar = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, matrix, true)
            return ByteArrayOutputStream().use {
                check(avatar.compress(Bitmap.CompressFormat.JPEG, 87, it))
                it.toByteArray().also { result -> require(result.size <= MAX_AVATAR_BYTES) }
            }
        } finally {
            if (avatar !== bitmap) avatar.recycle()
            bitmap.recycle()
        }
    }
}
