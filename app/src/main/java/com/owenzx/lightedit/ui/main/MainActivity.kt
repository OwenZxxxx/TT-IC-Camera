package com.owenzx.lightedit.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.owenzx.lightedit.databinding.ActivityMainBinding
import com.owenzx.lightedit.ui.entry.EntryFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 只在首次创建 Activity 时添加 Fragment（避免旋转屏幕重复叠加）
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(binding.fragmentContainerView.id, EntryFragment())
                .commit()
        }

    }
}