package com.owenzx.lightedit.core.permissions

import android.Manifest
import android.os.Build

object MediaPermissionHelper {

    /**
     * 返回当前系统下，读取图片需要的权限字符串
     */
    fun getReadImagesPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 10–12
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun getCameraPermission(): String {
        return Manifest.permission.CAMERA
    }

}