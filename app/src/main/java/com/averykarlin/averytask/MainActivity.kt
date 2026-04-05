package com.averykarlin.averytask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.averykarlin.averytask.ui.navigation.AveryTaskNavGraph
import com.averykarlin.averytask.ui.theme.AveryTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AveryTaskTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AveryTaskNavGraph(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
