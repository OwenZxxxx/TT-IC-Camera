package com.owenzx.lightedit.ui.album.all

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.owenzx.lightedit.R
import com.owenzx.lightedit.core.permissions.MediaPermissionHelper
import com.owenzx.lightedit.data.album.MediaStoreAlbumRepository
import com.owenzx.lightedit.data.album.PhotoItem
import com.owenzx.lightedit.databinding.FragmentAllPhotosBinding
import com.owenzx.lightedit.ui.editor.EditorFragment
import com.owenzx.lightedit.ui.preview.PreviewFragment
import java.util.concurrent.Executors

class AllPhotosFragment : Fragment() {

    private var _binding: FragmentAllPhotosBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AllPhotosAdapter

    // 用线程池做异步加载
    // 启动一个单线程后台线程，专门用于加载相册数据
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        if (!hasPermission()) {
            showNoPermission()
        } else {
            loadPhotos()
        }
    }

    private fun hasPermission(): Boolean {
        val p = MediaPermissionHelper.getReadImagesPermission()
        return ContextCompat.checkSelfPermission(requireContext(), p) ==
                PackageManager.PERMISSION_GRANTED
    }

    // 设置Recyclerview
    private fun setupRecyclerView() {
        adapter = AllPhotosAdapter(
            emptyList(),
            onItemClick = { photo -> openEditor(photo) },
            onPreviewClick = { photo -> openPreview(photo) }
        )

        binding.recyclerAllPhotos.layoutManager =
            GridLayoutManager(requireContext(), 3)
        binding.recyclerAllPhotos.adapter = adapter
    }

    // 打开编辑界面
    private fun openEditor(photo: PhotoItem) {
        val fm = requireActivity().supportFragmentManager
        fm.beginTransaction()
            .replace(
                R.id.fragment_container_view,
                EditorFragment.newInstance(photo.uri)
            )
            .addToBackStack(null)
            .commit()
    }

    // 打开预览界面
    private fun openPreview(photo: PhotoItem) {
        val fm = requireActivity().supportFragmentManager
        fm.beginTransaction()
            .replace(
                R.id.fragment_container_view,
                PreviewFragment.newInstance(photo.uri))
            .addToBackStack(null)
            .commit()
    }

    // 展示没有权限信息
    private fun showNoPermission() {
        binding.recyclerAllPhotos.visibility = View.GONE
        binding.tvState.visibility = View.VISIBLE
        binding.tvState.text = "无相册权限，无法加载本地图片"
    }

    // 展示相册为空信息
    private fun showEmpty() {
        binding.recyclerAllPhotos.visibility = View.GONE
        binding.tvState.visibility = View.VISIBLE
        binding.tvState.text = "相册为空"
    }

    // 调整layout
    private fun showContent() {
        binding.recyclerAllPhotos.visibility = View.VISIBLE
        binding.tvState.visibility = View.GONE
    }

    // 加载图片
    private fun loadPhotos() {

        // 进入 loadPhotos 时，先显示“正在加载相册...”
        binding.recyclerAllPhotos.visibility = View.GONE
        binding.tvState.visibility = View.VISIBLE
        binding.tvState.text = "正在加载相册..."

        val resolver = requireContext().contentResolver

        // 在后台线程中调用queryAllPhotos
        executor.execute {

            val photos: List<PhotoItem> = try {
                MediaStoreAlbumRepository.queryAllPhotos(resolver)
            } catch (e: SecurityException) {
                emptyList()
            }

            // MediaStore 查询结果拿到之后，回到主线程更新 UI
            requireActivity().runOnUiThread {
                if (!isAdded) return@runOnUiThread
                // 处理空情况
                if (photos.isEmpty()) {
                    showEmpty()
                // 更新 RecyclerView
                } else {
                    showContent()
                    adapter.submitList(photos)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}