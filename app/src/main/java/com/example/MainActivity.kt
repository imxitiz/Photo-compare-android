package com.example

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.prismdiff.feature.navigation.PrismDiffAppShell
import com.example.prismdiff.feature.studio.PrismDiffViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: PrismDiffViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PrismDiffViewModel(applicationContext) as T
                }
            },
        )[PrismDiffViewModel::class.java]

        val isShare = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE
        if (isShare) {
            viewModel.handleIncomingShareIntent(intent)
        }

        setContent {
            PrismDiffAppShell(
                viewModel = viewModel,
                launchedWithShareIntent = isShare,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            viewModel.handleIncomingShareIntent(intent)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::viewModel.isInitialized && viewModel.state.value.preferences.volumeKeyNavigation) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    viewModel.stepSlotB(-1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    viewModel.stepSlotB(1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
