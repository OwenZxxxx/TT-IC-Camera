package com.owenzx.lightedit.ui.editor.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min
import android.graphics.drawable.BitmapDrawable

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // 当前用于绘制的 Matrix
    private val drawMatrix = Matrix()

    // 视图尺寸
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    // 图片原始尺寸
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    // 初始适配的缩放（FitCenter 的 scale）
    private var baseScale: Float = 1f

    // 用户手势附加的缩放倍数（相对于 baseScale 的 0.5x ~ 2x）
    private var userScaleFactor: Float = 1f
    private val minUserScale = 0.5f
    private val maxUserScale = 2.0f

    // 拖动相关
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // 初始 FitCenter 是否已经应用（避免多次重复）
    private var hasInitFit = false

    // 拖动时允许“超出边界”的最大像素（白边宽度）
    private val overScroll = 80f

    // 回弹动画
    private var settleAnimator: ValueAnimator? = null

    // 表示剪裁功能开启
    private var cropModeEnabled: Boolean = false

    // 缩放手势检测器
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {

                // 计算缩放后的目标倍数，并限制在 [minScale, maxScale]
                val scaleFactor = detector.scaleFactor
                val target = userScaleFactor * scaleFactor
                val clamped = target.coerceIn(minUserScale, maxUserScale)

                // 本次实际需要乘上的比例，以双指中心为缩放中心
                val realFactor = clamped / userScaleFactor
                if (realFactor != 1f) {
                    drawMatrix.postScale(
                        realFactor,
                        realFactor,
                        detector.focusX,
                        detector.focusY
                    )
                    userScaleFactor = clamped
                    // 缩放时仍然做“硬边界”修正
                    applyHardBounds()
                }
                return true
            }
        }
    )

    init {
        // 用 Matrix 来控制显示
        scaleType = ScaleType.MATRIX
    }

    // 生命周期尺寸变化：用来触发初始 FitCenter
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        applyInitialFitIfPossible()
    }

    // 设置图片：统一更新原始尺寸，并触发初始适配
    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        if (bm != null) {
            imageWidth = bm.width
            imageHeight = bm.height
            hasInitFit = false
            post { applyInitialFitIfPossible() }
        } else {
            imageWidth = 0
            imageHeight = 0
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        drawable?.let { d ->
            imageWidth = d.intrinsicWidth
            imageHeight = d.intrinsicHeight
            hasInitFit = false
            post { applyInitialFitIfPossible() }
        } ?: run {
            imageWidth = 0
            imageHeight = 0
        }
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        drawable?.let { d ->
            imageWidth = d.intrinsicWidth
            imageHeight = d.intrinsicHeight
            hasInitFit = false
            post { applyInitialFitIfPossible() }
        } ?: run {
            imageWidth = 0
            imageHeight = 0
        }
    }

    // 初始适配：FitCenter + 重置 userScaleFactor
    private fun applyInitialFitIfPossible() {
        if (viewWidth == 0 || viewHeight == 0) return
        if (imageWidth <= 0 || imageHeight <= 0) return
        if (hasInitFit) return

        // 终止可能存在的回弹动画
        cancelSettleAnimator()

        drawMatrix.reset()

        // FitCenter：以最小比例让整张图刚好完整显示在画布内
        val scaleX = viewWidth.toFloat() / imageWidth
        val scaleY = viewHeight.toFloat() / imageHeight
        baseScale = min(scaleX, scaleY)

        // 先按 baseScale 缩放
        drawMatrix.postScale(baseScale, baseScale)

        // 再居中
        val scaledWidth = imageWidth * baseScale
        val scaledHeight = imageHeight * baseScale
        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f
        drawMatrix.postTranslate(dx, dy)

        // 用户附加缩放从 1f 开始
        userScaleFactor = 1f
        hasInitFit = true

        applyHardBounds()
    }

    // 拖动 + 边界限制逻辑
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 裁剪模式下，图片不响应缩放/拖动，把事件交给上层（裁剪框）
        if (cropModeEnabled) {
            return false
        }

        // 先交给缩放检测器处理（内部会根据多指事件计算缩放）
        scaleDetector.onTouchEvent(event)

        // 如果当前是两指及以上，优先视为缩放，不处理拖动
        if (event.pointerCount > 1) {
            isDragging = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelSettleAnimator()  // 用户开始操作时，停止回弹动画
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    if (dx != 0f || dy != 0f) {
                        drawMatrix.postTranslate(dx, dy)
                        // 拖动过程使用“软边界”：允许 overScroll，给用户“拉出白边”的感觉
                        applySoftBounds()

                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                // 抬手时再做一次“硬边界”动画回弹
                settleToHardBoundsWithAnimation()
            }
        }

        // 消费事件
        return true
    }



    // 软边界：拖动中调用 允许在合法区域外再多出 overScroll 像素的白边，
    private fun applySoftBounds() {
        val drawable = drawable ?: run {
            imageMatrix = drawMatrix
            return
        }

        if (viewWidth == 0 || viewHeight == 0) {
            imageMatrix = drawMatrix
            return
        }

        val rect = RectF(
            0f,
            0f,
            drawable.intrinsicWidth.toFloat(),
            drawable.intrinsicHeight.toFloat()
        )
        drawMatrix.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        // 水平方向
        if (rect.width() <= viewWidth) {
            // 小图：中心可以在 View 中心的 ±overScroll 范围内晃动
            val centerOffset = viewWidth / 2f - rect.centerX()
            if (centerOffset > overScroll) {
                deltaX = centerOffset - overScroll
            } else if (centerOffset < -overScroll) {
                deltaX = centerOffset + overScroll
            }
        } else {
            // 大图：左右边界允许超出 0 ~ overScroll
            if (rect.left > overScroll) {
                deltaX = overScroll - rect.left
            } else if (rect.right < viewWidth - overScroll) {
                deltaX = viewWidth - overScroll - rect.right
            }
        }

        // 垂直方向
        if (rect.height() <= viewHeight) {
            val centerOffset = viewHeight / 2f - rect.centerY()
            if (centerOffset > overScroll) {
                deltaY = centerOffset - overScroll
            } else if (centerOffset < -overScroll) {
                deltaY = centerOffset + overScroll
            }
        } else {
            if (rect.top > overScroll) {
                deltaY = overScroll - rect.top
            } else if (rect.bottom < viewHeight - overScroll) {
                deltaY = viewHeight - overScroll - rect.bottom
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            drawMatrix.postTranslate(deltaX, deltaY)
        }

        imageMatrix = drawMatrix
    }

    // 硬边界：不允许有任何超出。
    private fun applyHardBounds() {
        val drawable = drawable ?: run {
            imageMatrix = drawMatrix
            return
        }
        if (viewWidth == 0 || viewHeight == 0) {
            imageMatrix = drawMatrix
            return
        }

        val rect = RectF(
            0f,
            0f,
            drawable.intrinsicWidth.toFloat(),
            drawable.intrinsicHeight.toFloat()
        )
        drawMatrix.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        // 水平方向
        if (rect.width() <= viewWidth) {
            // 小图：始终完全居中
            deltaX = viewWidth / 2f - rect.centerX()
        } else {
            // 大图：至少有一边贴在 View 边缘，无法看到“无限白边”
            if (rect.left > 0) {
                deltaX = -rect.left
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right
            }
        }

        // 垂直方向
        if (rect.height() <= viewHeight) {
            deltaY = viewHeight / 2f - rect.centerY()
        } else {
            if (rect.top > 0) {
                deltaY = -rect.top
            } else if (rect.bottom < viewHeight) {
                deltaY = viewHeight - rect.bottom
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            drawMatrix.postTranslate(deltaX, deltaY)
        }

        imageMatrix = drawMatrix
    }

    // 计算当前矩形相对“硬边界”的偏移量，用动画把它平滑移回合法位置
    private fun settleToHardBoundsWithAnimation() {
        val drawable = drawable ?: run {
            imageMatrix = drawMatrix
            return
        }
        if (viewWidth == 0 || viewHeight == 0) {
            imageMatrix = drawMatrix
            return
        }

        val rect = RectF(
            0f,
            0f,
            drawable.intrinsicWidth.toFloat(),
            drawable.intrinsicHeight.toFloat()
        )
        drawMatrix.mapRect(rect)

        var targetDx = 0f
        var targetDy = 0f

        // 和 applyHardBounds 逻辑相同，不立即 postTranslate
        if (rect.width() <= viewWidth) {
            targetDx = viewWidth / 2f - rect.centerX()
        } else {
            if (rect.left > 0) {
                targetDx = -rect.left
            } else if (rect.right < viewWidth) {
                targetDx = viewWidth - rect.right
            }
        }

        if (rect.height() <= viewHeight) {
            targetDy = viewHeight / 2f - rect.centerY()
        } else {
            if (rect.top > 0) {
                targetDy = -rect.top
            } else if (rect.bottom < viewHeight) {
                targetDy = viewHeight - rect.bottom
            }
        }

        // 不需要回弹
        if (targetDx == 0f && targetDy == 0f) {
            imageMatrix = drawMatrix
            return
        }

        // 启动动画，从 0 -> 1，逐步平移 targetDx / targetDy
        cancelSettleAnimator()

        var lastFraction = 0f
        settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val step = fraction - lastFraction
                lastFraction = fraction

                val dx = targetDx * step
                val dy = targetDy * step

                drawMatrix.postTranslate(dx, dy)
                imageMatrix = drawMatrix
            }
            start()
        }
    }

    // 根据裁剪框（View 坐标系）裁剪当前 Bitmap，返回新的 Bitmap
    fun getCroppedBitmap(cropRectInView: RectF): Bitmap? {
        // 拿到当前 Bitmap
        val drawable = drawable as? BitmapDrawable ?: return null
        val source = drawable.bitmap ?: return null
        if (source.width <= 0 || source.height <= 0) return null

        // 计算当前 Bitmap 在 View 中的显示范围（用于和裁剪框取交集）
        val imageBoundsInView = RectF(
            0f,
            0f,
            source.width.toFloat(),
            source.height.toFloat()
        )
        imageMatrix.mapRect(imageBoundsInView)

        // 和裁剪框做相交，避免裁出去的是“没有图片”的区域
        val intersectRect = RectF(cropRectInView)
        val hasIntersection = intersectRect.intersect(imageBoundsInView)
        if (!hasIntersection) {
            // 裁剪区域完全在图片外面，没必要裁
            return null
        }

        // 用 imageMatrix 的逆矩阵，把 View 坐标系下的 intersectRect 映射回 Bitmap 坐标
        val inverseMatrix = Matrix()
        if (!imageMatrix.invert(inverseMatrix)) {
            return null
        }

        val cropInBitmap = RectF(intersectRect)
        inverseMatrix.mapRect(cropInBitmap)

        // 做边界裁剪（防止浮点误差导致越界）
        val leftF = cropInBitmap.left.coerceIn(0f, source.width.toFloat())
        val topF = cropInBitmap.top.coerceIn(0f, source.height.toFloat())
        val rightF = cropInBitmap.right.coerceIn(0f, source.width.toFloat())
        val bottomF = cropInBitmap.bottom.coerceIn(0f, source.height.toFloat())

        val left = leftF.toInt()
        val top = topF.toInt()
        val width = (rightF - leftF).toInt().coerceAtLeast(1)
        val height = (bottomF - topF).toInt().coerceAtLeast(1)

        if (width <= 0 || height <= 0) return null
        if (left >= source.width || top >= source.height) return null

        val safeWidth = min(width, source.width - left)
        val safeHeight = min(height, source.height - top)

        return try {
            Bitmap.createBitmap(source, left, top, safeWidth, safeHeight)
        } catch (e: IllegalArgumentException) {
            null
        }
    }


    private fun cancelSettleAnimator() {
        settleAnimator?.cancel()
        settleAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelSettleAnimator()
    }

    // 裁剪模式：ZoomableImageView 不再处理触摸事件，事件会交给上面的 CropOverlayView
    fun setCropModeEnabled(enabled: Boolean) {
        cropModeEnabled = enabled
    }

}