package com.itg.rnhostsample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.itg.rnbussiness.ItgRnBusiness

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Button(context).apply {
                text = "Fabric + TurboModule Demo"
                setOnClickListener { ItgRnBusiness.openNativeDemo(this@MainActivity) }
            })
            addView(Button(context).apply {
                text = "Order Detail"
                setOnClickListener { ItgRnBusiness.openOrderDetail(this@MainActivity, "ITG-1001") }
            })
        })
    }
}
