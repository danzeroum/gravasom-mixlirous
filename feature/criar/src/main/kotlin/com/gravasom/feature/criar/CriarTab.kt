package com.gravasom.feature.criar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Descritor de aba do F6 (roteiro §3.1): nome, ícone e conteúdo. A [CriarScreen]
 * monta as cinco (Receita | Loops | Texturas | Mescla | Melhores) com esse contrato.
 */
data class CriarTab(
    val nome: String,
    val icone: androidx.compose.ui.graphics.vector.ImageVector,
    val conteudo: @Composable () -> Unit,
)

/**
 * Bloco comum às abas: progresso, resultado, distância de fingerprint, avisos
 * e o par Refazer/Voltar — mesma semântica em todas as cinco abas.
 */
@Suppress("LongParameterList")
@Composable
internal fun ResultadosCriacao(
    executando: Boolean,
    progresso: Float,
    resultado: String?,
    distancia: Double?,
    avisos: List<String>,
    podeRefazer: Boolean,
    onRefazer: () -> Unit,
    onVoltar: () -> Unit,
) {
    if (executando) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            Text(stringResource(R.string.criar_renderizando))
        }
    }
    if (progresso > 0f && executando) {
        LinearProgressIndicator(
            progress = { progresso },
            modifier = Modifier.fillMaxWidth().testTag("progresso_criar"),
        )
    }
    resultado?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("resultado_criar"))
    }
    distancia?.let {
        Text(
            stringResource(R.string.criar_distancia, it),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("distancia_fingerprint"),
        )
    }
    avisos.forEach { aviso ->
        AssistChip(onClick = {}, label = { Text(aviso) })
    }
    OutlinedButton(
        onClick = onRefazer,
        enabled = !executando && podeRefazer,
        modifier = Modifier.fillMaxWidth().testTag("botao_refazer"),
    ) {
        Text(stringResource(R.string.criar_refazer))
    }
    OutlinedButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.criar_voltar))
    }
}
