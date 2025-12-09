package com.owenzx.lightedit.core.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageFilters {

    enum class FilterType {
        ORIGINAL,   // 原图
        BLACK_WHITE,// 黑白
        VINTAGE,    // 复古
        FRESH,      // 清新
        WARM,       // 暖色
        COOL        // 冷色
    }

    /**
     * 根据滤镜类型对 Bitmap 进行处理，返回新的 Bitmap。
     * 注意：不会修改原 Bitmap。
     */
    fun applyFilter(source: Bitmap, type: FilterType): Bitmap {
        // 统一用 ARGB_8888，避免某些格式不支持 ColorMatrix
        val config = Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(source.width, source.height, config)

        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (type) {
            FilterType.ORIGINAL -> {
                // 原图直接画上去即可
                canvas.drawBitmap(source, 0f, 0f, null)
                return result
            }

            FilterType.BLACK_WHITE -> {
                // 黑白：去饱和
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            FilterType.VINTAGE -> {
                // 复古：先降饱和，再加一点棕黄色调（类似 sepia）
                val cm = ColorMatrix()

                // sepia 大致矩阵
                cm.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                ))

                // 降一点饱和，避免太夸张
                val sat = ColorMatrix()
                sat.setSaturation(0.8f)
                cm.postConcat(sat)

                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            FilterType.FRESH -> {
                // 清新：略微提升饱和度 + 稍微偏冷一点
                val cm = ColorMatrix()

                // 提升饱和度
                val sat = ColorMatrix()
                sat.setSaturation(1.2f) // 比 1.0 稍微鲜艳一点
                cm.set(sat)

                // 冷色偏移：轻微增加蓝色分量
                val coolShift = ColorMatrix(floatArrayOf(
                    1.0f, 0f,   0f,   0f, 0f,
                    0f,   1.0f, 0f,   0f, 0f,
                    0f,   0f,   1.1f, 0f, 5f, // 蓝色略提高 + 少量偏移
                    0f,   0f,   0f,   1f, 0f
                ))
                cm.postConcat(coolShift)

                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            FilterType.WARM -> {
                // 暖色：提升红色，略微降低蓝色
                val cm = ColorMatrix(floatArrayOf(
                    1.1f, 0f,   0f,   0f, 10f,  // Red: 乘以 1.1 + 少量偏移
                    0f,   1.0f, 0f,   0f, 0f,   // Green: 保持
                    0f,   0f,   0.9f, 0f, -10f, // Blue: 略微降低
                    0f,   0f,   0f,   1f, 0f
                ))

                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            FilterType.COOL -> {
                // 冷色：提升蓝色，略微降低红色
                val cm = ColorMatrix(floatArrayOf(
                    0.9f, 0f,   0f,   0f, -10f, // Red 降低
                    0f,   1.0f, 0f,   0f, 0f,
                    0f,   0f,   1.1f, 0f, 10f,  // Blue 增加
                    0f,   0f,   0f,   1f, 0f
                ))

                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
        }

        return result
    }

    /**
     * 逐像素黑白滤镜示例
     */
    fun toGrayPixelByPixel(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = source.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)

                // 标准灰度公式
                val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
                val newColor = Color.argb(Color.alpha(color), gray, gray, gray)
                result.setPixel(x, y, newColor)
            }
        }

        return result
    }
}
