package com.busalert.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.busalert.mobile.ui.MobileApp

/** Única Activity: la UI completa está en [MobileApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MobileApp((application as BusAlertMobileApp).appDependencies) }
    }
}
