package com.owenzx.lightedit.ui.album.folder

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.owenzx.lightedit.R
import com.owenzx.lightedit.data.album.MediaStoreAlbumRepository
import com.owenzx.lightedit.data.album.PhotoItem
import com.owenzx.lightedit.databinding.FragmentFolderDetailBinding
import com.owenzx.lightedit.ui.album.all.AllPhotosAdapter
import com.owenzx.lightedit.ui.editor.EditorFragment
import com.owenzx.lightedit.ui.preview.PreviewFragment
import java.util.concurrent.Executors

class FolderDetailFragment : Fragment() {

    companion object {
        private const val ARG_BUCKET_ID = "arg_bucket_id"
        private const val ARG_BUCKET_NAME = "arg_bucket_name"

        fun newInstance(bucketId: Long, bucketName: String): FolderDetailFragment {
            return FolderDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_BUCKET_ID, bucketId)
                    putString(ARG_BUCKET_NAME, bucketName)
                }
            }
        }
    }

    private var _binding: FragmentFolderDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AllPhotosAdapter
    private val executor = Executors.newSingleThreadExecutor()

    private var bucketId: Long = -1L
    private var bucketName: String = ""

    // 数据属于 Fragment 生命周期 哪怕 View 被销毁/重建，依然保留在 Fragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bucketId = requireArguments().getLong(ARG_BUCKET_ID)
        bucketName = requireArguments().getString(ARG_BUCKET_NAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置标题
        binding.tvFolderTitle.text = bucketName

        setupRecyclerView()

        loadPhotosInFolder()
    }

    private fun setupRecyclerView() {
        adapter = AllPhotosAdapter(
            items = emptyList(),
            onItemClick = { photo ->
                openEditor(photo.uri)
            },
            onPreviewClick = { photo ->
                openPreview(photo.uri)
            }
        )

        binding.recyclerFolderPhotos.layoutManager =
            GridLayoutManager(requireContext(), 3)
        binding.recyclerFolderPhotos.adapter = adapter
    }

    private fun showLoading() {
        binding.recyclerFolderPhotos.visibility = View.GONE
        binding.tvFolderState.visibility = View.VISIBLE
        binding.tvFolderState.text = "正在加载..."
    }

    private fun showEmpty() {
        binding.recyclerFolderPhotos.visibility = View.GONE
        binding.tvFolderState.visibility = View.VISIBLE
        binding.tvFolderState.text = "该文件夹中没有图片"
    }

    private fun showContent() {
        binding.recyclerFolderPhotos.visibility = View.VISIBLE
        binding.tvFolderState.visibility = View.GONE
    }

    // 后台线程加载图片
    private fun loadPhotosInFolder() {
        showLoading()

        val resolver = requireContext().contentResolver

        executor.execute {
            val list: List<PhotoItem> = MediaStoreAlbumRepository
                .loadPhotosInBucket(resolver, bucketId)

            requireActivity().runOnUiThread {
                if (!isAdded) return@runOnUiThread
                // 处理空数据
                if (list.isEmpty()) {
                    showEmpty()
                // 展示内容
                } else {
                    showContent()
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun openEditor(uri: Uri) {
        val fm = requireActivity().supportFragmentManager
        fm.beginTransaction()
            .replace(
                R.id.fragment_container_view,
                EditorFragment.newInstance(uri)
            )
            .addToBackStack(null)
            .commit()
    }

    private fun openPreview(uri: Uri) {
        val fm = requireActivity().supportFragmentManager
        fm.beginTransaction()
            .replace(
                R.id.fragment_container_view,
                PreviewFragment.newInstance(uri)
            )
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // shutdown 线程池
        // executor.shutdown()
    }
}