package com.owenzx.lightedit.ui.editor.sticker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class StickerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isStickerToolActive: Boolean = false

    // 和 TextOverlayView 一样，用来在保存/合成时临时关闭选框绘制
    var suppressSelectionDrawing: Boolean = false

    // 贴纸列表（下标越大，层级越靠上）
    private val stickers = mutableListOf<StickerElement>()

    // 当前选中的贴纸下标
    private var selectedIndex: Int = -1

    private val tmpMatrix = Matrix()

    // 选中框画笔（虚线框）——风格和文字一致
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    // 小按钮底色（粉色圈）
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081")
        style = Paint.Style.FILL
    }

    // 小按钮前景（白色图标线）
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * resources.displayMetrics.density
    }

    // 手势状态
    private var isDragging = false
    private var isScalingRotating = false
    private var useSingleFingerScaleRotate = false

    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = -1

    private var initialDist = 0f
    private var initialAngle = 0f
    private var initialScale = 1f
    private var initialRotation = 0f

    // 当前命中的区域：主体 / 删除 / +1 / 缩放
    private enum class HitArea {
        NONE, BODY, DELETE, PLUS, SCALE
    }

    private var currentHitArea: HitArea = HitArea.NONE

    // 单击用：选中贴纸主体
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isStickerToolActive) return false
                val x = e.x
                val y = e.y

                val hitIndex = findStickerUnderPoint(x, y)
                return if (hitIndex != -1) {
                    selectedIndex = hitIndex
                    invalidate()
                    true
                } else {
                    false
                }
            }
        })

    // ===== 对外 API =====

    fun hasAnySticker(): Boolean = stickers.isNotEmpty()

    fun clearSelection() {
        selectedIndex = -1
        invalidate()
    }

    // 添加一张贴纸：贴纸 bitmap 已经在 EditorFragment 里下采样好了
    // 这里根据 View 尺寸算一个合适的初始 scale
    fun addSticker(bitmap: Bitmap) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return

        // View 还没测量好时，延迟一次
        if (width == 0 || height == 0) {
            post { addSticker(bitmap) }
            return
        }

        val cx = width / 2f
        val cy = height / 2f

        // 希望贴纸最大边 ≈ 画布的 1/4 左右
        val targetMaxSize = min(width, height) / 4f
        val maxBitmapEdge = max(bitmap.width, bitmap.height).toFloat()

        val baseScale = if (maxBitmapEdge > 0f) {
            (targetMaxSize / maxBitmapEdge).coerceIn(0.2f, 2.5f)
        } else {
            1f
        }

        val element = StickerElement(
            bitmap = bitmap,
            centerX = cx,
            centerY = cy,
            scale = baseScale,
            rotation = 0f
        )
        stickers.add(element)
        selectedIndex = stickers.lastIndex
        invalidate()
    }

    // 清空当前所有贴纸（本次修改的结果）
    fun clearAllStickers() {
        stickers.clear()
        selectedIndex = -1
        invalidate()
    }


    fun deleteSelected() {
        if (selectedIndex in stickers.indices) {
            stickers.removeAt(selectedIndex)
            selectedIndex = -1
            invalidate()
        }
    }

    fun moveSelectedUpOneLayer() {
        val idx = selectedIndex
        // 不是选中 && 不是最上面一层 就可以往上挪一格
        if (idx in 0 until stickers.lastIndex) {
            val e = stickers.removeAt(idx)
            stickers.add(idx + 1, e)
            selectedIndex = idx + 1
            invalidate()
        }
    }

    fun moveSelectedDownOneLayer() {
        val idx = selectedIndex
        // 不是选中 && 不是最底层 就可以往下挪一格
        if (idx in 1 until stickers.size) {
            val e = stickers.removeAt(idx)
            stickers.add(idx - 1, e)
            selectedIndex = idx - 1
            invalidate()
        }
    }


    fun bringSelectedToFront() {
        val idx = selectedIndex
        if (idx !in stickers.indices) return
        val e = stickers.removeAt(idx)
        stickers.add(e)
        selectedIndex = stickers.lastIndex
        invalidate()
    }

    fun sendSelectedToBack() {
        val idx = selectedIndex
        if (idx !in stickers.indices) return
        val e = stickers.removeAt(idx)
        stickers.add(0, e)
        selectedIndex = 0
        invalidate()
    }

    // 状态快照：用于 EditorFragment 中「取消贴纸」时恢复
    data class StickerStateSnapshot(
        val list: List<StickerElement>,
        val selectedIndex: Int
    )

    fun snapshotState(): StickerStateSnapshot {
        val copyList = stickers.map { s ->
            StickerElement(
                bitmap = s.bitmap, // 共用 bitmap 即可
                centerX = s.centerX,
                centerY = s.centerY,
                scale = s.scale,
                rotation = s.rotation
            )
        }
        return StickerStateSnapshot(copyList, selectedIndex)
    }

    fun restoreState(snapshot: StickerStateSnapshot) {
        stickers.clear()
        stickers.addAll(snapshot.list)
        selectedIndex = snapshot.selectedIndex
        invalidate()
    }

    // ===== 绘制 =====

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for ((index, sticker) in stickers.withIndex()) {
            val bmp = sticker.bitmap
            if (bmp.isRecycled) continue

            val halfW = bmp.width / 2f
            val halfH = bmp.height / 2f

            // Matrix：移到原点 -> 缩放 -> 旋转 -> 平移到 centerX/Y
            tmpMatrix.reset()
            tmpMatrix.postTranslate(-halfW, -halfH)
            tmpMatrix.postScale(sticker.scale, sticker.scale)
            tmpMatrix.postRotate(sticker.rotation)
            tmpMatrix.postTranslate(sticker.centerX, sticker.centerY)

            // 1. 画贴纸
            canvas.drawBitmap(bmp, tmpMatrix, null)

            // 2. 计算旋转后的四个角点（注意：这里要用 Bitmap 自己的坐标 0~w,0~h）
            val pts = sticker.cornerPoints
            // 未变换的四角：TL, TR, BR, BL，和 Bitmap 一样用 (0,0)~(w,h)
            pts[0] = 0f;                   pts[1] = 0f                   // TL
            pts[2] = bmp.width.toFloat();  pts[3] = 0f                   // TR
            pts[4] = bmp.width.toFloat();  pts[5] = bmp.height.toFloat() // BR
            pts[6] = 0f;                   pts[7] = bmp.height.toFloat() // BL
            tmpMatrix.mapPoints(pts)

            // 3. 更新 axis-aligned bounds（命中检测用）
            val minX = min(min(pts[0], pts[2]), min(pts[4], pts[6]))
            val maxX = max(max(pts[0], pts[2]), max(pts[4], pts[6]))
            val minY = min(min(pts[1], pts[3]), min(pts[5], pts[7]))
            val maxY = max(max(pts[1], pts[3]), max(pts[5], pts[7]))
            sticker.bounds.set(minX, minY, maxX, maxY)

            // 4. 如果选中并且允许画选框，就画框 + 三个角按钮
            if (index == selectedIndex &&
                isStickerToolActive &&
                !suppressSelectionDrawing
            ) {
                drawSelectionForSticker(canvas, sticker)
            }
        }
    }

    private fun drawSelectionForSticker(canvas: Canvas, sticker: StickerElement) {
        val pts = sticker.cornerPoints
        val tlx = pts[0]; val tly = pts[1]
        val trx = pts[2]; val tryY = pts[3]
        val brx = pts[4]; val bry = pts[5]
        val blx = pts[6]; val bly = pts[7]

        // 1. 旋转后的矩形框（和文字一样有虚线）
        val path = Path().apply {
            moveTo(tlx, tly)
            lineTo(trx, tryY)
            lineTo(brx, bry)
            lineTo(blx, bly)
            close()
        }
        canvas.drawPath(path, borderPaint)

        val radius = 12f * resources.displayMetrics.density
        val handleRadius = radius
        val handleSize = radius * 2.2f

        // 2. 右上角：删除按钮（X）
        val delCx = trx
        val delCy = tryY
        sticker.deleteBounds.set(
            delCx - handleSize, delCy - handleSize,
            delCx + handleSize, delCy + handleSize
        )
        canvas.drawCircle(delCx, delCy, handleRadius, handleFillPaint)
        canvas.drawLine(
            delCx - radius / 2, delCy - radius / 2,
            delCx + radius / 2, delCy + radius / 2,
            handleStrokePaint
        )
        canvas.drawLine(
            delCx - radius / 2, delCy + radius / 2,
            delCx + radius / 2, delCy - radius / 2,
            handleStrokePaint
        )

        // 3. 左下角：+1（复制）
        val plusCx = blx
        val plusCy = bly
        sticker.plusBounds.set(
            plusCx - handleSize, plusCy - handleSize,
            plusCx + handleSize, plusCy + handleSize
        )
        canvas.drawCircle(plusCx, plusCy, handleRadius, handleFillPaint)
        // "+"
        canvas.drawLine(
            plusCx - radius / 2, plusCy,
            plusCx + radius / 2, plusCy,
            handleStrokePaint
        )
        canvas.drawLine(
            plusCx, plusCy - radius / 2,
            plusCx, plusCy + radius / 2,
            handleStrokePaint
        )

        // 4. 右下角：缩放 + 旋转
        val scaleCx = brx
        val scaleCy = bry
        sticker.scaleBounds.set(
            scaleCx - handleSize, scaleCy - handleSize,
            scaleCx + handleSize, scaleCy + handleSize
        )
        canvas.drawCircle(scaleCx, scaleCy, handleRadius, handleFillPaint)
        // 画一条斜线表示缩放
        canvas.drawLine(
            scaleCx - radius / 2, scaleCy + radius / 2,
            scaleCx + radius / 2, scaleCy - radius / 2,
            handleStrokePaint
        )
    }

    // ===== 命中检测 =====

    private fun findStickerUnderPoint(x: Float, y: Float): Int {
        for (i in stickers.indices.reversed()) {
            if (stickers[i].bounds.contains(x, y)) return i
        }
        return -1
    }

    private fun hitDeleteHandle(x: Float, y: Float): Int {
        for (i in stickers.indices.reversed()) {
            if (stickers[i].deleteBounds.contains(x, y)) return i
        }
        return -1
    }

    private fun hitPlusHandle(x: Float, y: Float): Int {
        for (i in stickers.indices.reversed()) {
            if (stickers[i].plusBounds.contains(x, y)) return i
        }
        return -1
    }

    private fun hitScaleHandle(x: Float, y: Float): Int {
        for (i in stickers.indices.reversed()) {
            if (stickers[i].scaleBounds.contains(x, y)) return i
        }
        return -1
    }

    // ===== 触摸逻辑：拖动 + 单指缩放旋转 + 双指缩放旋转 =====

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isStickerToolActive) return false

        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y

                // 1. 角按钮优先：删除
                val delIdx = hitDeleteHandle(x, y)
                if (delIdx != -1) {
                    stickers.removeAt(delIdx)
                    if (selectedIndex == delIdx) {
                        selectedIndex = -1
                    } else if (selectedIndex > delIdx) {
                        selectedIndex -= 1
                    }
                    currentHitArea = HitArea.DELETE
                    invalidate()
                    return true
                }

                // 2. +1（复制）
                val plusIdx = hitPlusHandle(x, y)
                if (plusIdx != -1) {
                    val base = stickers[plusIdx]
                    val offset = 24f * resources.displayMetrics.density
                    val copy = StickerElement(
                        bitmap = base.bitmap,
                        centerX = base.centerX + offset,
                        centerY = base.centerY + offset,
                        scale = base.scale,
                        rotation = base.rotation
                    )
                    stickers.add(copy)
                    selectedIndex = stickers.lastIndex
                    currentHitArea = HitArea.PLUS
                    invalidate()
                    return true
                }

                // 3. 缩放/旋转角：单指缩放+旋转
                val scaleIdx = hitScaleHandle(x, y)
                if (scaleIdx != -1) {
                    selectedIndex = scaleIdx
                    val s = stickers[scaleIdx]

                    val dx = x - s.centerX
                    val dy = y - s.centerY
                    initialDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    initialAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                    initialScale = s.scale
                    initialRotation = s.rotation

                    isScalingRotating = true
                    useSingleFingerScaleRotate = true
                    activePointerId = event.getPointerId(0)
                    currentHitArea = HitArea.SCALE

                    invalidate()
                    return true
                }

                // 4. 点在贴纸主体：开始拖动
                val bodyIdx = findStickerUnderPoint(x, y)
                if (bodyIdx != -1) {
                    selectedIndex = bodyIdx
                    isDragging = true
                    lastX = x
                    lastY = y
                    activePointerId = event.getPointerId(0)
                    currentHitArea = HitArea.BODY
                    invalidate()
                    return true
                }

                currentHitArea = HitArea.NONE
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 双指：缩放 + 旋转
                if (event.pointerCount == 2 && selectedIndex in stickers.indices) {
                    isDragging = false
                    isScalingRotating = true
                    useSingleFingerScaleRotate = false

                    val x1 = event.getX(0)
                    val y1 = event.getY(0)
                    val x2 = event.getX(1)
                    val y2 = event.getY(1)

                    initialDist =
                        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                    initialAngle =
                        atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()

                    val s = stickers[selectedIndex]
                    initialScale = s.scale
                    initialRotation = s.rotation
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedIndex in stickers.indices) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        val dx = x - lastX
                        val dy = y - lastY
                        val s = stickers[selectedIndex]
                        s.centerX += dx
                        s.centerY += dy
                        lastX = x
                        lastY = y
                        invalidate()
                        return true
                    }
                } else if (isScalingRotating && selectedIndex in stickers.indices) {
                    val s = stickers[selectedIndex]

                    if (useSingleFingerScaleRotate) {
                        // 单指：相对 center 的距离变化 => scale；角度变化 => rotation
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex != -1) {
                            val x = event.getX(pointerIndex)
                            val y = event.getY(pointerIndex)

                            val dx = x - s.centerX
                            val dy = y - s.centerY
                            val newDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            if (initialDist > 0f) {
                                val factor = newDist / initialDist
                                s.scale = (initialScale * factor).coerceIn(0.3f, 4f)
                            }

                            val newAngle =
                                atan2(dy.toDouble(), dx.toDouble()).toFloat()
                            val deltaDeg = Math.toDegrees(
                                (newAngle - initialAngle).toDouble()
                            ).toFloat()
                            s.rotation = (initialRotation + deltaDeg) % 360f

                            invalidate()
                            return true
                        }
                    } else if (event.pointerCount >= 2) {
                        // 双指缩放 + 旋转
                        val x1 = event.getX(0)
                        val y1 = event.getY(0)
                        val x2 = event.getX(1)
                        val y2 = event.getY(1)

                        val newDist =
                            hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                        if (initialDist > 0f) {
                            val factor = newDist / initialDist
                            s.scale = (initialScale * factor).coerceIn(0.3f, 4f)
                        }

                        val newAngle =
                            atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
                        val deltaDeg = Math.toDegrees(
                            (newAngle - initialAngle).toDouble()
                        ).toFloat()
                        s.rotation = (initialRotation + deltaDeg) % 360f

                        invalidate()
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isScalingRotating = false
                useSingleFingerScaleRotate = false
                activePointerId = -1
                currentHitArea = HitArea.NONE
            }
        }

        return true
    }
}
