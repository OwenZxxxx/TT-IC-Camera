package com.owenzx.lightedit.ui.editor

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.owenzx.lightedit.databinding.FragmentEditorBinding
import android.widget.Toast
import com.owenzx.lightedit.ui.editor.crop.AspectRatioMode
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable

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

    private lateinit var photoUri: Uri

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    //裁剪模式标记
    private var inCropMode: Boolean = false

    // 旋转/翻转模式标记
    private var inRotateMode: Boolean = false

    // 进入旋转模式时的原图备份，用于“取消”还原
    private var rotateBackupBitmap: Bitmap? = null

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

    // 这里我们来接 UI + 展示图片
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置标题（可选）
        binding.tvEditorTitle.text = "编辑图片"

        // 显示图片（ZoomableImageView 负责缩放 + 拖动）
        binding.imageEditCanvas.setImageURI(photoUri)

        // 顶部返回按钮：返回上一层（比如 Preview / Album）
        binding.btnEditorBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 普通工具栏：裁剪按钮 → 进入裁剪模式
        binding.btnToolCrop.setOnClickListener {
            enterCropMode()
        }

        // 旋转按钮 → 进入旋转模式
        binding.btnToolRotate.setOnClickListener {
            enterRotateMode()
        }

        // 下面几个暂时先留空或 TODO，后续再逐个实现
        binding.btnToolAdjust.setOnClickListener {
            Toast.makeText(requireContext(), "亮度/对比度功能待实现", Toast.LENGTH_SHORT).show()
        }

        binding.btnToolText.setOnClickListener {
            Toast.makeText(requireContext(), "文字功能待实现", Toast.LENGTH_SHORT).show()
        }

        binding.btnToolSave.setOnClickListener {
            Toast.makeText(requireContext(), "保存功能待实现", Toast.LENGTH_SHORT).show()
        }

        // 裁剪模式下：取消按钮
        binding.btnCropCancel.setOnClickListener {
            exitCropMode()
        }

        // 裁剪模式下：确认按钮
        binding.btnCropConfirm.setOnClickListener {
            // 从 CropOverlayView 拿到当前裁剪框（View 坐标）
            val cropRectInView = binding.cropOverlayView.getCropRect()

            // 让 ZoomableImageView 根据这个裁剪框去裁 Bitmap
            val croppedBitmap = binding.imageEditCanvas.getCroppedBitmap(cropRectInView)

            if (croppedBitmap != null) {
                // 用裁剪后的 Bitmap 替换当前显示的图片
                binding.imageEditCanvas.setImageBitmap(croppedBitmap)
                // 可选：你也可以在这里更新 photoUri 或记录“已裁剪状态”，方便保存时使用

                // 退出裁剪模式
                exitCropMode()
            } else {
                Toast.makeText(requireContext(), "裁剪失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }

        // 比例按钮：切换裁剪宽高比
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

        // 旋转/翻转控制条按钮
        // 左转 90°
        binding.btnRotateLeft90.setOnClickListener {
            applyRotate( -90f )
        }

        // 右转 90°
        binding.btnRotateRight90.setOnClickListener {
            applyRotate( 90f )
        }

        // 旋转 180°
        binding.btnRotate180.setOnClickListener {
            applyRotate( 180f )
        }

        // 水平翻转
        binding.btnFlipHorizontal.setOnClickListener {
            applyFlip(horizontal = true)
        }

        // 垂直翻转
        binding.btnFlipVertical.setOnClickListener {
            applyFlip(horizontal = false)
        }

        // 取消：丢弃所有旋转/翻转操作，还原备份
        binding.btnRotateCancel.setOnClickListener {
            exitRotateMode(applyChanges = false)
        }

        // 确认：保留当前结果
        binding.btnRotateConfirm.setOnClickListener {
            exitRotateMode(applyChanges = true)
        }
    }

    private fun enterCropMode() {
        if (inCropMode) return
        inCropMode = true

        // 显示裁剪遮罩 & 裁剪控制区
        binding.cropOverlayView.visibility = View.VISIBLE
        binding.layoutCropControls.visibility = View.VISIBLE

        // 隐藏普通工具栏
        binding.layoutEditorToolbar.visibility = View.GONE
        // 让图片在裁剪模式下不响应手势（只移动裁剪框）
        binding.imageEditCanvas.setCropModeEnabled(true)
    }

    private fun exitCropMode() {
        if (!inCropMode) return
        inCropMode = false

        binding.cropOverlayView.visibility = View.GONE
        binding.layoutCropControls.visibility = View.GONE
        binding.layoutEditorToolbar.visibility = View.VISIBLE

        // 恢复裁剪模式里锁定过缩放/拖动
        binding.imageEditCanvas.setCropModeEnabled(false)
    }

    private fun enterRotateMode() {
        if (inRotateMode) return

        // 如果当前在裁剪模式，先“应用”裁剪再进入旋转（也可以选择先退出裁剪）
        if (inCropMode) {
            exitCropMode()
        }

        // 备份当前 Bitmap：用于“取消”时恢复
        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable
        val currentBitmap = drawable?.bitmap
        if (currentBitmap == null) {
            Toast.makeText(requireContext(), "没有可编辑的图片", Toast.LENGTH_SHORT).show()
            return
        }

        // copy 一份防止后续 Bitmap.createBitmap 链式操作污染原图
        rotateBackupBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)

        inRotateMode = true

        // 显示旋转控制条，隐藏普通编辑工具条
        binding.layoutRotateControls.visibility = View.VISIBLE
        binding.layoutEditorToolbar.visibility = View.GONE

        // 裁剪遮罩一定要关掉
        binding.cropOverlayView.visibility = View.GONE
        binding.layoutCropControls.visibility = View.GONE
    }

    // 对当前图片做旋转
    private fun applyRotate(degrees: Float) {
        if (!inRotateMode) return

        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable ?: return
        val src = drawable.bitmap ?: return
        if (src.width <= 0 || src.height <= 0) return

        val matrix = Matrix().apply {
            // 围绕图片中心旋转
            postRotate(degrees, src.width / 2f, src.height / 2f)
        }

        val rotated = try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), "旋转失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 更新显示为旋转之后的图片
        binding.imageEditCanvas.setImageBitmap(rotated)
    }

    // 对当前图片做翻转；只在旋转模式下生效
    private fun applyFlip(horizontal: Boolean) {
        if (!inRotateMode) return

        val drawable = binding.imageEditCanvas.drawable as? BitmapDrawable ?: return
        val src = drawable.bitmap ?: return
        if (src.width <= 0 || src.height <= 0) return

        val matrix = Matrix().apply {
            val cx = src.width / 2f
            val cy = src.height / 2f
            if (horizontal) {
                // 水平翻转：X 轴取反
                postScale(-1f, 1f, cx, cy)
            } else {
                // 垂直翻转：Y 轴取反
                postScale(1f, -1f, cx, cy)
            }
        }

        val flipped = try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), "翻转失败", Toast.LENGTH_SHORT).show()
            return
        }

        binding.imageEditCanvas.setImageBitmap(flipped)
    }


    private fun exitRotateMode(applyChanges: Boolean) {
        if (!inRotateMode) return
        inRotateMode = false

        if (!applyChanges) {
            // 还原原图
            rotateBackupBitmap?.let { backup ->
                binding.imageEditCanvas.setImageBitmap(backup)
            }
        } else {
            // 确认旋转：备份已没用了，可以回收释放内存
            rotateBackupBitmap?.recycle()
        }
        rotateBackupBitmap = null

        // 隐藏旋转控制条，恢复普通工具栏
        binding.layoutRotateControls.visibility = View.GONE
        binding.layoutEditorToolbar.visibility = View.VISIBLE
    }


    override fun onDestroyView() {
        super.onDestroyView()

        // 安全回收旋转备份
        rotateBackupBitmap?.recycle()
        rotateBackupBitmap = null

        _binding = null
    }
}