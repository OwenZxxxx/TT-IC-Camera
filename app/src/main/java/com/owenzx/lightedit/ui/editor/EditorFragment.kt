package com.owenzx.lightedit.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.owenzx.lightedit.databinding.FragmentEditorBinding
import com.owenzx.lightedit.ui.editor.crop.AspectRatioMode
import com.owenzx.lightedit.ui.editor.text.TextOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors


class EditorFragment : Fragment() {

    companion object {
        private const val ARG_PHOTO_URI = "arg_photo_uri"

        fun newInstance(uri: Uri): EditorFragment {
            return EditorFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PHOTO_URI, uri)
                }
            }
        }
    }

    // 模式：普通 / 裁剪 / 旋转 / 调色 / 文字
    private enum class EditorMode {
        NORMAL,
        CROP,
        ROTATE,
        ADJUST,
        TEXT
    }

    private var currentMode: EditorMode = EditorMode.NORMAL

    private lateinit var photoUri: Uri

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    // 裁剪模式标记
    private var inCropMode: Boolean = false

    // 旋转/翻转模式标记
    private var inRotateMode: Boolean = false

    // 进入旋转模式时的原图备份，用于“取消”还原
    private var rotateBackupBitmap: Bitmap? = null

    // 调色模式标记
    private var inAdjustMode: Boolean = false

    // 进入调色模式时的原始 Bitmap，用于实时调节和“按住对比”
    private var adjustBackupBitmap: Bitmap? = null

    // 当前亮度 [-100, 100]，默认 0
    private var currentBrightness: Int = 0

    // 当前对比度 [-50, 150]，默认 0
    private var currentContrast: Int = 0

    // 文字模式的状态备份（进入 TEXT 模式时记录，用于取消恢复）
    private var textBackupState: TextOverlayView.TextStateSnapshot? = null

    // 用于保存图片的后台线程池（单线程即可）
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoUri = requireArguments().getParcelable(ARG_PHOTO_URI)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 统一：底部某个 toolbar 从底下弹出来的动画
    private fun showToolbarWithSlideUp(target: View) {
        target.visibility = View.VISIBLE
        target.alpha = 0f
        target.post {
            target.translationY = target.height.toFloat()
            target.alpha = 0f
            target.visibility = View.VISIBLE
            target.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220L)
                .start()
        }
    }

    // 根据模式切换 UI
    private fun updateUiForMode(mode: EditorMode) {
        currentMode = mode

        when (mode) {
            EditorMode.NORMAL -> {
                binding.layoutEditorHeader.visibility = View.VISIBLE

                binding.layoutEditorRoot.setBackgroundColor(Color.WHITE)
                binding.editorCanvasContainer.setBackgroundColor(Color.WHITE)
                binding.layoutBottomPanel.setBackgroundColor(Color.WHITE)

                binding.layoutEditorToolbar.visibility = View.VISIBLE

                binding.layoutCropControls.visibility = View.GONE
                binding.layoutRotateControls.visibility = View.GONE
                binding.layoutAdjustControls.visibility = View.GONE
                binding.layoutTextControls.visibility = View.GONE

                binding.cropOverlayView.visibility = View.GONE

                // 文字：退出可编辑状态，不画虚线框
                binding.viewDimBackground.visibility = View.GONE
                binding.layoutTextEditBar.visibility = View.GONE
                binding.layoutTextStylePanel.visibility = View.GONE
                binding.textOverlayView.isTextToolActive = false
            }

            EditorMode.CROP -> {
                binding.layoutEditorHeader.visibility = View.GONE

                binding.layoutEditorRoot.setBackgroundColor(Color.BLACK)
                binding.editorCanvasContainer.setBackgroundColor(Color.BLACK)
                binding.layoutBottomPanel.setBackgroundColor(Color.BLACK)

                binding.layoutEditorToolbar.visibility = View.GONE

                binding.layoutRotateControls.visibility = View.GONE
                binding.layoutAdjustControls.visibility = View.GONE
                binding.layoutTextControls.visibility = View.GONE

                binding.layoutCropControls.setBackgroundColor(Color.WHITE)
                showToolbarWithSlideUp(binding.layoutCropControls)

                binding.cropOverlayView.visibility = View.VISIBLE

                // 文字工具关闭
                binding.viewDimBackground.visibility = View.GONE
                binding.layoutTextEditBar.visibility = View.GONE
                binding.layoutTextStylePanel.visibility = View.GONE
                binding.textOverlayView.isTextToolActive = false
            }

            EditorMode.ROTATE -> {
                binding.layoutEditorHeader.visibility = View.GONE

                binding.layoutEditorRoot.setBackgroundColor(Color.BLACK)
                binding.editorCanvasContainer.setBackgroundColor(Color.BLACK)
                binding.layoutBottomPanel.setBackgroundColor(Color.BLACK)

                binding.layoutEditorToolbar.visibility = View.GONE
                binding.layoutCropControls.visibility = View.GONE
                binding.layoutAdjustControls.visibility = View.GONE
                binding.layoutTextControls.visibility = View.GONE

                binding.layoutRotateControls.setBackgroundColor(Color.WHITE)
                showToolbarWithSlideUp(binding.layoutRotateControls)

                binding.cropOverlayView.visibility = View.GONE

                binding.viewDimBackground.visibility = View.GONE
                binding.layoutTextEditBar.visibility = View.GONE
                binding.layoutTextStylePanel.visibility = View.GONE
                binding.textOverlayView.isTextToolActive = false
            }

            EditorMode.ADJUST -> {
                binding.layoutEditorHeader.visibility = View.GONE

                binding.layoutEditorRoot.setBackgroundColor(Color.BLACK)
                binding.editorCanvasContainer.setBackgroundColor(Color.BLACK)
                binding.layoutBottomPanel.setBackgroundColor(Color.BLACK)

                binding.layoutEditorToolbar.visibility = View.GONE
                binding.layoutCropControls.visibility = View.GONE
                binding.layoutRotateControls.visibility = View.GONE
                binding.layoutTextControls.visibility = View.GONE

                binding.layoutAdjustControls.setBackgroundColor(Color.WHITE)
                showToolbarWithSlideUp(binding.layoutAdjustControls)

                binding.cropOverlayView.visibility = View.GONE

                binding.viewDimBackground.visibility = View.GONE
                binding.layoutTextEditBar.visibility = View.GONE
                binding.layoutTextStylePanel.visibility = View.GONE
                binding.textOverlayView.isTextToolActive = false
            }

            EditorMode.TEXT -> {
                // 专注文字模式：顶部隐藏，底部是文字工具栏
                binding.layoutEditorHeader.visibility = View.GONE

                binding.layoutEditorRoot.setBackgroundColor(Color.BLACK)
                binding.editorCanvasContainer.setBackgroundColor(Color.BLACK)
                binding.layoutBottomPanel.setBackgroundColor(Color.BLACK)

                binding.layoutEditorToolbar.visibility = View.GONE
                binding.layoutCropControls.visibility = View.GONE
                binding.layoutRotateControls.visibility = View.GONE
                binding.layoutAdjustControls.visibility = View.GONE

                // 先设成白底，然后用统一的卡片上滑动画
                binding.layoutTextControls.setBackgroundColor(Color.WHITE)
                showToolbarWithSlideUp(binding.layoutTextControls)

                binding.cropOverlayView.visibility = View.GONE

                // 进入 TEXT 模式：文字可编辑（显示虚线框、支持拖拽缩放旋转）
                binding.viewDimBackground.visibility = View.GONE
                binding.layoutTextEditBar.visibility = View.GONE
                binding.layoutTextStylePanel.visibility = View.VISIBLE

                // 激活文字工具
                binding.textOverlayView.isTextToolActive = true
            }
        }
    }

    // 接 UI + 展示图片
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvEditorTitle.text = "编辑图片"

        // 显示图片（首次允许自动适配）
        binding.imageEditCanvas.setAutoInitFitEnabled(true)
        binding.imageEditCanvas.setImageURI(photoUri)
        binding.imageEditCanvas.post {
            binding.imageEditCanvas.setAutoInitFitEnabled(false)
        }

        binding.layoutEditorToolbar.setBackgroundColor(Color.WHITE)
        binding.layoutCropControls.setBackgroundColor(Color.WHITE)
        binding.layoutRotateControls.setBackgroundColor(Color.WHITE)
        binding.layoutAdjustControls.setBackgroundColor(Color.WHITE)

        // 顶部返回
        binding.btnEditorBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 顶部保存：合成当前画面 + 水印，然后写入系统相册
        binding.btnEditorSave.setOnClickListener {
            // 先在当前线程合成带“训练营”水印的 Bitmap
            val composed = composeCurrentImageWithWatermark()
            if (composed == null) {
                Toast.makeText(requireContext(), "当前画面尚未准备好，稍后再试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 再把保存操作丢到线程池执行
            saveBitmapToGallery(composed) { success ->
                if (success) {
                    Toast.makeText(
                        requireContext(),
                        "保存成功，图片已保存到相册",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "保存失败，可能是存储空间不足或无写入权限",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // 普通工具栏：裁剪
        binding.btnToolCrop.setOnClickListener {
            enterCropMode()
        }

        // 普通工具栏：旋转
        binding.btnToolRotate.setOnClickListener {
            enterRotateMode()
        }

        // 普通工具栏：调色
        binding.btnToolAdjust.setOnClickListener {
            enterAdjustMode()
        }

        // 普通工具栏：文字
        binding.btnToolText.setOnClickListener {
            // 如果有其它模式，先收尾
            if (inCropMode) exitCropMode()
            if (inRotateMode) exitRotateMode(applyChanges = true)
            if (inAdjustMode) exitAdjustMode(applyChanges = true)

            // 备份当前文字状态
            textBackupState = binding.textOverlayView.snapshotState()

            // 切到 TEXT 模式（带卡片动画）
            updateUiForMode(EditorMode.TEXT)

            // 如果还没有任何文字框：创建一个默认文本框
            if (!binding.textOverlayView.hasAnyElement()) {
                binding.textOverlayView.createNewTextElement("双击编辑文字")
            } else {
                // 已经有文字框了：不要再创建，只是确保有一个被选中
                binding.textOverlayView.ensureOneSelected()
            }
        }

        // 裁剪：取消 / 确认
        binding.btnCropCancel.setOnClickListener {
            exitCropMode()
        }
        binding.btnCropConfirm.setOnClickListener {
            val cropRectInView = binding.cropOverlayView.getCropRect()
            val croppedBitmap = binding.imageEditCanvas.getCroppedBitmap(cropRectInView)

            if (croppedBitmap != null) {
                binding.imageEditCanvas.setAutoInitFitEnabled(true)
                binding.imageEditCanvas.setImageBitmap(croppedBitmap)
                binding.imageEditCanvas.setAutoInitFitEnabled(false)
                exitCropMode()
            } else {
                Toast.makeText(requireContext(), "裁剪失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }

        // 裁剪比例
        binding.btnRatioFree.setOnClickListener {
            binding.cropOverlayView.setAspectRatio(AspectRatioMode.FREE)
        }
        binding.btnRatio11.setOnClickListener {
            binding.cropOverlayView.setAspectRatio(AspectRatioMode.RATIO_1_1)
        }
        binding.btnRatio43.setOnClickListener {
            binding.cropOverlayView.setAspectRatio(AspectRatioMode.RATIO_4_3)
        }
        binding.btnRatio169.setOnClickListener {
            binding.cropOverlayView.setAspectRatio(AspectRatioMode.RATIO_16_9)
        }
        binding.btnRatio34.setOnClickListener {
            binding.cropOverlayView.setAspectRatio(AspectRatioMode.RATIO_3_4)
        }
        binding.btnRatio916.setOnClickListener {
            binding.cropOverlayView.setAspectRatio(AspectRatioMode.RATIO_9_16)
        }

        // 旋转 / 翻转
        binding.btnRotateLeft90.setOnClickListener {
            applyRotate(-90f)
        }
        binding.btnRotateRight90.setOnClickListener {
            applyRotate(90f)
        }
        binding.btnRotate180.setOnClickListener {
            applyRotate(180f)
        }
        binding.btnFlipHorizontal.setOnClickListener {
            applyFlip(horizontal = true)
        }
        binding.btnFlipVertical.setOnClickListener {
            applyFlip(horizontal = false)
        }

        // 旋转：取消 / 确认
        binding.btnRotateCancel.setOnClickListener {
            exitRotateMode(applyChanges = false)
        }
        binding.btnRotateConfirm.setOnClickListener {
            exitRotateMode(applyChanges = true)
        }

        // 亮度滑条：[-100, 100]
        binding.seekBrightness.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (!inAdjustMode || !fromUser) return
                currentBrightness = progress - 100
                binding.tvBrightnessValue.text = currentBrightness.toString()
                applyAdjustPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 对比度滑条：[-50, 150]
        binding.seekContrast.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (!inAdjustMode || !fromUser) return
                currentContrast = progress - 50
                binding.tvContrastValue.text = currentContrast.toString()
                applyAdjustPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 调色快捷
        binding.btnAdjustReset.setOnClickListener {
            if (!inAdjustMode) return@setOnClickListener
            currentBrightness = 0
            currentContrast = 0
            binding.seekBrightness.progress = 100
            binding.seekContrast.progress = 50
            binding.tvBrightnessValue.text = "0"
            binding.tvContrastValue.text = "0"
            applyAdjustPreview()
        }
        binding.btnBrightnessMinus20.setOnClickListener {
            if (!inAdjustMode) return@setOnClickListener
            currentBrightness = (currentBrightness - 20).coerceIn(-100, 100)
            binding.seekBrightness.progress = currentBrightness + 100
            binding.tvBrightnessValue.text = currentBrightness.toString()
            applyAdjustPreview()
        }
        binding.btnBrightnessPlus20.setOnClickListener {
            if (!inAdjustMode) return@setOnClickListener
            currentBrightness = (currentBrightness + 20).coerceIn(-100, 100)
            binding.seekBrightness.progress = currentBrightness + 100
            binding.tvBrightnessValue.text = currentBrightness.toString()
            applyAdjustPreview()
        }
        binding.btnContrastMinus20.setOnClickListener {
            if (!inAdjustMode) return@setOnClickListener
            currentContrast = (currentContrast - 20).coerceIn(-50, 150)
            binding.seekContrast.progress = currentContrast + 50
            binding.tvContrastValue.text = currentContrast.toString()
            applyAdjustPreview()
        }
        binding.btnContrastPlus20.setOnClickListener {
            if (!inAdjustMode) return@setOnClickListener
            currentContrast = (currentContrast + 20).coerceIn(-50, 150)
            binding.seekContrast.progress = currentContrast + 50
            binding.tvContrastValue.text = currentContrast.toString()
            applyAdjustPreview()
        }

        // 调色：取消 / 确认
        binding.btnAdjustCancel.setOnClickListener {
            exitAdjustMode(applyChanges = false)
        }
        binding.btnAdjustConfirm.setOnClickListener {
            exitAdjustMode(applyChanges = true)
        }

        // 按住对比原图
        binding.btnAdjustCompare.setOnTouchListener { _, event ->
            if (!inAdjustMode || adjustBackupBitmap == null) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    applyAdjustPreview(brightnessOverride = 0, contrastOverride = 0)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    applyAdjustPreview()
                    true
                }

                else -> false
            }
        }

        // 文本交互：双击文字框 -> 进入内容编辑
        binding.textOverlayView.onTextBoxDoubleTap = {
            enterTextEditModeForSelected()
        }

        // 文本交互：右下角 + -> 新增文本框
        binding.textOverlayView.onRequestNewTextBox = {
            binding.textOverlayView.createNewTextElement("双击编辑文字")
        }

        // 文本内容编辑：清空
        binding.btnTextClear.setOnClickListener {
            binding.etTextInput.setText("")
        }

        // 文本内容编辑：关闭（取消本次内容修改，回到文字样式面板）
        binding.btnTextClose.setOnClickListener {
            exitTextContentEdit()
        }

        // 文本内容编辑：确认（更新文字内容，回到文字样式面板）
        binding.btnTextConfirm.setOnClickListener {
            val text = binding.etTextInput.text.toString()
            val trimmed = text.trim()
            if (trimmed.isNotEmpty()) {
                binding.textOverlayView.updateSelectedTextContent(trimmed)
            }
            exitTextContentEdit()
        }

        // 字体：3 种
        binding.btnFontDefault.setOnClickListener {
            binding.textOverlayView.setSelectedTypeface(0)
        }
        binding.btnFontSerif.setOnClickListener {
            binding.textOverlayView.setSelectedTypeface(1)
        }
        binding.btnFontMono.setOnClickListener {
            binding.textOverlayView.setSelectedTypeface(2)
        }

        // 字号：12 ~ 36
        binding.seekTextSize.max = 36 - 12  // 0 ~ 24
        binding.seekTextSize.progress = 18 - 12
        binding.tvTextSizeValue.text = "18"
        binding.seekTextSize.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeSp = 12 + progress
                binding.tvTextSizeValue.text = sizeSp.toString()
                binding.textOverlayView.setSelectedTextSize(sizeSp.toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 颜色（10 个预设）
        fun View.bindTextColor(colorInt: Int) {
            setOnClickListener {
                binding.textOverlayView.setSelectedTextColor(colorInt)
            }
        }
        binding.btnColor1.bindTextColor(Color.WHITE)
        binding.btnColor2.bindTextColor(Color.BLACK)
        binding.btnColor3.bindTextColor(0xFFFF0000.toInt())
        binding.btnColor4.bindTextColor(0xFF00FF00.toInt())
        binding.btnColor5.bindTextColor(0xFF0000FF.toInt())
        binding.btnColor6.bindTextColor(0xFFFFFF00.toInt())
        binding.btnColor7.bindTextColor(0xFFFFA500.toInt())
        binding.btnColor8.bindTextColor(0xFF00FFFF.toInt())
        binding.btnColor9.bindTextColor(0xFF800080.toInt())
        binding.btnColor10.bindTextColor(0xFFFFC0CB.toInt())

        // 透明度：50% ~ 100%
        binding.seekTextAlpha.max = 100 - 50   // 0~50
        binding.seekTextAlpha.progress = 100 - 50
        binding.tvTextAlphaValue.text = "100%"
        binding.seekTextAlpha.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = 50 + progress
                binding.tvTextAlphaValue.text = "${percent}%"
                binding.textOverlayView.setSelectedTextAlpha(percent)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 文字模式底部：取消 / 确认（都退出文字模式，回到 NORMAL）
        binding.btnTextModeCancel.setOnClickListener {
            // 有备份就恢复
            textBackupState?.let { snapshot ->
                binding.textOverlayView.restoreState(snapshot)
            }
            textBackupState = null

            // 退出 TEXT 模式（只收 UI，不再改图片）
            exitTextMode()
        }
        binding.btnTextModeConfirm.setOnClickListener {
            // 确认就不恢复 snapshot，直接保留当前文字状态
            textBackupState = null
            exitTextMode()
        }

        // 默认：普通模式（白底）
        updateUiForMode(EditorMode.NORMAL)
    }

    // ---- 模式切换 ----

    private fun enterCropMode() {
        if (inCropMode) return
        inCropMode = true

        if (inRotateMode) exitRotateMode(applyChanges = true)
        if (inAdjustMode) exitAdjustMode(applyChanges = true)

        binding.imageEditCanvas.setCropModeEnabled(true)
        updateUiForMode(EditorMode.CROP)
    }

    private fun exitCropMode() {
        if (!inCropMode) return
        inCropMode = false

        binding.imageEditCanvas.setCropModeEnabled(false)
        updateUiForMode(EditorMode.NORMAL)
    }

    private fun enterRotateMode() {
        if (inRotateMode) return

        if (inCropMode) exitCropMode()
        if (inAdjustMode) exitAdjustMode(applyChanges = true)

        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable
        val currentBitmap = drawable?.bitmap
        if (currentBitmap == null) {
            Toast.makeText(requireContext(), "没有可编辑的图片", Toast.LENGTH_SHORT).show()
            return
        }

        rotateBackupBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        inRotateMode = true

        updateUiForMode(EditorMode.ROTATE)
    }

    // 对当前图片做旋转（带安全 recycle）
    private fun applyRotate(degrees: Float) {
        if (!inRotateMode) return

        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable ?: return
        val src = drawable.bitmap ?: return
        if (src.width <= 0 || src.height <= 0) return

        val matrix = Matrix().apply {
            postRotate(degrees, src.width / 2f, src.height / 2f)
        }

        val rotated = try {
            Bitmap.createBitmap(
                src, 0, 0, src.width, src.height, matrix, true
            )
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), "旋转失败", Toast.LENGTH_SHORT).show()
            return
        }

        binding.imageEditCanvas.setAutoInitFitEnabled(true)
        binding.imageEditCanvas.setImageBitmap(rotated)
        binding.imageEditCanvas.setAutoInitFitEnabled(false)


        if (src != rotateBackupBitmap && !src.isRecycled) {
            src.recycle()
        }
    }

    // 翻转（带安全 recycle）
    private fun applyFlip(horizontal: Boolean) {
        if (!inRotateMode) return

        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable ?: return
        val src = drawable.bitmap ?: return
        if (src.width <= 0 || src.height <= 0) return

        val matrix = Matrix().apply {
            val cx = src.width / 2f
            val cy = src.height / 2f
            if (horizontal) {
                postScale(-1f, 1f, cx, cy)
            } else {
                postScale(1f, -1f, cx, cy)
            }
        }

        val flipped = try {
            Bitmap.createBitmap(
                src, 0, 0, src.width, src.height, matrix, true
            )
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), "翻转失败", Toast.LENGTH_SHORT).show()
            return
        }

        binding.imageEditCanvas.setAutoInitFitEnabled(true)
        binding.imageEditCanvas.setImageBitmap(flipped)
        binding.imageEditCanvas.setAutoInitFitEnabled(false)

        if (src != rotateBackupBitmap && !src.isRecycled) {
            src.recycle()
        }
    }

    private fun exitRotateMode(applyChanges: Boolean) {
        if (!inRotateMode) return
        inRotateMode = false

        val currentDrawable = binding.imageEditCanvas.drawable as? BitmapDrawable
        val currentBitmap = currentDrawable?.bitmap

        if (!applyChanges) {
            rotateBackupBitmap?.let { backup ->
                binding.imageEditCanvas.setImageBitmap(backup)
            }
            if (currentBitmap != null &&
                currentBitmap != rotateBackupBitmap &&
                !currentBitmap.isRecycled
            ) {
                currentBitmap.recycle()
            }
        } else {
            rotateBackupBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
        }
        rotateBackupBitmap = null

        updateUiForMode(EditorMode.NORMAL)
    }

    // 进入调色模式
    private fun enterAdjustMode() {
        if (inAdjustMode) return

        if (inCropMode) exitCropMode()
        if (inRotateMode) exitRotateMode(applyChanges = true)

        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable
        val currentBitmap = drawable?.bitmap
        if (currentBitmap == null) {
            Toast.makeText(requireContext(), "没有可调节的图片", Toast.LENGTH_SHORT).show()
            return
        }

        adjustBackupBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)

        currentBrightness = 0
        currentContrast = 0

        binding.seekBrightness.max = 200
        binding.seekBrightness.progress = 100
        binding.tvBrightnessValue.text = "0"

        binding.seekContrast.max = 200
        binding.seekContrast.progress = 50
        binding.tvContrastValue.text = "0"

        inAdjustMode = true

        updateUiForMode(EditorMode.ADJUST)
    }

    // 调色预览
    private fun applyAdjustPreview(
        brightnessOverride: Int? = null,
        contrastOverride: Int? = null
    ) {
        if (!inAdjustMode) return

        val source = adjustBackupBitmap ?: return
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return

        val brightness = brightnessOverride ?: currentBrightness
        val contrast = contrastOverride ?: currentContrast

        val brightnessOffset = brightness / 100f * 255f
        val contrastFactor = (100f + contrast) / 100f

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]

            val a = color ushr 24 and 0xFF
            var r = color ushr 16 and 0xFF
            var g = color ushr 8 and 0xFF
            var b = color and 0xFF

            r = (((r - 128) * contrastFactor) + 128f + brightnessOffset).toInt()
                .coerceIn(0, 255)
            g = (((g - 128) * contrastFactor) + 128f + brightnessOffset).toInt()
                .coerceIn(0, 255)
            b = (((b - 128) * contrastFactor) + 128f + brightnessOffset).toInt()
                .coerceIn(0, 255)

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val adjustedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        adjustedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val oldPreview = (binding.imageEditCanvas.drawable as? BitmapDrawable)?.bitmap
        binding.imageEditCanvas.setImageBitmap(adjustedBitmap)

        if (oldPreview != null &&
            oldPreview != adjustBackupBitmap &&
            !oldPreview.isRecycled
        ) {
            oldPreview.recycle()
        }
    }

    // 退出调色模式
    private fun exitAdjustMode(applyChanges: Boolean) {
        if (!inAdjustMode) return
        inAdjustMode = false

        val currentDrawable = binding.imageEditCanvas.drawable as? BitmapDrawable
        val currentBitmap = currentDrawable?.bitmap

        if (!applyChanges) {
            adjustBackupBitmap?.let { backup ->
                binding.imageEditCanvas.setImageBitmap(backup)
            }

            if (currentBitmap != null &&
                currentBitmap != adjustBackupBitmap &&
                !currentBitmap.isRecycled
            ) {
                currentBitmap.recycle()
            }
        } else {
            adjustBackupBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
        }

        adjustBackupBitmap = null

        updateUiForMode(EditorMode.NORMAL)
    }

    // ---- 文字相关 ----

    // 可选：如果你想在某些地方主动加一个默认文字，可以调用这个
    private fun addDefaultTextElement() {
        binding.textOverlayView.addTextAtCenter("双击编辑文字\n支持换行展示")
    }

    // 进入单个文本框的“内容编辑”（弹键盘）
    private fun enterTextEditModeForSelected() {
        val selected = binding.textOverlayView.getSelectedElement() ?: return

        // 保持在 TEXT 模式
        if (currentMode != EditorMode.TEXT) {
            updateUiForMode(EditorMode.TEXT)
        }
        binding.textOverlayView.isTextToolActive = true

        // 显示暗背景 + 输入栏，隐藏样式 panel
        binding.viewDimBackground.visibility = View.VISIBLE
        binding.layoutTextEditBar.visibility = View.VISIBLE
        binding.layoutTextStylePanel.visibility = View.GONE

        binding.etTextInput.setText(selected.text)
        binding.etTextInput.setSelection(selected.text.length)
        binding.etTextInput.requestFocus()
        showKeyboard(binding.etTextInput)
    }

    // 退出“内容编辑”（仅关闭键盘和输入栏，回到 TEXT 样式面板）
    private fun exitTextContentEdit() {
        hideKeyboard()
        binding.viewDimBackground.visibility = View.GONE
        binding.layoutTextEditBar.visibility = View.GONE

        if (currentMode == EditorMode.TEXT) {
            binding.layoutTextStylePanel.visibility = View.VISIBLE
        }
    }

    // 完全退出文字模式（回到 NORMAL）
    private fun exitTextMode() {
        hideKeyboard()

        binding.viewDimBackground.visibility = View.GONE
        binding.layoutTextEditBar.visibility = View.GONE
        binding.layoutTextControls.visibility = View.GONE
        binding.layoutTextStylePanel.visibility = View.GONE

        binding.textOverlayView.isTextToolActive = false
        binding.textOverlayView.clearSelection()

        updateUiForMode(EditorMode.NORMAL)
    }

    private fun showKeyboard(view: View) {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.post {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etTextInput.windowToken, 0)
    }

    private fun composeCurrentImageWithText(): Bitmap? {
        // 用整个编辑区域的大小来合成，确保文字和图片对齐
        val root = binding.editorCanvasContainer
        val w = root.width
        val h = root.height
        if (w <= 0 || h <= 0) return null

        // 保险起见：保存前去掉选中框（如果你希望保存图里不要虚线框）
        binding.textOverlayView.clearSelection()
        binding.textOverlayView.isTextToolActive = false

        // 创建结果 Bitmap
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 直接把整个容器画上去：里面已经包括 imageEditCanvas + textOverlayView
        root.draw(canvas)

        return result
    }

    //  把当前编辑画面（底图 + 文本）合成到一张 Bitmap，并在右下角加上“训练营”水印
    private fun composeCurrentImageWithWatermark(): Bitmap? {
        val root = binding.editorCanvasContainer
        val w = root.width
        val h = root.height
        if (w <= 0 || h <= 0) return null

        // 临时隐藏选中框和角按钮，仅用于这次绘制
        val overlay = binding.textOverlayView
        val oldSuppress = overlay.suppressSelectionDrawing
        overlay.suppressSelectionDrawing = true

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 把整个编辑容器（包括 imageEditCanvas + textOverlayView）画到 Bitmap
        root.draw(canvas)

        // 恢复选中框绘制开关
        overlay.suppressSelectionDrawing = oldSuppress

        // 在右下角绘制“训练营”水印
        val watermarkText = "训练营"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (16 * resources.displayMetrics.scaledDensity)  // 16sp 左右
            style = Paint.Style.FILL
            setShadowLayer(4f, 2f, 2f, 0x80000000.toInt())            // 轻微阴影提高可见性
        }

        // 计算文字宽高
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(watermarkText, 0, watermarkText.length, textBounds)
        val padding = 16 * resources.displayMetrics.density

        val x = w - padding - textBounds.width()
        val y = h - padding

        canvas.drawText(watermarkText, x, y, paint)

        return result
    }

    // 把 bitmap 保存到系统相册（Pictures/TT-IC-Camera），成功返回 true，失败返回 false
    private fun saveBitmapToGallery(bitmap: Bitmap, onResult: (Boolean) -> Unit) {
        ioExecutor.execute {
            val success = try {
                val resolver = requireContext().contentResolver
                val fileName = "TTIC_${System.currentTimeMillis()}.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/TT-IC-Camera"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@execute onResult(false)

                resolver.openOutputStream(uri)?.use { out ->
                    val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    if (!ok) {
                        return@execute onResult(false)
                    }
                } ?: return@execute onResult(false)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            // 回到主线程通知结果（弹 Toast 只能在主线程）
            requireActivity().runOnUiThread {
                onResult(success)
            }
        }
    }


    override fun onDestroyView() {
        val b = _binding

        if (b != null) {
            val currentBitmap =
                (b.imageEditCanvas.drawable as? BitmapDrawable)?.bitmap

            rotateBackupBitmap?.let { bmp ->
                if (bmp != currentBitmap && !bmp.isRecycled) {
                    bmp.recycle()
                }
            }
            rotateBackupBitmap = null

            adjustBackupBitmap?.let { bmp ->
                if (bmp != currentBitmap && !bmp.isRecycled) {
                    bmp.recycle()
                }
            }
            adjustBackupBitmap = null
        } else {
            rotateBackupBitmap = null
            adjustBackupBitmap = null
        }

        _binding = null
        super.onDestroyView()
    }
}
