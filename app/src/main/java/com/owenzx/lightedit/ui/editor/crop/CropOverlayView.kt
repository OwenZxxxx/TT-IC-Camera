package com.owenzx.lightedit.ui.editor.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min



enum class AspectRatioMode {
    FREE,
    RATIO_1_1,
    RATIO_4_3,
    RATIO_16_9,
    RATIO_3_4,
    RATIO_9_16
}


// 裁剪模式下的遮罩 View：- 外层是半透明黑色 - 中间一个矩形裁剪区域（暂时固定大小）
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 画外面的“暗色遮罩”
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // 半透明黑
        style = Paint.Style.FILL
    }

    // 画裁剪框
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp2px(2f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // 当前裁剪框的View 坐标系
    private val cropRect = RectF()

    // 遮罩路径：外矩形 - 内矩形（裁剪框）
    private val clipPath = Path()

    // 拖动/缩放模式
    private enum class TouchMode {
        NONE,
        DRAG,              // 拖动整个裁剪框
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT,
        RESIZE_LEFT,
        RESIZE_TOP,
        RESIZE_RIGHT,
        RESIZE_BOTTOM
    }

    private var touchMode: TouchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    //  触碰角点 / 边点的判定半径
    private val handleRadius: Float = dp2px(24f)
    // 画出来的小圆点半径
    private val handleDrawRadius: Float = dp2px(5f)

    // 裁剪框最小宽高 - 避免被缩到几乎看不见
    private val minCropWidth: Float = dp2px(60f)
    private val minCropHeight: Float = dp2px(60f)

    // 当前宽高比模式
    private var aspectRatioMode: AspectRatioMode = AspectRatioMode.FREE

    // 固定宽高比：width / height 自由模式下为 null
    private var fixedAspectRatio: Float? = null

    // 根据 View 尺寸决定裁剪框初始位置与大小
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w == 0 || h == 0) return

        // 整体 padding 留一点边
        val padding = dp2px(24f)
        val leftBound = padding
        val rightBound = w - padding
        val availableWidth = rightBound - leftBound

        val topBound = h * 0.15f
        val bottomBound = h * 0.85f
        val availableHeight = bottomBound - topBound

        // 默认先使用 4:3 的初始矩形
        val targetAspect = 4f / 3f
        val viewAspect = availableWidth / availableHeight

        if (viewAspect > targetAspect) {
            // 高度限制
            val cropHeight = availableHeight
            val cropWidth = cropHeight * targetAspect
            val left = (w - cropWidth) / 2f
            val top = topBound
            cropRect.set(left, top, left + cropWidth, top + cropHeight)
        } else {
            // 宽度限制
            val cropWidth = availableWidth
            val cropHeight = cropWidth / targetAspect
            val left = leftBound
            val top = (h - cropHeight) / 2f
            cropRect.set(left, top, left + cropWidth, top + cropHeight)
        }

        updateClipPath()
    }

    // 根据当前 cropRect 更新外层遮罩的镂空区域
    private fun updateClipPath() {
        clipPath.reset()
        // 外矩形：整个 View
        clipPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        // 内矩形：裁剪框区域，CCW 形成“减去”的效果
        clipPath.addRect(cropRect, Path.Direction.CCW)
    }

    // 绘制
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 画外层暗色遮罩（中间镂空）
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.restore()

        // 画裁剪框边线
        canvas.drawRect(cropRect, framePaint)

        drawHandles(canvas)
    }

    // 可视化拖拽点
    private fun drawHandles(canvas: Canvas) {
        if (cropRect.isEmpty) return

        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom
        val centerX = cropRect.centerX()
        val centerY = cropRect.centerY()

        // 四个角
        canvas.drawCircle(left, top, handleDrawRadius, handlePaint)
        canvas.drawCircle(right, top, handleDrawRadius, handlePaint)
        canvas.drawCircle(left, bottom, handleDrawRadius, handlePaint)
        canvas.drawCircle(right, bottom, handleDrawRadius, handlePaint)
        if (aspectRatioMode == AspectRatioMode.FREE) {
            // 四条边中点（视觉提示，可以在固定比例下不响应，但仍画出来）
            canvas.drawCircle(centerX, top, handleDrawRadius, handlePaint)     // 上边中点
            canvas.drawCircle(centerX, bottom, handleDrawRadius, handlePaint)  // 下边中点
            canvas.drawCircle(left, centerY, handleDrawRadius, handlePaint)    // 左边中点
            canvas.drawCircle(right, centerY, handleDrawRadius, handlePaint)   // 右边中点
        }

    }

    // 外部接口：设置宽高比模式
    fun setAspectRatio(mode: AspectRatioMode) {
        aspectRatioMode = mode
        fixedAspectRatio = when (mode) {
            AspectRatioMode.FREE -> null
            AspectRatioMode.RATIO_1_1 -> 1f
            AspectRatioMode.RATIO_4_3 -> 4f / 3f
            AspectRatioMode.RATIO_16_9 -> 16f / 9f
            AspectRatioMode.RATIO_3_4 -> 3f / 4f
            AspectRatioMode.RATIO_9_16 -> 9f / 16f
        }

        // 调整当前裁剪框为新的比例，但保持中心尽量不变
        fixedAspectRatio?.let { ratio ->
            applyAspectRatioToCurrentRect(ratio)
        }

        updateClipPath()
        invalidate()
    }

    // 将 cropRect 调整为指定宽高比，尽量保持中心位置和尺寸不突变太大
    private fun applyAspectRatioToCurrentRect(ratio: Float) {
        if (width == 0 || height == 0) return

        val centerX = cropRect.centerX()
        val centerY = cropRect.centerY()
        val currentWidth = cropRect.width()
        val currentHeight = cropRect.height()

        if (currentWidth <= 0f || currentHeight <= 0f) return

        // 尝试以当前较小的边为基准，重新计算宽高
        var newWidth: Float
        var newHeight: Float

        val currentAspect = currentWidth / currentHeight

        if (currentAspect > ratio) {
            // 当前更“宽”，以高度为准
            newHeight = currentHeight
            newWidth = newHeight * ratio
        } else {
            // 当前更“高”，以宽度为准
            newWidth = currentWidth
            newHeight = newWidth / ratio
        }

        // 最小尺寸限制
        newWidth = max(newWidth, minCropWidth)
        newHeight = max(newHeight, minCropHeight)

        // 在不超出 View 边界的前提下尽量使用该宽高
        newWidth = min(newWidth, width.toFloat())
        newHeight = min(newHeight, height.toFloat())

        // 根据中心点重新计算矩形
        val halfW = newWidth / 2f
        val halfH = newHeight / 2f
        var left = centerX - halfW
        var top = centerY - halfH
        var right = centerX + halfW
        var bottom = centerY + halfH

        // 边界限制
        val dx = when {
            left < 0f -> -left
            right > width.toFloat() -> width.toFloat() - right
            else -> 0f
        }
        val dy = when {
            top < 0f -> -top
            bottom > height.toFloat() -> height.toFloat() - bottom
            else -> 0f
        }

        left += dx
        right += dx
        top += dy
        bottom += dy

        cropRect.set(left, top, right, bottom)
    }

    //  拖动裁剪框逻辑
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果 View 不可见 / 宽高为 0，直接不处理
        if (visibility != VISIBLE || width == 0 || height == 0) {
            return false
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 先检查是否按在某个角点上
                val handleMode = findTouchedHandle(x, y)
                if (handleMode != TouchMode.NONE) {
                    touchMode = handleMode
                    lastTouchX = x
                    lastTouchY = y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // 再检查是否按在裁剪框内部（用于拖动）
                if (cropRect.contains(x, y)) {
                    touchMode = TouchMode.DRAG
                    lastTouchX = x
                    lastTouchY = y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // 其他区域不处理，让事件传给下层 View
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                when (touchMode) {
                    TouchMode.DRAG -> {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        if (dx != 0f || dy != 0f) {
                            offsetCropRect(dx, dy)
                            lastTouchX = x
                            lastTouchY = y
                            invalidate()
                        }
                        return true
                    }

                    TouchMode.RESIZE_TOP_LEFT,
                    TouchMode.RESIZE_TOP_RIGHT,
                    TouchMode.RESIZE_BOTTOM_LEFT,
                    TouchMode.RESIZE_BOTTOM_RIGHT,
                    TouchMode.RESIZE_LEFT,
                    TouchMode.RESIZE_TOP,
                    TouchMode.RESIZE_RIGHT,
                    TouchMode.RESIZE_BOTTOM -> {
                        resizeCropRectByCorner(touchMode, x, y)
                        lastTouchX = x
                        lastTouchY = y
                        invalidate()
                        return true
                    }

                    else -> return false
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (touchMode != TouchMode.NONE) {
                    touchMode = TouchMode.NONE
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                return false
            }

            else -> return false
        }
    }

    // 检查是否触到了某个角点
    private fun findTouchedHandle(x: Float, y: Float): TouchMode {
        if (cropRect.isEmpty) return TouchMode.NONE

        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom
        val centerX = cropRect.centerX()
        val centerY = cropRect.centerY()

        // 先检查四个角
        return when {
            isPointNear(x, y, left, top) -> TouchMode.RESIZE_TOP_LEFT
            isPointNear(x, y, right, top) -> TouchMode.RESIZE_TOP_RIGHT
            isPointNear(x, y, left, bottom) -> TouchMode.RESIZE_BOTTOM_LEFT
            isPointNear(x, y, right, bottom) -> TouchMode.RESIZE_BOTTOM_RIGHT

            // 再检查四条边中点（只在自由模式下开启）
            aspectRatioMode == AspectRatioMode.FREE && isPointNear(x, y, centerX, top) ->
                TouchMode.RESIZE_TOP
            aspectRatioMode == AspectRatioMode.FREE && isPointNear(x, y, centerX, bottom) ->
                TouchMode.RESIZE_BOTTOM
            aspectRatioMode == AspectRatioMode.FREE && isPointNear(x, y, left, centerY) ->
                TouchMode.RESIZE_LEFT
            aspectRatioMode == AspectRatioMode.FREE && isPointNear(x, y, right, centerY) ->
                TouchMode.RESIZE_RIGHT

            else -> TouchMode.NONE
        }
    }

    private fun isPointNear(x: Float, y: Float, px: Float, py: Float): Boolean {
        return hypot(x - px, y - py) <= handleRadius
    }


    // 将裁剪框整体平移 dx, dy，并保证最终结果不会超出 View 边界
    private fun offsetCropRect(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return

        var newDx = dx
        var newDy = dy

        // 预计移动后的位置
        val newLeft = cropRect.left + dx
        val newRight = cropRect.right + dx
        val newTop = cropRect.top + dy
        val newBottom = cropRect.bottom + dy

        // 水平边界
        if (newLeft < 0f) {
            newDx = -cropRect.left // 直接贴到左边
        } else if (newRight > width.toFloat()) {
            newDx = width.toFloat() - cropRect.right // 贴到右边
        }

        // 垂直边界
        if (newTop < 0f) {
            newDy = -cropRect.top // 贴到上边
        } else if (newBottom > height.toFloat()) {
            newDy = height.toFloat() - cropRect.bottom // 贴到下边
        }

        // 真正平移
        cropRect.offset(newDx, newDy)
        updateClipPath()
    }

    // 根据拖动 corner 来缩放裁剪框
    // FREE 模式：边只改一个方向，角改双方向
    // 固定比例模式下 根据 fixedAspectRatio 保持宽高比。
    private fun resizeCropRectByCorner(mode: TouchMode, touchX: Float, touchY: Float) {
        if (cropRect.isEmpty) return

        val minW = minCropWidth
        val minH = minCropHeight

        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom

        var newLeft = left
        var newTop = top
        var newRight = right
        var newBottom = bottom

        val ratio = fixedAspectRatio

        if (ratio == null || aspectRatioMode == AspectRatioMode.FREE) {
            // 自由比例：宽高独立变化
            when (mode) {
                // 角：两个方向一起改
                TouchMode.RESIZE_TOP_LEFT -> {
                    newLeft = touchX.coerceIn(0f, right - minW)
                    newTop = touchY.coerceIn(0f, bottom - minH)
                }
                TouchMode.RESIZE_TOP_RIGHT -> {
                    newRight = touchX.coerceIn(left + minW, width.toFloat())
                    newTop = touchY.coerceIn(0f, bottom - minH)
                }
                TouchMode.RESIZE_BOTTOM_LEFT -> {
                    newLeft = touchX.coerceIn(0f, right - minW)
                    newBottom = touchY.coerceIn(top + minH, height.toFloat())
                }
                TouchMode.RESIZE_BOTTOM_RIGHT -> {
                    newRight = touchX.coerceIn(left + minW, width.toFloat())
                    newBottom = touchY.coerceIn(top + minH, height.toFloat())
                }

                // 边：只改一个轴方向
                TouchMode.RESIZE_LEFT -> {
                    newLeft = touchX.coerceIn(0f, right - minW)
                }
                TouchMode.RESIZE_RIGHT -> {
                    newRight = touchX.coerceIn(left + minW, width.toFloat())
                }
                TouchMode.RESIZE_TOP -> {
                    newTop = touchY.coerceIn(0f, bottom - minH)
                }
                TouchMode.RESIZE_BOTTOM -> {
                    newBottom = touchY.coerceIn(top + minH, height.toFloat())
                }

                else -> {}
            }
        } else {
            // 固定宽高比：width / height = ratio 只角生效(边不会进入到这里)
            when (mode) {
                TouchMode.RESIZE_TOP_LEFT -> {
                    // 以 bottom-right 为锚点，根据手指位置决定高度，再算宽度
                    val maxBottom = bottom
                    val maxRight = right

                    // 新高度：从 bottom 到 touchY，限制范围
                    var newH = (maxBottom - touchY).coerceIn(minH, height.toFloat())
                    var newW = newH * ratio

                    // 如果宽度太大超边界，则以宽为准重新算高
                    if (maxRight - newW < 0f) {
                        newW = (maxRight).coerceIn(minW, width.toFloat())
                        newH = newW / ratio
                    }

                    newLeft = maxRight - newW
                    newTop = maxBottom - newH

                    // 再做一次边界限制
                    if (newLeft < 0f) {
                        val diff = -newLeft
                        newLeft = 0f
                        newRight = maxRight - diff
                    } else {
                        newRight = maxRight
                    }
                    if (newTop < 0f) {
                        val diff = -newTop
                        newTop = 0f
                        newBottom = maxBottom - diff
                    } else {
                        newBottom = maxBottom
                    }
                }

                TouchMode.RESIZE_TOP_RIGHT -> {
                    // 以 bottom-left 为锚点
                    val maxBottom = bottom
                    val minLeft = left

                    var newH = (maxBottom - touchY).coerceIn(minH, height.toFloat())
                    var newW = newH * ratio

                    if (minLeft + newW > width.toFloat()) {
                        newW = (width.toFloat() - minLeft).coerceIn(minW, width.toFloat())
                        newH = newW / ratio
                    }

                    newRight = minLeft + newW
                    newTop = maxBottom - newH

                    if (newRight > width.toFloat()) {
                        val diff = newRight - width.toFloat()
                        newRight = width.toFloat()
                        newLeft = minLeft + diff
                    } else {
                        newLeft = minLeft
                    }
                    if (newTop < 0f) {
                        val diff = -newTop
                        newTop = 0f
                        newBottom = maxBottom - diff
                    } else {
                        newBottom = maxBottom
                    }
                }

                TouchMode.RESIZE_BOTTOM_LEFT -> {
                    // 以 top-right 为锚点
                    val minTop = top
                    val maxRight = right

                    var newH = (touchY - minTop).coerceIn(minH, height.toFloat())
                    var newW = newH * ratio

                    if (maxRight - newW < 0f) {
                        newW = (maxRight).coerceIn(minW, width.toFloat())
                        newH = newW / ratio
                    }

                    newLeft = maxRight - newW
                    newBottom = minTop + newH

                    if (newLeft < 0f) {
                        val diff = -newLeft
                        newLeft = 0f
                        newRight = maxRight - diff
                    } else {
                        newRight = maxRight
                    }
                    if (newBottom > height.toFloat()) {
                        val diff = newBottom - height.toFloat()
                        newBottom = height.toFloat()
                        newTop = minTop + diff
                    } else {
                        newTop = minTop
                    }
                }

                TouchMode.RESIZE_BOTTOM_RIGHT -> {
                    // 以 top-left 为锚点
                    val minTop = top
                    val minLeft = left

                    var newH = (touchY - minTop).coerceIn(minH, height.toFloat())
                    var newW = newH * ratio

                    if (minLeft + newW > width.toFloat()) {
                        newW = (width.toFloat() - minLeft).coerceIn(minW, width.toFloat())
                        newH = newW / ratio
                    }

                    newRight = minLeft + newW
                    newBottom = minTop + newH

                    if (newRight > width.toFloat()) {
                        val diff = newRight - width.toFloat()
                        newRight = width.toFloat()
                        newLeft = minLeft + diff
                    } else {
                        newLeft = minLeft
                    }
                    if (newBottom > height.toFloat()) {
                        val diff = newBottom - height.toFloat()
                        newBottom = height.toFloat()
                        newTop = minTop + diff
                    } else {
                        newTop = minTop
                    }
                }

                else -> {}
            }
        }

        // 最终统一做一次最小尺寸与边界的保护
        val finalWidth = max(newRight - newLeft, minW)
        val finalHeight = max(newBottom - newTop, minH)

        // 如果由于上面的限制导致左/上越界，要再矫正一下
        var correctedLeft = newLeft
        var correctedTop = newTop
        var correctedRight = correctedLeft + finalWidth
        var correctedBottom = correctedTop + finalHeight

        if (correctedLeft < 0f) {
            correctedLeft = 0f
            correctedRight = correctedLeft + finalWidth
        }
        if (correctedTop < 0f) {
            correctedTop = 0f
            correctedBottom = correctedTop + finalHeight
        }
        if (correctedRight > width.toFloat()) {
            correctedRight = width.toFloat()
            correctedLeft = correctedRight - finalWidth
        }
        if (correctedBottom > height.toFloat()) {
            correctedBottom = height.toFloat()
            correctedTop = correctedBottom - finalHeight
        }

        cropRect.set(correctedLeft, correctedTop, correctedRight, correctedBottom)
        updateClipPath()
    }

    private fun dp2px(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    // 剪裁逻辑占位
    fun getCropRect(): RectF = RectF(cropRect)
}