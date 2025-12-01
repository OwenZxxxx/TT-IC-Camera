package com.owenzx.lightedit.data.album

import android.net.Uri

data class AlbumFolder(
    val bucketId: Long,
    val bucketName: String,
    val coverUri: Uri,   // 封面图（这个文件夹里的某一张图片，一般选最新那张）
    val photoCount: Int  // 该文件夹下图片数量
)