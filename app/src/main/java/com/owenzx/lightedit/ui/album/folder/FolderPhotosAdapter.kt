package com.owenzx.lightedit.ui.album.folder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.owenzx.lightedit.data.album.AlbumFolder
import com.owenzx.lightedit.databinding.ItemAlbumFolderBinding

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
            // 封面图
            binding.imageCover.setImageURI(folder.coverUri)

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