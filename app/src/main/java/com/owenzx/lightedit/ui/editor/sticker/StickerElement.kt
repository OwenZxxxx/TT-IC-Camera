package com.owenzx.lightedit.ui.editor.sticker

import android.graphics.Bitmap
import android.graphics.RectF

data class StickerElement(
    val bitmap: Bitmap,
    var centerX: Float,
    var centerY: Float,
    var scale: Float,
    var rotation: Float,
    // 旋转后外接矩形，用于命中检测
    val bounds: RectF = RectF(),
    // 三个角按钮的点击区域
    val deleteBounds: RectF = RectF(),   // 右上角：删除
    val plusBounds: RectF = RectF(),     // 左下角：+1
    val scaleBounds: RectF = RectF(),    // 右下角：缩放+旋转
    // 旋转后的四个角点：TL(x,y), TR(x,y), BR(x,y), BL(x,y)
    val cornerPoints: FloatArray = FloatArray(8)
)
