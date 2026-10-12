package com.gravasom.core.service.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import android.content.Intent
import com.gravasom.core.service.R
import com.gravasom.core.audio.engine.RecordingState
import com.gravasom.core.service.RecordingService
import com.gravasom.core.service.RecordingServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Widget Glance com três alvos: Gravar (inicia a gravacao), Marcar (marca o instante) e Criar versão (F8). */
class RecordWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val startAction = actionStartService(serviceIntent(context, RecordingService.ACTION_START), isForegroundService = true)
        // Marcar passa por um callback: sem gravacao em curso mostra um aviso curto (antes o toque era ignorado em silencio).
        val markerAction = actionRunCallback<MarkerActionCallback>()
        // F8: "Criar versão" abre a biblioteca — o fluxo de criação (F6) precisa de uma
        // gravação de origem; 1 toque na gravação → ⋮ → Criar versão.
        val criarAction = actionStartActivity(
            Intent(ACTION_QUICK_CRIAR).setClassName(context, ACTIVITY_CRIAR),
        )
        Row(modifier = GlanceModifier.fillMaxSize().background(Color(0xFF12151A))) {
            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(startAction), contentAlignment = Alignment.Center) {
                Text(text = context.getString(R.string.widget_record), style = TextStyle(color = ColorProvider(Color.White)))
            }
            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(markerAction), contentAlignment = Alignment.Center) {
                Text(text = context.getString(R.string.widget_marker), style = TextStyle(color = ColorProvider(Color(MARKER_COLOR))))
            }
            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(criarAction), contentAlignment = Alignment.Center) {
                Text(text = context.getString(R.string.widget_criar), style = TextStyle(color = ColorProvider(Color(CRIAR_COLOR))))
            }
        }
    }

    private fun serviceIntent(context: Context, action: String): Intent =
        Intent(context, RecordingService::class.java).setAction(action)

    private companion object {
        const val MARKER_COLOR = 0xFFFFC107
        const val CRIAR_COLOR = 0xFFB388FF
        const val ACTION_QUICK_CRIAR = "com.gravasom.action.QUICK_CRIAR"
        const val ACTIVITY_CRIAR = "com.gravasom.MainActivity"
    }
}

/** Toque em "Marcar" do widget: marca a gravacao em curso ou avisa que nao ha nenhuma. */
class MarkerActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.Main) { markFromWidget(context, RecordingServiceState.state.value) }
    }
}

/**
 * Com gravacao (ou pausada), pede o marcador ao servico, que ja esta em primeiro plano. Sem gravacao, um aviso curto: antes o toque
 * era ignorado em silencio e ninguem sabia se tinha marcado (avaliacao heuristica da Onda 1).
 */
internal fun markFromWidget(context: Context, state: RecordingState) {
    if (state is RecordingState.Recording || state is RecordingState.Paused) {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_MARKER))
    } else {
        android.widget.Toast.makeText(context, R.string.widget_marker_idle, android.widget.Toast.LENGTH_SHORT).show()
    }
}
