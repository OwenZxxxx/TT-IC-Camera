package com.owenzx.lightedit.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.owenzx.lightedit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 验证：改一下 TextView 文本（假设布局里有一个 TextView）
        // 如果默认模板是 "Hello World!" 的 TextView，id 一般是 textView
        binding.textView.text = "LightEdit - Home"

    }
}