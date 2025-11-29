package com.owenzx.lightedit.ui.album

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.owenzx.lightedit.databinding.FragmentAlbumBinding
import com.owenzx.lightedit.ui.album.all.AllPhotosFragment
import com.owenzx.lightedit.ui.album.folder.FolderPhotosFragment


class AlbumFragment: Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!

    // Tab 标题
    private val tabTitles = listOf("全部图片", "按文件夹")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置 ViewPager2 的 Adapter
        val pagerAdapter = AlbumPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // 绑定 TabLayout 和 ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 内部类：ViewPager2 适配器
    private class AlbumPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AllPhotosFragment()
                1 -> FolderPhotosFragment()
                else -> AllPhotosFragment()
            }
        }
    }

}