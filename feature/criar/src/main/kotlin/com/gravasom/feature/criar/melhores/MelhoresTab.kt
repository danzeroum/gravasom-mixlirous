package com.gravasom.feature.criar.melhores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.gravasom.feature.criar.R
import com.gravasom.feature.criar.ResultadosCriacao

/**
 * Aba Melhores (F6): maior energia até o alvo; os marcadores do usuário são
 * trechos OBRIGATÓRIOS. Duração alvo < soma dos trechos é recusada na aba.
 */
@Suppress("UnusedParameter")
@Composable
fun MelhoresTab(
    recordingId: Long,
    onVoltar: () -> Unit,
    viewModel: MelhoresMomentosViewModel = hiltViewModel(),
) {
    val duracao by viewModel.duracaoAlvoS.collectAsStateWithLifecycle()
    val quantidade by viewModel.quantidadeMarcadores.collectAsStateWithLifecycle()
    val erro by viewModel.erroValidacao.collectAsStateWithLifecycle()
    val executando by viewModel.executando.collectAsStateWithLifecycle()
    val progresso by viewModel.progresso.collectAsStateWithLifecycle()
    val resultado by viewModel.resultado.collectAsStateWithLifecycle()
    val distancia by viewModel.distancia.collectAsStateWithLifecycle()
    val avisos by viewModel.avisos.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.criar_melhores_desc), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.criar_melhores_marcadores, quantidade),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("melhores_marcadores_qtd"),
        )
        OutlinedTextField(
            value = duracao.toString(),
            onValueChange = { viewModel.duracaoAlvoS.value = it.toIntOrNull() ?: 0 },
            label = { Text(stringResource(R.string.criar_duracao)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("campo_duracao"),
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
            Text(stringResource(R.string.criar_melhores_acao))
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
