package com.owenzx.lightedit.ui.editor.text

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ====== 文本状态快照，用于“取消文字模式”恢复 ======
    data class TextStateSnapshot(
        val elementsCopy: List<TextElement>,
        val selectedIndex: Int
    )

    // ========== 对外状态 & 回调 ==========

    // 是否处于“文字工具激活”状态：
    // - EditorMode.TEXT 或 正在文字内容编辑 时应为 true
    // - NORMAL / CROP / ROTATE / ADJUST 时为 false
    // 为 true 时，所有触摸事件都由 TextOverlayView 处理，不再传到底图。
    var isTextToolActive: Boolean = false

    // 双击某个文本框时回调（交给 Fragment 弹出文字输入面板）
    var onTextBoxDoubleTap: (() -> Unit)? = null

    // 点击选中文字框的右下角 + 按钮时回调（交给 Fragment 再创建一个文本框）
    var onRequestNewTextBox: (() -> Unit)? = null

    // ========== 内部数据结构 ==========

    private val elements = mutableListOf<TextElement>()
    private var selectedIndex: Int = -1

    // 普通模式下，用来判断 DOWN 是否命中了某个文本框
    private var downHitElementIndex: Int = -1

    // ========== 画笔 ==========

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }

    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val plusBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
    }

    // ========== 手势识别：点击 / 双击（文字模式 & 普通模式共用） ==========

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y

                val current = getSelectedElement()
                if (current != null) {
                    // 先检查左下角 +（新增文本）
                    if (current.plusBounds.contains(x, y)) {
                        onRequestNewTextBox?.invoke()
                        return true
                    }
                    // 再检查右上角删除
                    if (current.deleteBounds.contains(x, y)) {
                        val index = selectedIndex
                        if (index in elements.indices) {
                            elements.removeAt(index)
                            // 删除后重新选择一个（如果还有的话）
                            selectedIndex = (elements.lastIndex).coerceAtLeast(-1)
                            invalidate()
                        }
                        return true
                    }
                }

                // 命中某个文本框 -> 切换选中
                val tappedIndex = findElementUnderPoint(x, y)
                if (tappedIndex != -1) {
                    selectedIndex = tappedIndex
                    invalidate()
                    return true
                }

                // 否则取消选中
                selectedIndex = -1
                invalidate()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                val tappedIndex = findElementUnderPoint(x, y)
                if (tappedIndex != -1) {
                    selectedIndex = tappedIndex
                    invalidate()
                    onTextBoxDoubleTap?.invoke()
                    return true
                }
                return false
            }
        })

    // ========== 拖动 / 缩放 / 旋转 手势相关字段 ==========

    private var isDragging = false
    private var isScalingRotating = false

    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = -1

    private var initialDist = 0f
    private var initialAngle = 0f
    private var initialScale = 1f
    private var initialRotation = 0f

    // 当前缩放/旋转是否来自“右下角手柄”
    private var useHandleForScaleRotate = false


    // 导出/保存时使用：为 true 时不画选中边框和角按钮
    var suppressSelectionDrawing: Boolean = false

    // ========== 工具函数 ==========

    private fun sp2px(sp: Float): Float =
        sp * resources.displayMetrics.scaledDensity

    fun hasAnyElement(): Boolean = elements.isNotEmpty()

    fun getSelectedElement(): TextElement? =
        if (selectedIndex in elements.indices) elements[selectedIndex] else null

    // 如果没有选中任何元素，但列表非空，则自动选中最后一个；返回选中的元素
    fun ensureOneSelected(): TextElement? {
        if (selectedIndex !in elements.indices && elements.isNotEmpty()) {
            selectedIndex = elements.lastIndex
            invalidate()
        }
        return getSelectedElement()
    }

    fun createNewTextElement(defaultText: String = "双击编辑文字") {
        if (width == 0 || height == 0) {
            post { createNewTextElement(defaultText) }
            return
        }

        val yOffset = elements.size * 40f * resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f + yOffset.coerceAtMost(height / 3f)

        val element = TextElement(
            text = defaultText,
            centerX = cx,
            centerY = cy,
            textSizeSp = 18f
        )
        elements.add(element)
        selectedIndex = elements.lastIndex
        invalidate()
    }

    fun addTextAtCenter(text: String) {
        if (!hasAnyElement()) {
            createNewTextElement(text)
        } else {
            ensureOneSelected()
            updateSelectedTextContent(text)
        }
    }

    fun updateSelectedTextContent(newText: String) {
        val element = getSelectedElement() ?: return
        element.text = newText
        invalidate()
    }

    // 命中检测：找出点 (x, y) 命中的哪一个文本框（从顶层开始）
    private fun findElementUnderPoint(x: Float, y: Float): Int {
        for (i in elements.indices.reversed()) {
            val e = elements[i]
            if (e.bounds.contains(x, y)) {
                return i
            }
        }
        return -1
    }

    // ========== 样式调节接口（字体 / 字号 / 颜色 / 透明度） ==========

    // 字体：0 默认，1 Serif，2 Monospace
    fun setSelectedTypeface(index: Int) {
        val element = getSelectedElement() ?: return
        element.typefaceIndex = index.coerceIn(0, 2)
        invalidate()
    }

    // 字号：以 sp 为单位，限定在 [12, 36]
    fun setSelectedTextSize(sp: Float) {
        val element = getSelectedElement() ?: return
        element.textSizeSp = sp.coerceIn(12f, 36f)
        invalidate()
    }

    // 颜色：直接传入 ARGB Int
    fun setSelectedTextColor(colorInt: Int) {
        val element = getSelectedElement() ?: return
        element.textColor = colorInt
        invalidate()
    }

    // 透明度：传入百分比 [50, 100]，内部转换为 [128, 255]
    fun setSelectedTextAlpha(percent: Int) {
        val element = getSelectedElement() ?: return
        val p = percent.coerceIn(50, 100)
        val alpha = ((p / 100f) * 255f).roundToInt().coerceIn(128, 255)
        element.alpha = alpha
        invalidate()
    }

    // 清除当前选中的文本框，退出后不再显示虚线框
    fun clearSelection() {
        selectedIndex = -1
        invalidate()
    }

    // ========== 状态快照：用于 EditorFragment 的取消恢复 ==========

    // 生成当前文字状态的快照（深拷贝），用于取消时恢复
    fun snapshotState(): TextStateSnapshot {
        val copiedList = elements.map { e ->
            TextElement(
                text = e.text,
                centerX = e.centerX,
                centerY = e.centerY,
                textSizeSp = e.textSizeSp,
                textColor = e.textColor,
                alpha = e.alpha,
                typefaceIndex = e.typefaceIndex,
                scale = e.scale,
                rotation = e.rotation
            )
        }
        return TextStateSnapshot(
            elementsCopy = copiedList,
            selectedIndex = selectedIndex
        )
    }

    // 从快照恢复文字状态（用于文字模式“取消”）
    fun restoreState(snapshot: TextStateSnapshot) {
        elements.clear()
        snapshot.elementsCopy.forEach { e ->
            elements.add(
                TextElement(
                    text = e.text,
                    centerX = e.centerX,
                    centerY = e.centerY,
                    textSizeSp = e.textSizeSp,
                    textColor = e.textColor,
                    alpha = e.alpha,
                    typefaceIndex = e.typefaceIndex,
                    scale = e.scale,
                    rotation = e.rotation
                )
            )
        }
        selectedIndex = snapshot.selectedIndex.coerceIn(-1, elements.lastIndex)
        invalidate()
    }

    // ========== 绘制 ==========

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (elements.isEmpty()) return

        val drawSelection = !suppressSelectionDrawing
        for ((index, element) in elements.withIndex()) {
            val isSelected = drawSelection && index == selectedIndex
            drawSingleElement(canvas, element, isSelected)
        }
    }

    private fun drawSingleElement(canvas: Canvas, element: TextElement, isSelected: Boolean) {
        if (element.text.isEmpty()) return

        // 文字画笔配置
        textPaint.textSize = sp2px(element.textSizeSp)
        textPaint.color = element.textColor
        textPaint.alpha = element.alpha
        textPaint.typeface = when (element.typefaceIndex) {
            1 -> Typeface.SERIF
            2 -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }

        val lines = element.text.split("\n")
        val lineHeight = textPaint.fontSpacing

        var maxLineWidth = 0f
        for (line in lines) {
            val w = textPaint.measureText(if (line.isEmpty()) " " else line)
            if (w > maxLineWidth) maxLineWidth = w
        }

        val totalHeight = lineHeight * lines.size

        // 局部坐标系下的半宽半高（还没乘 scale，scale 交给 Matrix 处理）
        val baseHalfW = maxLineWidth / 2f + 24f
        val baseHalfH = totalHeight / 2f + 16f

        // 先画文字（带缩放 + 旋转）
        canvas.save()
        canvas.translate(element.centerX, element.centerY)
        canvas.rotate(element.rotation)
        canvas.scale(element.scale, element.scale)

        val startY = -totalHeight / 2f + lineHeight * 0.8f
        lines.forEachIndexed { index, line ->
            val y = startY + index * lineHeight
            canvas.drawText(line, 0f, y, textPaint)
        }

        canvas.restore()

        // 不管是否选中，都计算当前元素的外接矩形（给命中检测用）
        val localToScreen = Matrix().apply {
            // 顺序：先 scale，再 rotate，再平移到 center
            postScale(element.scale, element.scale)
            postRotate(element.rotation)
            postTranslate(element.centerX, element.centerY)
        }

        // 局部坐标系下的边框矩形
        val boundsLocal = RectF(
            -baseHalfW,
            -baseHalfH,
            baseHalfW,
            baseHalfH
        )

        // 映射到屏幕坐标，更新 element.bounds（供 findElementUnderPoint 使用）
        mapRectWithMatrix(localToScreen, boundsLocal, element.bounds)

        if (isSelected) {
            // 选中时：画旋转后的虚线框 + 三个角按钮

            // 在局部坐标系里画，再通过 matrix 映射到屏幕
            canvas.save()
            canvas.concat(localToScreen)

            val handleSizeLocal = 36f
            val rx = 12f

            // 左下角：新增文本 + （局部坐标）
            val plusLocal = RectF(
                boundsLocal.left,
                boundsLocal.bottom - handleSizeLocal,
                boundsLocal.left + handleSizeLocal,
                boundsLocal.bottom
            )

            // 右下角：旋转/缩放手柄（局部坐标）
            val rotateLocal = RectF(
                boundsLocal.right - handleSizeLocal,
                boundsLocal.bottom - handleSizeLocal,
                boundsLocal.right,
                boundsLocal.bottom
            )

            // 右上角：删除按钮（局部坐标）
            val deleteLocal = RectF(
                boundsLocal.right - handleSizeLocal,
                boundsLocal.top,
                boundsLocal.right,
                boundsLocal.top + handleSizeLocal
            )

            // 3.1 虚线外框
            canvas.drawRect(boundsLocal, borderPaint)

            // 3.2 左下角 + 按钮
            run {
                val r = plusLocal
                canvas.drawRoundRect(r, rx, rx, plusBackgroundPaint)
                val cx = r.centerX()
                val cy = r.centerY()
                val halfLen = handleSizeLocal / 4f
                canvas.drawLine(cx - halfLen, cy, cx + halfLen, cy, plusPaint)
                canvas.drawLine(cx, cy - halfLen, cx, cy + halfLen, plusPaint)
            }

            // 3.3 右下角 旋转/缩放手柄
            run {
                val r = rotateLocal
                canvas.drawRoundRect(r, rx, rx, plusBackgroundPaint)
                val cx = r.centerX()
                val cy = r.centerY()
                val radius = handleSizeLocal / 3f
                canvas.drawCircle(cx, cy, radius, plusPaint)
                canvas.drawLine(
                    cx, cy - radius,
                    cx + radius * 0.7f, cy + radius * 0.7f,
                    plusPaint
                )
            }

            // 3.4 右上角 删除按钮
            run {
                val r = deleteLocal
                canvas.drawRoundRect(r, rx, rx, plusBackgroundPaint)
                val cx = r.centerX()
                val cy = r.centerY()
                val half = handleSizeLocal / 4f
                canvas.drawLine(cx - half, cy - half, cx + half, cy + half, plusPaint)
                canvas.drawLine(cx - half, cy + half, cx + half, cy - half, plusPaint)
            }

            canvas.restore()

            // 用同一个 Matrix，把 3 个小按钮映射到屏幕坐标，更新点击区域
            mapRectWithMatrix(localToScreen, plusLocal, element.plusBounds)
            mapRectWithMatrix(localToScreen, rotateLocal, element.rotateBounds)
            mapRectWithMatrix(localToScreen, deleteLocal, element.deleteBounds)

        } else {
            // 未选中：不画框、不画按钮，但保留 bounds（供命中检测）
            // 只清空三个按钮的点击区域
            element.plusBounds.setEmpty()
            element.rotateBounds.setEmpty()
            element.deleteBounds.setEmpty()
        }
    }


    // 将局部坐标系中的矩形，通过 matrix 映射到屏幕坐标，得到轴对齐外接框
    private fun mapRectWithMatrix(matrix: Matrix, src: RectF, out: RectF) {
        val pts = floatArrayOf(
            src.left, src.top,
            src.right, src.top,
            src.right, src.bottom,
            src.left, src.bottom
        )
        matrix.mapPoints(pts)

        var minX = pts[0]
        var maxX = pts[0]
        var minY = pts[1]
        var maxY = pts[1]
        for (i in 2 until 8 step 2) {
            val x = pts[i]
            val y = pts[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        out.set(minX, minY, maxX, maxY)
    }


    // ========== 触摸事件：状态机（普通模式 vs 文字模式） ==========

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 只有文字工具激活时才处理触摸；否则全部放行给底图
        if (!isTextToolActive) {
            return false
        }

        // 文字模式下：拖动/旋转/缩放 + 点击/双击
        handleTextTransformGesture(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // ========== 文字拖动 / 缩放 / 旋转 手势处理（仅在 isTextToolActive = true 时使用） ==========

    private fun handleTextTransformGesture(event: MotionEvent) {
        val selected = getSelectedElement() ?: return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y

                // 如果点在右下角旋转手柄上 -> 进入“单指旋转/缩放模式”
                if (selected.rotateBounds.contains(x, y)) {
                    isDragging = false
                    isScalingRotating = true
                    useHandleForScaleRotate = true

                    val dx = x - selected.centerX
                    val dy = y - selected.centerY
                    initialDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    initialAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                    initialScale = selected.scale
                    initialRotation = selected.rotation
                } else {
                    // 否则：进入拖动模式
                    isDragging = true
                    isScalingRotating = false
                    useHandleForScaleRotate = false
                    activePointerId = event.getPointerId(0)
                    lastX = x
                    lastY = y
                }
            }

            // 第二根手指按下：如果不是手柄模式，就进入“双指旋转/缩放模式”
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2 && !useHandleForScaleRotate) {
                    isDragging = false
                    isScalingRotating = true

                    val x1 = event.getX(0)
                    val y1 = event.getY(0)
                    val x2 = event.getX(1)
                    val y2 = event.getY(1)

                    initialDist =
                        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                    initialAngle =
                        atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
                    initialScale = selected.scale
                    initialRotation = selected.rotation
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isScalingRotating) {
                    if (useHandleForScaleRotate) {
                        // 右下角手柄单指旋转+缩放
                        val x = event.x
                        val y = event.y

                        val dx = x - selected.centerX
                        val dy = y - selected.centerY
                        val newDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (initialDist > 0f) {
                            val scaleFactor = newDist / initialDist
                            selected.scale =
                                (initialScale * scaleFactor).coerceIn(0.5f, 3.0f)
                        }

                        val newAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        val deltaAngleDeg =
                            ((newAngle - initialAngle) * 180f / Math.PI.toFloat())
                        var newRotation = initialRotation + deltaAngleDeg
                        newRotation = (newRotation % 360f + 360f) % 360f
                        selected.rotation = newRotation

                        invalidate()
                    } else if (event.pointerCount >= 2) {
                        // 双指旋转+缩放（原来的逻辑）
                        val x1 = event.getX(0)
                        val y1 = event.getY(0)
                        val x2 = event.getX(1)
                        val y2 = event.getY(1)

                        val newDist =
                            hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                        if (initialDist > 0f) {
                            val scaleFactor = newDist / initialDist
                            selected.scale =
                                (initialScale * scaleFactor).coerceIn(0.5f, 3.0f)
                        }

                        val newAngle =
                            atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
                        val deltaAngleDeg =
                            ((newAngle - initialAngle) * 180f / Math.PI.toFloat())
                        var newRotation = initialRotation + deltaAngleDeg
                        newRotation = (newRotation % 360f + 360f) % 360f
                        selected.rotation = newRotation

                        invalidate()
                    }
                } else if (isDragging) {
                    // 单指拖动
                    val index = event.findPointerIndex(activePointerId)
                    if (index != -1) {
                        val x = event.getX(index)
                        val y = event.getY(index)
                        val dx = x - lastX
                        val dy = y - lastY
                        lastX = x
                        lastY = y

                        selected.centerX += dx
                        selected.centerY += dy
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isScalingRotating = false
                useHandleForScaleRotate = false
                activePointerId = -1
            }
        }
    }

}
