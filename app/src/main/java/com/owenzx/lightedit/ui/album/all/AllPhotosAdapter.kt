package com.owenzx.lightedit.ui.album.all

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.owenzx.lightedit.data.album.PhotoItem
import com.owenzx.lightedit.databinding.ItemPhotoGridBinding
import com.owenzx.lightedit.core.image.ThumbnailLoader

class AllPhotosAdapter(
    private var items: List<PhotoItem>,
    private val onItemClick: (PhotoItem) -> Unit,
    private val onPreviewClick: (PhotoItem) -> Unit
) : RecyclerView.Adapter<AllPhotosAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(
        // 缓存每一个 item 的 View，避免重复 findViewById
        private val binding: ItemPhotoGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoItem) {
            // 估算缩略图尺寸：用屏幕宽度 / 列数
            val spanCount = 3
            val screenWidth = binding.root.resources.displayMetrics.widthPixels
            val size = screenWidth / spanCount

            val resolver = binding.root.context.applicationContext.contentResolver

            ThumbnailLoader.loadSquareThumbnail(
                imageView = binding.imageThumbnail,
                resolver = resolver,
                photoId = photo.id,
                uri = photo.uri,
                targetSizePx = size
            )

            // 点击图片区域：进入编辑
            binding.imageThumbnail.setOnClickListener {
                onItemClick(photo)
            }

            // 点击右下角预览 icon：进入预览
            binding.iconPreview.setOnClickListener {
                onPreviewClick(photo)
            }
        }
    }

    // Fragment 里异步加载完数据后更新
    fun submitList(newList: List<PhotoItem>) {
        items = newList
        notifyDataSetChanged()
    }

    // 创建每个格子的布局
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemPhotoGridBinding.inflate(inflater, parent, false)
        return PhotoViewHolder(binding)
    }

    // 把特定数据绑定到特定的item 滑出屏幕后复用 ViewHolder → bind 下一个位置
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

}