package com.owenzx.lightedit.ui.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.owenzx.lightedit.databinding.FragmentEntryBinding

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

    override fun onDestroyView() {
        super.onDestroyView()
        // 释放 Binding防止内存泄露
        _binding = null
    }
}