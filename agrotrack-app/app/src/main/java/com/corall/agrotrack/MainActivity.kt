package com.corall.agrotrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.corall.agrotrack.core.navigation.AppNavGraph
import com.corall.agrotrack.presentation.common.theme.AgroTrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgroTrackTheme {
                AppNavGraph()
            }
        }
    }
}
