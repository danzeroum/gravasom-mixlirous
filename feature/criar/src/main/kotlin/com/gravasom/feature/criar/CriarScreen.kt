package com.gravasom.feature.criar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.core.service.mix.CriacaoWorker

/**
 * Tela de receita (Gate 4): duração alvo, blocos por batida, crossfade e
 * destino; crossfade > 3000 ms é recusado com mensagem clara; "Refazer igual"
 * re-renderiza com o mesmo som (fingerprint < 0,05).
 */
@Composable
fun CriarScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CriarSonsViewModel = hiltViewModel(),
) {
    val modo by viewModel.modo.collectAsStateWithLifecycle()
    val duracao by viewModel.duracaoSegundos.collectAsStateWithLifecycle()
    val compassos by viewModel.compassos.collectAsStateWithLifecycle()
    val fator by viewModel.fator.collectAsStateWithLifecycle()
    val crossfade by viewModel.crossfadeMs.collectAsStateWithLifecycle()
    val blocos by viewModel.blocosPorBatida.collectAsStateWithLifecycle()
    val destino by viewModel.destino.collectAsStateWithLifecycle()
    val erroValidacao by viewModel.erroValidacao.collectAsStateWithLifecycle()
    val executando by viewModel.executando.collectAsStateWithLifecycle()
    val progresso by viewModel.progresso.collectAsStateWithLifecycle()
    val resultado by viewModel.resultado.collectAsStateWithLifecycle()
    val etiqueta by viewModel.etiquetaOrigem.collectAsStateWithLifecycle()
    val distancia by viewModel.distancia.collectAsStateWithLifecycle()
    val avisos by viewModel.avisos.collectAsStateWithLifecycle()
    val erros by viewModel.erros.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(erros) {
        erros?.let { snackbar.showSnackbar(it); viewModel.consumirErro() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.criar_titulo), style = MaterialTheme.typography.headlineSmall)
        etiqueta?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("etiqueta_mix"),
            )
        }

        // ——— Modo ———
        Text(stringResource(R.string.criar_modo), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = modo == CriacaoWorker.MODO_REMIX,
                onClick = { viewModel.modo.value = CriacaoWorker.MODO_REMIX },
                label = { Text(stringResource(R.string.criar_modo_remix)) },
            )
            FilterChip(
                selected = modo == CriacaoWorker.MODO_LOOP,
                onClick = { viewModel.modo.value = CriacaoWorker.MODO_LOOP },
                label = { Text(stringResource(R.string.criar_modo_loop)) },
            )
            FilterChip(
                selected = modo == CriacaoWorker.MODO_TEXTURA,
                onClick = { viewModel.modo.value = CriacaoWorker.MODO_TEXTURA },
                label = { Text(stringResource(R.string.criar_modo_textura)) },
            )
            FilterChip(
                selected = modo == CriacaoWorker.MODO_MELHORES,
                onClick = { viewModel.modo.value = CriacaoWorker.MODO_MELHORES },
                label = { Text(stringResource(R.string.criar_modo_melhores)) },
            )
        }

        // ——— Duração alvo (remix / melhores) ———
        if (modo == CriacaoWorker.MODO_REMIX || modo == CriacaoWorker.MODO_MELHORES) {
            OutlinedTextField(
                value = duracao.toString(),
                onValueChange = { viewModel.duracaoSegundos.value = it.toIntOrNull() ?: 0 },
                label = { Text(stringResource(R.string.criar_duracao)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("campo_duracao"),
            )
        }

        // ——— Compassos (loop) ———
        if (modo == CriacaoWorker.MODO_LOOP) {
            OutlinedTextField(
                value = compassos.toString(),
                onValueChange = { viewModel.compassos.value = it.toIntOrNull() ?: 0 },
                label = { Text(stringResource(R.string.criar_compassos)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("campo_compassos"),
            )
        }

        // ——— Fator (textura 2×–8×) ———
        if (modo == CriacaoWorker.MODO_TEXTURA) {
            Text(stringResource(R.string.criar_fator, fator.toInt()))
            Slider(
                value = fator,
                onValueChange = { viewModel.fator.value = (it.toInt() + if (it - it.toInt() > 0.5f) 1 else 0).toFloat() },
                valueRange = 2f..8f,
                steps = 5,
                modifier = Modifier.testTag("campo_fator"),
            )
        }

        // ——— Blocos por batida ———
        if (modo == CriacaoWorker.MODO_REMIX) {
            Text(stringResource(R.string.criar_blocos, blocos))
            Slider(
                value = blocos.toFloat(),
                onValueChange = { viewModel.blocosPorBatida.value = it.toInt() + 1 },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.testTag("campo_blocos"),
            )
        }

        // ——— Crossfade (0–3000 ms; acima disso a ValidationLayer recusa) ———
        Text(stringResource(R.string.criar_crossfade, crossfade))
        Slider(
            value = crossfade.toFloat(),
            onValueChange = { viewModel.crossfadeMs.value = it.toLong() },
            valueRange = 0f..5000f, // deixa IR além do limite DE PROPÓSITO: a recusa é o gate
            modifier = Modifier.testTag("campo_crossfade"),
        )
        erroValidacao?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("erro_validacao"),
            )
        }

        // ——— Destino ———
        Text(stringResource(R.string.criar_destino), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("reels", "shorts", "streaming", "voz").forEach { alvo ->
                FilterChip(
                    selected = destino == alvo,
                    onClick = { viewModel.destino.value = alvo },
                    label = { Text(alvo.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        // ——— Ações ———
        Button(
            onClick = { viewModel.iniciarCriacao() },
            enabled = !executando,
            modifier = Modifier.fillMaxWidth().testTag("botao_criar"),
        ) {
            Text(stringResource(R.string.criar_acao))
        }
        OutlinedButton(
            onClick = { viewModel.refazerIgual() },
            enabled = !executando && viewModel.fingerprintAnterior.value != null,
            modifier = Modifier.fillMaxWidth().testTag("botao_refazer"),
        ) {
            Text(stringResource(R.string.criar_refazer))
        }

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

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.criar_voltar))
        }
    }
    SnackbarHost(hostState = snackbar)
}
