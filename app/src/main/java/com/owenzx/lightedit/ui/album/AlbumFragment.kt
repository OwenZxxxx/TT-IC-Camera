package com.owenzx.lightedit.ui.album

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.owenzx.lightedit.core.permissions.MediaPermissionHelper
import com.owenzx.lightedit.databinding.FragmentAlbumBinding
import com.owenzx.lightedit.ui.album.all.AllPhotosFragment
import com.owenzx.lightedit.ui.album.folder.FolderPhotosFragment


class AlbumFragment: Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!

    // Tab 标题
    private val tabTitles = listOf("全部图片", "按文件夹")

    // 注册权限请求 launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "已获得相册读取权限", Toast.LENGTH_SHORT).show()
            // 后面接入真数据时，可以在这里触发数据加载
        } else {
            Toast.makeText(requireContext(), "没有相册权限，将无法加载本地图片", Toast.LENGTH_LONG).show()
            // 后面可以在这里显示“无权限”占位 UI
        }
    }

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


        // 检查并请求权限
        checkAndRequestPermission()
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
    // 检查并请求权限
    private fun checkAndRequestPermission() {
        val permission = MediaPermissionHelper.getReadImagesPermission()
        val context = requireContext()

        val granted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            // 已有权限：现在还用假数据，先提示一下
            // 后面会在这里调用：加载真实 MediaStore 数据
            Toast.makeText(context, "相册权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            // 没权限：弹出请求框
            requestPermissionLauncher.launch(permission)
        }
    }

}