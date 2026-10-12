package com.gravasom.feature.criar.textura

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.feature.criar.R
import com.gravasom.feature.criar.CriarSonsUseCases
import com.gravasom.feature.criar.ResultadosCriacao

/** Aba Texturas (F6): fator 2×–8× com fechamento em loop (Natureza/Água). */
@Suppress("UnusedParameter")
@Composable
fun TexturaTab(
    recordingId: Long,
    onVoltar: () -> Unit,
    viewModel: TexturaViewModel = hiltViewModel(),
) {
    val fator by viewModel.fator.collectAsStateWithLifecycle()
    val erro by viewModel.erroValidacao.collectAsStateWithLifecycle()
    val executando by viewModel.executando.collectAsStateWithLifecycle()
    val progresso by viewModel.progresso.collectAsStateWithLifecycle()
    val resultado by viewModel.resultado.collectAsStateWithLifecycle()
    val distancia by viewModel.distancia.collectAsStateWithLifecycle()
    val avisos by viewModel.avisos.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.criar_textura_desc), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.criar_fator, fator.toInt()))
        Slider(
            value = fator,
            onValueChange = { viewModel.fator.value = (it.toInt() + if (it - it.toInt() > 0.5f) 1 else 0).toFloat() },
            valueRange = CriarSonsUseCases.FATOR_MIN..CriarSonsUseCases.FATOR_MAX,
            steps = 5,
            modifier = Modifier.testTag("campo_fator"),
        )
        erro?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("erro_validacao"),
            )
        }
        Button(
            onClick = viewModel::iniciarCriacao,
            enabled = !executando,
            modifier = Modifier.fillMaxWidth().testTag("botao_criar"),
        ) {
            Text(stringResource(R.string.criar_textura_acao))
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
