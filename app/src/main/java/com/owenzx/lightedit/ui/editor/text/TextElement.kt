package com.owenzx.lightedit.ui.editor.text

import android.graphics.Color
import android.graphics.RectF

/**
 * 单个文字元素的数据结构
 */
data class TextElement(
    var text: String = "双击编辑文字",

    // 文本块中心点（相对于 TextOverlayView 的坐标）
    var centerX: Float = 0f,
    var centerY: Float = 0f,

    // 样式相关（后面字体、字号、颜色、透明度调节都基于这些字段）
    var textSizeSp: Float = 18f,
    var textColor: Int = Color.WHITE,
    var alpha: Int = 255,
    var typefaceIndex: Int = 0,  // 0: 默认, 1: Serif, 2: Monospace

    // 预留给后面的缩放 / 旋转
    var scale: Float = 1f,
    var rotation: Float = 0f,

    // 运行时计算出来的边框（用于命中检测 & 画虚线框）
    @Transient val bounds: RectF = RectF(),

    // 左下角：新增文本按钮
    @Transient val plusBounds: RectF = RectF(),

    // 右下角：旋转/缩放手柄
    @Transient val rotateBounds: RectF = RectF(),

    // 右上角：删除按钮
    @Transient val deleteBounds: RectF = RectF()
)
