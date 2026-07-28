package net.bdfz.recite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.bdfz.recite.ui.LangLangTheme
import net.bdfz.recite.ui.ReciteApp
import net.bdfz.recite.ui.ReciteViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ReciteViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onForeground()
            },
        )
        setContent {
            LangLangTheme {
                ReciteApp(
                    viewModel = viewModel,
                    windowSizeClass = calculateWindowSizeClass(this),
                )
            }
        }
    }
}
