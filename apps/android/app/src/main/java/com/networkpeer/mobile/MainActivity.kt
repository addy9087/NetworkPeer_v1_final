package com.networkpeer.mobile

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.networkpeer.mobile.ui.NetworkPeerApp
import com.networkpeer.mobile.ui.theme.NetworkPeerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NetworkPeerApplication).container
        container.handleDeepLink(intent?.data)
        setContent {
            NetworkPeerTheme {
                NetworkPeerApp(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as NetworkPeerApplication).container.handleDeepLink(intent.data)
    }
}
