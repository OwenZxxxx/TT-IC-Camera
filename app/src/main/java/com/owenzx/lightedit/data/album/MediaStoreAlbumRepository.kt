package com.owenzx.lightedit.data.album

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log


object MediaStoreAlbumRepository {

    /**
     * 查询本地所有图片（Android 10+）
     */
    fun queryAllPhotos(contentResolver: ContentResolver): List<PhotoItem> {

        val photos = mutableListOf<PhotoItem>()

        // Android 10+ 直接用 VOLUME_EXTERNAL
        val collection: Uri =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        // 时间倒序-符合用户需求，新拍的放在前面
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // 访问系统相册的入口
        val cursor = contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        ) ?: return emptyList()

        // 安全读取cursor
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol =
                c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val displayName = c.getString(nameCol)
                val dateAdded = c.getLong(dateCol)
                val bucketId = c.getLong(bucketIdCol)
                val bucketName = c.getString(bucketNameCol)

                // 拼出每张图的 Uri
                val contentUri = ContentUris.withAppendedId(collection, id)

                // 构建统一的数据对象
                photos.add(
                    PhotoItem(
                        id = id,
                        uri = contentUri,
                        displayName = displayName ?: "图片",
                        dateAdded = dateAdded,
                        bucketId = bucketId,
                        bucketName = bucketName ?: "未分类"
                    )
                )
            }
        }
        Log.d("AlbumRepo", "queryAllPhotos result size = ${photos.size}")
        return photos
    }

    // 先load再分组
    fun loadAllFolders(contentResolver: ContentResolver): List<AlbumFolder> {
        val allPhotos = queryAllPhotos(contentResolver)
        if (allPhotos.isEmpty()) return emptyList()

        // 按 bucketId 分组
        val grouped = allPhotos.groupBy { it.bucketId }

        val folders = mutableListOf<AlbumFolder>()

        for ((bucketId, photosInBucket) in grouped) {
            // 假设 allPhotos 是按时间倒序的，
            // 则 photosInBucket 里也大致是按时间倒序
            val first = photosInBucket.first()

            val folder = AlbumFolder(
                bucketId = bucketId,
                bucketName = first.bucketName,
                coverUri = first.uri,
                photoCount = photosInBucket.size
            )
            folders += folder
        }

        // 按文件夹名升序排序（忽略大小写）
        return folders.sortedBy { it.bucketName.lowercase() }
    }

    // 按照bucketId过滤图片
    fun loadPhotosInBucket(
        contentResolver: ContentResolver,
        bucketId: Long
    ): List<PhotoItem> {
        // 先重用已有的“全部图片”列表，然后按 bucketId 过滤
        val all = queryAllPhotos(contentResolver)
        if (all.isEmpty()) return emptyList()

        return all.filter { it.bucketId == bucketId }
    }

}