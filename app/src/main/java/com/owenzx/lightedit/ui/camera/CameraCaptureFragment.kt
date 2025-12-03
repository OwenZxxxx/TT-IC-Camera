package com.owenzx.lightedit.ui.camera

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.owenzx.lightedit.R
import com.owenzx.lightedit.core.permissions.MediaPermissionHelper
import com.owenzx.lightedit.databinding.FragmentCameraCaptureBinding
import com.owenzx.lightedit.ui.editor.EditorFragment
import java.util.concurrent.Executors

class CameraCaptureFragment : Fragment() {

    private var _binding: FragmentCameraCaptureBinding? = null
    private val binding get() = _binding!!

    // 本次拍照要写入的 Uri，成功后会传给 EditorFragment
    private var pendingImageUri: Uri? = null

    // 申请相机权限
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCaptureFlow()
            } else {
                showState("相机权限被拒绝，返回首页")
                backToEntry()
            }
        }

    // 调用系统相机拍照
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && pendingImageUri != null) {
                openEditorWithPhoto(pendingImageUri!!)
            } else {
                // 用户取消 or 拍照失败
                showState("未拍摄照片，返回首页")
                backToEntry()
            }
        }

    // 用于保存图片的后台线程池（单线程即可）
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prepareAndStartCapture()
    }

    private fun prepareAndStartCapture() {
        if (!hasCamera()) {
            showState("设备不支持相机功能，返回首页")
            backToEntry()
            return
        }

        ensureCameraPermissionAndCapture()
    }

    private fun hasCamera(): Boolean {
        val pm = requireContext().packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun ensureCameraPermissionAndCapture() {
        val permission = MediaPermissionHelper.getCameraPermission()
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startCaptureFlow()
        } else {
            showState("正在请求相机权限...")
            requestCameraPermissionLauncher.launch(permission)
        }
    }

    private fun startCaptureFlow() {
        showState("正在打开相机...")

        // 向 MediaStore 申请一个可写入的 Uri
        val uri = createImageUri()
        if (uri == null) {
            showState("无法为照片创建存储位置，返回首页")
            backToEntry()
            return
        }

        // Uri 创建成功 - 拉起相机
        pendingImageUri = uri
        takePictureLauncher.launch(uri)
    }

    // 使用 MediaStore 创建一个图片 Uri，照片会保存到系统相册
    private fun createImageUri(): Uri? {
        val contentValues = ContentValues().apply {
            val fileName = "LE_${System.currentTimeMillis()}.jpg"
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            // 图片保存的位置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/LightEdit"
                )
            }
        }

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        return try {
            requireContext().contentResolver.insert(collection, contentValues)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 拍照成功 → 进入 EditorFragment 编辑刚拍的图片
    private fun openEditorWithPhoto(uri: Uri) {
        val fm = requireActivity().supportFragmentManager

        // 关键：先把当前的 CameraCaptureFragment 从 back stack 中移除，
        // 这样返回键就不会再回到它，而是直接回到 EntryFragment。
        fm.popBackStack()

        fm.beginTransaction()
            .replace(
                R.id.fragment_container_view,
                EditorFragment.newInstance(uri)
            )
            .addToBackStack(null) //让 Editor 能按返回回到 Entry
            .commit()
    }

    // 从当前 CameraCaptureFragment 回到首页（EntryFragment）
    private fun backToEntry() {
        // 延迟一点点，让用户看到提示文本再返回
        binding.tvCameraState.postDelayed({
            parentFragmentManager.popBackStack()
        }, 500)
    }



    private fun showState(msg: String) {
        binding.tvCameraState.text = msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
