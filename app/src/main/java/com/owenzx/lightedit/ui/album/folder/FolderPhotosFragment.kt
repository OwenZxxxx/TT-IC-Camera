package com.owenzx.lightedit.ui.album.folder

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.owenzx.lightedit.R
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.owenzx.lightedit.core.permissions.MediaPermissionHelper
import com.owenzx.lightedit.data.album.AlbumFolder
import com.owenzx.lightedit.data.album.MediaStoreAlbumRepository
import com.owenzx.lightedit.databinding.FragmentFolderPhotosBinding
import java.util.concurrent.Executors

class FolderPhotosFragment : Fragment() {

    private var _binding: FragmentFolderPhotosBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FolderPhotosAdapter

    // 用线程池做异步加载
    // 启动一个单线程后台线程，专门用于加载相册数据
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        if (!hasPermission()) {
            showNoPermission()
        } else {
            loadFolders()
        }
    }

    private fun setupRecyclerView() {
        adapter = FolderPhotosAdapter(
            emptyList(),
            onFolderClick = { folder ->
                openFolderDetail(folder)
            }
        )
        binding.recyclerFolders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFolders.adapter = adapter
    }

    private fun hasPermission(): Boolean {
        val p = MediaPermissionHelper.getReadImagesPermission()
        return ContextCompat.checkSelfPermission(requireContext(), p) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun showNoPermission() {
        binding.recyclerFolders.visibility = View.GONE
        binding.tvStateFolder.visibility = View.VISIBLE
        binding.tvStateFolder.text = "无相册权限，无法按文件夹加载"
    }

    private fun showEmpty() {
        binding.recyclerFolders.visibility = View.GONE
        binding.tvStateFolder.visibility = View.VISIBLE
        binding.tvStateFolder.text = "相册为空，暂无文件夹"
    }

    private fun showContent() {
        binding.recyclerFolders.visibility = View.VISIBLE
        binding.tvStateFolder.visibility = View.GONE
    }

    // 后台加载 + 主线程更新 UI
    private fun loadFolders() {
        // 进入 loadFolders 时，先显示“正在加载文件夹...”
        binding.recyclerFolders.visibility = View.GONE
        binding.tvStateFolder.visibility = View.VISIBLE
        binding.tvStateFolder.text = "正在加载文件夹..."

        val resolver = requireContext().contentResolver

        // 在后台线程中调用loadAllFolders
        executor.execute {
            val folders: List<AlbumFolder> = try {
                MediaStoreAlbumRepository.loadAllFolders(resolver)
            } catch (e: SecurityException) {
                emptyList()
            }

            // MediaStore 查询结果拿到之后，回到主线程更新 UI
            requireActivity().runOnUiThread {
                if (!isAdded) return@runOnUiThread

                // 提示“相册为空”
                if (folders.isEmpty()) {
                    showEmpty()
                // 更新 RecyclerView
                } else {
                    showContent()
                    adapter.submitList(folders)
                }
            }
        }
    }

    private fun openFolderDetail(folder: AlbumFolder) {
        val fm = requireActivity().supportFragmentManager
        fm.beginTransaction()
            .replace(
                R.id.fragment_container_view,
                FolderDetailFragment.newInstance(folder.bucketId, folder.bucketName)
            )
            .addToBackStack(null)
            .commit()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // executor.shutdown() // 可选
    }
}