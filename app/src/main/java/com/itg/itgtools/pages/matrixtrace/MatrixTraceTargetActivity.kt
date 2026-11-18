package com.itg.itgtools.pages.matrixtrace

import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.itg.itgtools.databinding.ActivityMatrixTraceTargetBinding

class MatrixTraceTargetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 故意放慢目标页创建，让 Activity 暂停到目标页首帧的耗时更容易观察。
        SystemClock.sleep(350)
        val binding = ActivityMatrixTraceTargetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.goBack.setOnClickListener { finish() }
    }
}
