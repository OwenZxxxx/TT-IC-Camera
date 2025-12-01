package com.owenzx.lightedit.ui.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.owenzx.lightedit.ui.album.AlbumFragment
import com.owenzx.lightedit.databinding.FragmentEntryBinding
import android.widget.Toast

class EntryFragment : Fragment() {
    // 可空变量，fragment 的view可能比fragment的生命周期短
    private var _binding: FragmentEntryBinding? = null
    // 自定义getter
    // !! 能保证不会在 UI 已销毁时操作 UI
    private val binding get() = _binding!!

    override fun onCreateView(
        // 构建UI
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 从相册选择照片：跳转到 AlbumFragment
        binding.btnFromAlbum.setOnClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(
                    // MainActivity 中 Fragment 容器的 id
                    (requireActivity()
                        .findViewById<View>(com.owenzx.lightedit.R.id.fragment_container_view)).id,
                    AlbumFragment()
                )
                .addToBackStack(null)  // 加入返回栈，按返回键能回到 Entry
                .commit()
        }
        
        // 拍一张照片：暂时先保留 Toast 占位
        binding.btnTakePhoto.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "TODO: 打开相机（后面实现）",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 释放 Binding防止内存泄露
        _binding = null
    }
}