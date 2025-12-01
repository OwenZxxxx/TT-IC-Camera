package com.owenzx.lightedit.core.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.collection.LruCache
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

// 内部缓存/线程池也是全局共享
object ThumbnailLoader {

    // 简单的内存缓存，key 用 photoId
    // maxSize 这里先随便定个 50，之后可根据内存情况调
    private val cache = object : LruCache<Long, Bitmap>(50) {
        override fun sizeOf(key: Long, value: Bitmap): Int {
            // 粗略估算：以“张数”为单位计数
            return 1
        }
    }

    // 固定大小线程池
    private val executor = Executors.newFixedThreadPool(4)

    // 入口方法
    fun loadSquareThumbnail(
        imageView: ImageView,               // 要显示缩略图的 ImageView
        resolver: ContentResolver,          // 用来读取图片
        photoId: Long,                      // 图片的id
        uri: Uri,                           // 图片的 Uri
        targetSizePx: Int                   // 缩略图最终尺寸（px）
    ) {
        // 给这次加载打一个“tag”，防止 RecyclerView 复用ViewHolder时错图
        imageView.tag = photoId

        // 尝试从Lru缓存中拿减少重复 decode
        cache.get(photoId)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        // 先设置个占位图 避免复用时出现“上一张图残留”
        imageView.setImageDrawable(null)

        // 避免内存泄露
        val imageViewRef = WeakReference(imageView)

        // 启动后台线程池加载 + 裁剪
        executor.execute {
            // 再次检查一下缓存，避免刚刚别的线程已经算过了
            cache.get(photoId)?.let { cached ->
                val iv = imageViewRef.get() ?: return@execute
                iv.post {
                    if (iv.tag == photoId) {
                        iv.setImageBitmap(cached)
                    }
                }
                return@execute
            }

            // 真正 decode → 裁剪 → 缩放
            val bitmap = decodeAndCropSquare(resolver, uri, targetSizePx) ?: return@execute

            // 放到缓存里
            cache.put(photoId, bitmap)

            // 回到主线程更新 UI
            val iv = imageViewRef.get() ?: return@execute
            iv.post {
                // 再次确认 tag，防止错位
                if (iv.tag == photoId) {
                    iv.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun decodeAndCropSquare(
        resolver: ContentResolver,
        uri: Uri,
        targetSizePx: Int
    ): Bitmap? {
        // 只读尺寸，不 decode 数据 避免OOM
        val optionsBounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, optionsBounds)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        // 读取宽高并校验
        val srcWidth = optionsBounds.outWidth
        val srcHeight = optionsBounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        // 计算 inSampleSize，尽量接近目标尺寸，避免 decode 超大图
        val sampleSize = calculateInSampleSize(
            srcWidth,
            srcHeight,
            targetSizePx,
            targetSizePx
        )

        val optionsDecode = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded: Bitmap = try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, optionsDecode)
            } ?: return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        // 中心裁剪成正方形
        val square = centerCropSquare(decoded)

        if (square != decoded) {
            decoded.recycle()
        }

        // 缩放到目标尺寸
        if (square.width == targetSizePx && square.height == targetSizePx) {
            return square
        }

        val scaled = Bitmap.createScaledBitmap(square, targetSizePx, targetSizePx, true)
        if (scaled != square) {
            square.recycle()
        }
        return scaled
    }

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            // 保证最终 decode 出来的宽高都 >= 请求尺寸的 2 倍左右，再做裁剪 & 缩放
            while ((halfHeight / inSampleSize) >= reqHeight * 2 &&
                (halfWidth / inSampleSize) >= reqWidth * 2
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    // 裁剪成正方形
    private fun centerCropSquare(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        if (width == height) return source

        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2

        return Bitmap.createBitmap(source, x, y, size, size)
    }
}