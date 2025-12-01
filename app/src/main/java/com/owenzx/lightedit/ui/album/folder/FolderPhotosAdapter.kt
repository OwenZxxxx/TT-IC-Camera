package com.owenzx.lightedit.ui.album.folder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.owenzx.lightedit.data.album.AlbumFolder
import com.owenzx.lightedit.databinding.ItemAlbumFolderBinding
import com.owenzx.lightedit.core.image.ThumbnailLoader

// 绑定数据到item上
class FolderPhotosAdapter(
    private var items: List<AlbumFolder>,
    // Adapter 不管跳转逻辑
    private val onFolderClick: (AlbumFolder) -> Unit
) : RecyclerView.Adapter<FolderPhotosAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(
        private val binding: ItemAlbumFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: AlbumFolder) {
            val resolver = binding.root.context.applicationContext.contentResolver

            // 给封面也做一个方形缩略图，这里大小可以小一些
            val density = binding.root.resources.displayMetrics.density
            val sizePx = (72 * density).toInt() // 72dp

            ThumbnailLoader.loadSquareThumbnail(
                imageView = binding.imageCover,
                resolver = resolver,
                photoId = folder.bucketId, // 这里可以用 bucketId 做 key（每个文件夹一个缩略图）
                uri = folder.coverUri,
                targetSizePx = sizePx
            )

            // 名称 & 数量
            binding.tvFolderName.text = folder.bucketName
            binding.tvFolderCount.text = "共 ${folder.photoCount} 张"

            binding.root.setOnClickListener {
                onFolderClick(folder)
            }
        }
    }

    fun submitList(newItems: List<AlbumFolder>) {
        items = newItems
        notifyDataSetChanged() // 后续可以换 DiffUtil
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemAlbumFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}