package com.gravasom.feature.criar.loop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.feature.criar.CriarSonsUseCases
import com.gravasom.feature.criar.R
import com.gravasom.feature.criar.ResultadosCriacao

/**
 * Aba Loops (F6): compassos, repetições (2–50) e fade (≤ 2 s por emenda).
 * O motor fecha a emenda sem clique; as repetições escrevem o ciclo N vezes.
 * [recordingId] é contrato do roteiro §3.4 — o ViewModel lê o id do SavedStateHandle.
 */
@Suppress("UnusedParameter")
@Composable
fun LoopTab(
    recordingId: Long,
    onVoltar: () -> Unit,
    viewModel: LoopViewModel = hiltViewModel(),
) {
    val erro by viewModel.erroValidacao.collectAsStateWithLifecycle()
    val executando by viewModel.executando.collectAsStateWithLifecycle()
    val progresso by viewModel.progresso.collectAsStateWithLifecycle()
    val resultado by viewModel.resultado.collectAsStateWithLifecycle()
    val distancia by viewModel.distancia.collectAsStateWithLifecycle()
    val avisos by viewModel.avisos.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.criar_loop_desc), style = MaterialTheme.typography.bodySmall)
        ParametrosDoLoop(viewModel)
        erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("erro_validacao"))
        }
        Button(
            onClick = viewModel::iniciarCriacao,
            enabled = !executando,
            modifier = Modifier.fillMaxWidth().testTag("botao_criar"),
        ) {
            Text(stringResource(R.string.criar_loop_acao))
        }
        ResultadosCriacao(
            executando = executando,
            progresso = progresso,
            resultado = resultado,
            distancia = distancia,
            avisos = avisos,
            podeRefazer = viewModel.fingerprintAnterior.value != null,
            onRefazer = viewModel::refazerIgual,
            onVoltar = onVoltar,
        )
    }
}

@Composable
private fun ParametrosDoLoop(viewModel: LoopViewModel) {
    val compassos by viewModel.compassos.collectAsStateWithLifecycle()
    val repeticoes by viewModel.repeticoes.collectAsStateWithLifecycle()
    val fadeMs by viewModel.fadeMs.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = compassos.toString(),
            onValueChange = { viewModel.compassos.value = it.toIntOrNull() ?: 0 },
            label = { Text(stringResource(R.string.criar_compassos)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("campo_compassos"),
        )
        Text(stringResource(R.string.criar_loop_repeticoes, repeticoes))
        Slider(
            value = repeticoes.toFloat(),
            onValueChange = { viewModel.repeticoes.value = it.toInt() },
            valueRange = CriarSonsUseCases.REPETICOES_MIN.toFloat()..CriarSonsUseCases.REPETICOES_MAX.toFloat(),
            steps = CriarSonsUseCases.REPETICOES_MAX - CriarSonsUseCases.REPETICOES_MIN - 1,
            modifier = Modifier.testTag("campo_repeticoes"),
        )
        Text(stringResource(R.string.criar_loop_fade, fadeMs))
        Slider(
            value = fadeMs.toFloat(),
            onValueChange = { viewModel.fadeMs.value = it.toLong() },
            valueRange = 0f..CriarSonsUseCases.FADE_MAX_MS.toFloat(), // limite próprio do loop
            modifier = Modifier.testTag("campo_fade"),
        )
    }
}
