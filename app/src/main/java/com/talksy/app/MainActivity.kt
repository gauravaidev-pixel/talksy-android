package com.talksy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.talksy.app.ui.nav.RootNav
import com.talksy.app.ui.theme.TalksyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by AppGraph.session.themeFlow.collectAsState(initial = "system")
            TalksyTheme(mode) {
                RootNav()
            }
        }
    }
}
