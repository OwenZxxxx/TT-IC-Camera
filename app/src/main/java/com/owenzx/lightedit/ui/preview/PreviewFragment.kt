package com.owenzx.lightedit.ui.preview

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.transition.Transition
import com.owenzx.lightedit.R
import com.owenzx.lightedit.databinding.FragmentPreviewBinding
import com.owenzx.lightedit.ui.editor.EditorFragment
import androidx.transition.TransitionInflater

class PreviewFragment : Fragment() {

    // 通过 arguments Bundle 传参数
    // 配合 Fragment 重建（旋转屏幕 / 进程被杀再恢复）都能自动恢复
    // 没有对 Activity / View 的强引用，也不会造成泄露
    companion object {
        private const val ARG_PHOTO_URI = "arg_photo_uri"

        // 通过newInstance把uri传进来
        fun newInstance(uri: Uri): PreviewFragment {
            return PreviewFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PHOTO_URI, uri)
                }
            }
        }
    }

    private lateinit var photoUri: Uri
    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    // 标记：是否需要“延迟显示控件”
    private var shouldDelayControls: Boolean = true

    // 在 onCreate() 里读取 arguments 里的 Uri
    // 放 onCreate数据（Uri）不依赖 View，即便 Fragment 的 View 多次销毁/重建（切换 Tab 之类），photoUri 仍然可用
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从 arguments 中取出 Uri。若没传会抛异常，说明调用方没按规范来。
        photoUri = requireArguments().getParcelable(ARG_PHOTO_URI)!!

        // 只配置共享元素动画，不要额外的 enter/return 动画去“盖背景”
        val transition = TransitionInflater.from(requireContext())
            .inflateTransition(R.transition.transition_image_shared)
        sharedElementEnterTransition = transition
        sharedElementReturnTransition = transition

        // 如果是配置变化恢复（比如旋转屏幕），不再延迟控件
        if (savedInstanceState != null) {
            shouldDelayControls = false
        }

        // 等图片布局好了再开启动画，避免“直接跳”过去的感觉
        postponeEnterTransition()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 显示图片 + 点击按钮跳转到 EditorFragment
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 保证根布局透明，不抢背景
        binding.layoutPreviewRoot.setBackgroundResource(android.R.color.transparent)

        // 保证与缩略图使用相同的 transitionName（Uri）
        binding.imagePreview.transitionName = photoUri.toString()

        // 显示这张图片
        binding.imagePreview.setImageURI(photoUri)

        // ✅ 只有“首进预览且需要动画”的时候才把控件设为透明
        if (shouldDelayControls) {
            binding.layoutPreviewHeader.alpha = 0f
            binding.btnGoEdit.alpha = 0f
        } else {
            binding.layoutPreviewHeader.alpha = 1f
            binding.btnGoEdit.alpha = 1f
        }

        // 等 imagePreview 测量 & 绘制完成后再启动过渡动画
        binding.imagePreview.doOnPreDraw {
            startPostponedEnterTransition()
        }

        // 返回按钮：退回到相册页
        binding.btnPreviewBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 点击按钮：把同一张 Uri 丢给 EditorFragment
        binding.btnGoEdit.setOnClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.fragment_container_view,
                    EditorFragment.newInstance(photoUri, true) // 把同一张 Uri 传给编辑页
                )
                .addToBackStack(null)
                .commit()
        }

        // // 监听共享元素动画结束，结束后再淡入顶部 & 底部 UI
        (sharedElementEnterTransition as? Transition)?.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    if (shouldDelayControls) {
                        shouldDelayControls = false  // 之后不再延迟控件
                        // 动画结束，淡入header和按钮
                        binding.layoutPreviewHeader.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                        binding.btnGoEdit.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    transition.removeListener(this)
                }

                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            }
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}