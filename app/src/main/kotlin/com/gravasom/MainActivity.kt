package com.gravasom

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.gravasom.core.data.metrics.UsageMetrics
import com.gravasom.core.domain.metrics.UsageEventType
import com.gravasom.core.service.library.AudioImporter
import com.gravasom.core.service.library.ImportResult
import com.gravasom.core.ui.GravasomTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var importer: AudioImporter
    @Inject lateinit var metrics: UsageMetrics

    private val destination = mutableStateOf<String?>(null)
    private val autoStart = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GravasomTheme {
                GravasomApp(
                    destination = destination.value,
                    onDestinationHandled = { destination.value = null },
                    autoStart = autoStart.value,
                    onAutoStartHandled = { autoStart.value = false },
                )
            }
        }
        if (savedInstanceState == null) {
            lifecycleScope.launch { metrics.record(UsageEventType.APP_ABERTO) }
            handle(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Entradas externas: atalhos do launcher e audio recebido de outros apps (ACTION_SEND). */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_RECORD -> { destination.value = ROUTE_RECORD; autoStart.value = true }
            ACTION_SHOW_LIBRARY -> destination.value = ROUTE_LIBRARY
            Intent.ACTION_SEND -> if (intent.type?.startsWith("audio/") == true) receiveAudio(streamOf(intent))
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun receiveAudio(uri: Uri?) {
        if (uri == null) return
        lifecycleScope.launch {
            val message = when (importer.import(uri)) {
                is ImportResult.Imported -> { destination.value = ROUTE_LIBRARY; com.gravasom.feature.library.R.string.library_import_ok }
                ImportResult.Unsupported -> com.gravasom.feature.library.R.string.library_import_unsupported
                ImportResult.Invalid -> com.gravasom.feature.library.R.string.library_import_invalid
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_QUICK_RECORD = "com.gravasom.action.QUICK_RECORD"
        const val ACTION_SHOW_LIBRARY = "com.gravasom.action.SHOW_LIBRARY"
        const val ROUTE_RECORD = "gravar"
        const val ROUTE_LIBRARY = "gravacoes"
        const val ROUTE_SETTINGS = "ajustes"
        const val ROUTE_NUMBERS = "numeros"
        const val ROUTE_ENHANCE = "melhorar/{recordingId}"
        const val ROUTE_CRIAR = "criar/{recordingId}"
    }
}
