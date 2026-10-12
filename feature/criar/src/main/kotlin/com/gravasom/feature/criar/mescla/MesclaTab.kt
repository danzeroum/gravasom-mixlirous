package com.gravasom.feature.criar.mescla

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.core.domain.model.Recording
import com.gravasom.feature.criar.R
import com.gravasom.feature.criar.ResultadosCriacao
import com.gravasom.feature.criar.TabCriacaoViewModel

/**
 * Aba Mescla (F6): trilha A (origem) + trilha B (da lista) + balance 0–1.
 * Tons incompatíveis mostram o aviso ANTES (HITL): a aba pergunta e re-enfileira.
 * [recordingId] é contrato do roteiro §3.4 — o ViewModel lê o id do SavedStateHandle.
 */
@Suppress("UnusedParameter")
@Composable
fun MesclaTab(
    recordingId: Long,
    onVoltar: () -> Unit,
    viewModel: MesclaViewModel = hiltViewModel(),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val trilhaBTitulo by viewModel.trilhaBTitulo.collectAsStateWithLifecycle()
    val candidatas by viewModel.candidatas.collectAsStateWithLifecycle()
    val erro by viewModel.erroValidacao.collectAsStateWithLifecycle()
    val recusa by viewModel.recusaDeTons.collectAsStateWithLifecycle()
    val executando by viewModel.executando.collectAsStateWithLifecycle()
    val progresso by viewModel.progresso.collectAsStateWithLifecycle()
    val resultado by viewModel.resultado.collectAsStateWithLifecycle()
    val distancia by viewModel.distancia.collectAsStateWithLifecycle()
    val avisos by viewModel.avisos.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.criar_mescla_desc), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.criar_mescla_trilha_b), style = MaterialTheme.typography.titleSmall)
        trilhaBTitulo?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("mescla_trilha_b_escolhida"))
        }
        ListaCandidatas(candidatas.take(LIMITE_LISTA), viewModel::escolherTrilhaB)
        Text(stringResource(R.string.criar_mescla_balance, (balance * 100).toInt()))
        Slider(
            value = balance,
            onValueChange = { viewModel.balance.value = it },
            valueRange = 0f..1f,
            modifier = Modifier.testTag("campo_balance"),
        )
        erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("erro_validacao"))
        }
        Button(
            onClick = viewModel::iniciarCriacao,
            enabled = !executando,
            modifier = Modifier.fillMaxWidth().testTag("botao_criar"),
        ) {
            Text(stringResource(R.string.criar_mescla_acao))
        }
        ResultadosCriacao(
            executando = executando,
            progresso = progresso,
            resultado = resultado,
            distancia = distancia,
            avisos = avisos,
            podeRefazer = false, // mescla re-renderiza pela confirmação de tons, não por "refazer igual"
            onRefazer = {},
            onVoltar = onVoltar,
        )
    }

    // ——— HITL: "Tons incompatíveis: continuar mesmo assim?" ———
    recusa?.let { DialogoRecusaTons(it, viewModel) }
}

/** Diálogo de confirmação da recusa por tons (HITL do roteiro). */
@Composable
private fun DialogoRecusaTons(recusa: TabCriacaoViewModel.RecusaDeTons, viewModel: MesclaViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::consumirRecusa,
        title = { Text(stringResource(R.string.criar_mescla_recusa_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(recusa.mensagem)
                recusa.avisos.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.consumirRecusa()
                    viewModel.confirmarTonsIncompativeis()
                },
                modifier = Modifier.testTag("mescla_confirmar_seg"),
            ) { Text(stringResource(R.string.criar_mescla_recusa_confirmar)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::consumirRecusa) { Text(stringResource(R.string.criar_cancelar)) }
        },
    )
}

/** Lista curta de gravações candidatas a trilha B. */
@Composable
private fun ListaCandidatas(itens: List<Recording>, onEscolher: (Recording) -> Unit) {
    Column(Modifier.heightIn(max = 220.dp)) {
        itens.forEach { r ->
            ListItem(
                headlineContent = { Text(r.title) },
                supportingContent = { Text("${r.durationMillis / 1000} s · ${r.format.name}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp) // alvo de toque ≥ 48 dp
                    .clickable { onEscolher(r) }
                    .testTag("mescla_candidata_${r.id}"),
            )
            HorizontalDivider(Modifier.padding(horizontal = 4.dp))
        }
    }
}

private const val LIMITE_LISTA = 20
