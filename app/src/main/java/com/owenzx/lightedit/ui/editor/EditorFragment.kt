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

    private var inCropMode: Boolean = false

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

        // 下面几个暂时先留空或 TODO，后续再逐个实现
        binding.btnToolRotate.setOnClickListener {
            Toast.makeText(requireContext(), "旋转功能待实现", Toast.LENGTH_SHORT).show()
        }

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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}