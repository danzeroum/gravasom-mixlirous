package com.gravasom.feature.transcricao

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.core.data.modelmanager.ModeloInfo
import kotlinx.coroutines.delay

/**
 * Tela de transcrição local (F7): consentimento do modelo (ADR-017) → download/importação →
 * inferência whisper com progresso → resumo EDITÁVEL → salvar como anotação (Gate 7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscricaoScreen(
    recordingId: Long,
    onDone: () -> Unit,
    viewModel: TranscricaoViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var comTimestamps by remember { mutableStateOf(true) }
    var confirmandoRemocao by remember { mutableStateOf(false) }

    // ——— Importação SAF do modelo (plano B offline, ADR-017 §2) ———
    val importarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importarModelo(uri)
    }

    LaunchedEffect(Unit) { viewModel.avaliarDisponibilidade() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transcricao_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onDone, modifier = Modifier.testTag("transcricao_fechar")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.transcricao_fechar))
                    }
                },
                actions = {
                    if (estado is TranscricaoEstado.Pronto) {
                        IconButton(onClick = { confirmandoRemocao = true }, modifier = Modifier.testTag("transcricao_remover_modelo")) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.transcricao_remover_modelo_menu))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val e = estado) {
                is TranscricaoEstado.Consentimento -> ConteudoConsentimento(
                    estado = e,
                    catalogo = viewModel.catalogo,
                    embutidos = viewModel.embutidos,
                    onSelecionar = viewModel::selecionarModelo,
                    onAceitar = viewModel::aceitarConsentimento,
                    onImportar = { importarLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    onRecusar = onDone,
                )
                is TranscricaoEstado.Baixando -> ConteudoBaixando(e, viewModel::cancelar)
                is TranscricaoEstado.Instalando -> ConteudoOcupado(
                    titulo = stringResource(R.string.transcricao_instalando, e.progresso),
                    decorridoMs = null,
                    onCancelar = viewModel::cancelar,
                    progresso = e.progresso / 100f,
                )
                is TranscricaoEstado.Pronto -> ConteudoPronto(e, comTimestamps, onTimestamps = { comTimestamps = it }, onTranscrever = {
                    viewModel.iniciarTranscricao(recordingId, comTimestamps = comTimestamps)
                })
                is TranscricaoEstado.Preparando -> ConteudoOcupado(
                    titulo = stringResource(R.string.transcricao_preparando),
                    decorridoMs = null,
                    onCancelar = viewModel::cancelar,
                )
                is TranscricaoEstado.Transcrevendo -> ConteudoTranscrevendo(e, viewModel::cancelar)
                is TranscricaoEstado.Sucesso -> ConteudoSucesso(
                    estado = e,
                    onSalvar = { textoEditado ->
                        viewModel.salvar(recordingId, TranscricaoUseCase.TITULO_PADRAO, textoEditado)
                    },
                    onFechar = onDone,
                )
                is TranscricaoEstado.Erro -> ConteudoErro(
                    mensagem = e.mensagem,
                    onTentarNovamente = { viewModel.tentarNovamente(recordingId, comTimestamps) },
                    onFechar = onDone,
                )
            }
        }
    }

    if (confirmandoRemocao) {
        AlertDialog(
            onDismissRequest = { confirmandoRemocao = false },
            title = { Text(stringResource(R.string.transcricao_remover_modelo_titulo)) },
            text = { Text(stringResource(R.string.transcricao_remover_modelo_texto)) },
            confirmButton = {
                TextButton(onClick = { confirmandoRemocao = false; viewModel.removerModelo() }) {
                    Text(stringResource(R.string.transcricao_remover_modelo_menu))
                }
            },
            dismissButton = { TextButton(onClick = { confirmandoRemocao = false }) { Text(stringResource(R.string.transcricao_fechar)) } },
        )
    }
}

@Composable
private fun ConteudoConsentimento(
    estado: TranscricaoEstado.Consentimento,
    catalogo: List<ModeloInfo>,
    embutidos: Set<String>,
    onSelecionar: (ModeloInfo) -> Unit,
    onAceitar: () -> Unit,
    onImportar: () -> Unit,
    onRecusar: () -> Unit,
) {
    val modeloEmbutido = estado.modeloSelecionado.nome in embutidos
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.transcricao_consentimento_titulo),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (modeloEmbutido) {
                stringResource(R.string.transcricao_consentimento_texto_embutido, estado.modeloSelecionado.rotulo)
            } else {
                stringResource(R.string.transcricao_consentimento_texto, estado.modeloSelecionado.rotulo)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        catalogo.forEach { modelo ->
            val noApp = modelo.nome in embutidos
            Row(
                Modifier.fillMaxWidth().testTag("modelo_${modelo.nome}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = modelo.nome == estado.modeloSelecionado.nome,
                    onClick = { onSelecionar(modelo) },
                )
                Text(
                    if (noApp) {
                        "${modelo.rotulo} · " + stringResource(R.string.transcricao_consentimento_embutido_sufixo)
                    } else {
                        modelo.rotulo
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            stringResource(
                R.string.transcricao_consentimento_origem,
                if (modeloEmbutido) stringResource(R.string.transcricao_origem_embutido) else estado.modeloSelecionado.url,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.transcricao_consentimento_sha, estado.modeloSelecionado.sha256.take(16) + "…"),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onAceitar, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transcricao_aceitar")) {
            Text(
                if (modeloEmbutido) {
                    stringResource(R.string.transcricao_consentimento_aceitar_embutido, "${estado.modeloSelecionado.tamanhoMb.toInt()} MB")
                } else {
                    stringResource(R.string.transcricao_consentimento_aceitar, "${estado.modeloSelecionado.tamanhoMb.toInt()} MB")
                },
            )
        }
        OutlinedButton(onClick = onImportar, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transcricao_importar")) {
            Text(stringResource(R.string.transcricao_consentimento_importar))
        }
        TextButton(onClick = onRecusar, modifier = Modifier.fillMaxWidth().testTag("transcricao_recusar")) {
            Text(stringResource(R.string.transcricao_consentimento_recusar))
        }
    }
}

@Composable
private fun ConteudoBaixando(estado: TranscricaoEstado.Baixando, onCancelar: () -> Unit) {
    ConteudoOcupado(
        titulo = stringResource(R.string.transcricao_baixando, estado.progresso),
        decorridoMs = null,
        onCancelar = onCancelar,
        progresso = estado.progresso / 100f,
    )
}

@Composable
private fun ConteudoPronto(
    estado: TranscricaoEstado.Pronto,
    comTimestamps: Boolean,
    onTimestamps: (Boolean) -> Unit,
    onTranscrever: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.transcricao_pronto, estado.modelo.nome), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.transcricao_pronto_tamanho, estado.modelo.sha256.take(16) + "…"),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.transcricao_timestamps), Modifier.weight(1f))
            Switch(checked = comTimestamps, onCheckedChange = onTimestamps, modifier = Modifier.testTag("transcricao_switch_timestamps"))
        }
        Button(
            onClick = onTranscrever,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transcricao_iniciar"),
        ) {
            Text(stringResource(R.string.transcricao_botao_transcrever))
        }
    }
}

@Composable
private fun ConteudoOcupado(titulo: String, decorridoMs: Long?, onCancelar: () -> Unit, progresso: Float? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (progresso != null) LinearProgressIndicator(progress = { progresso }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (decorridoMs != null) Text("%.1f s".format(decorridoMs / 1000f), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onCancelar, modifier = Modifier.height(48.dp).testTag("transcricao_cancelar")) {
            Text(stringResource(R.string.transcricao_cancelar))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ConteudoTranscrevendo(estado: TranscricaoEstado.Transcrevendo, onCancelar: () -> Unit) {
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(estado.inicioElapsedMs) {
        while (true) {
            delay(500)
            agora = SystemClock.elapsedRealtime()
        }
    }
    ConteudoOcupado(
        titulo = stringResource(R.string.transcricao_transcrevendo, ""),
        decorridoMs = (agora - estado.inicioElapsedMs).coerceAtLeast(0L),
        onCancelar = onCancelar,
    )
}

@Composable
private fun ConteudoSucesso(
    estado: TranscricaoEstado.Sucesso,
    onSalvar: (String) -> Unit,
    onFechar: () -> Unit,
) {
    var textoEditado by remember(estado.resultado.resumo) { mutableStateOf(estado.resultado.resumo) }
    val salvo = estado.salvoId != null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.transcricao_sucesso_resumo), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = textoEditado,
            onValueChange = { textoEditado = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("transcricao_resumo_editavel"),
            minLines = 6,
        )
        Text(stringResource(R.string.transcricao_sucesso_bruto), style = MaterialTheme.typography.titleSmall)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag("transcricao_texto_bruto"),
        ) {
            Text(
                estado.resultado.raw.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            stringResource(
                R.string.transcricao_sucesso_metrica,
                "%.1f s".format(estado.resultado.duracaoInferenciaMs / 1000f),
                estado.resultado.tokens,
                estado.resultado.modeloNome,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { onSalvar(textoEditado) },
            enabled = !salvo,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transcricao_salvar"),
        ) {
            Text(if (salvo) stringResource(R.string.transcricao_salvo) else stringResource(R.string.transcricao_salvar))
        }
        OutlinedButton(onClick = onFechar, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.transcricao_fechar))
        }
    }
}

@Composable
private fun ConteudoErro(mensagem: String, onTentarNovamente: () -> Unit, onFechar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.transcricao_erro), style = MaterialTheme.typography.titleMedium)
        Text(mensagem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onTentarNovamente, modifier = Modifier.height(48.dp).testTag("transcricao_tentar_novamente")) {
            Text(stringResource(R.string.transcricao_tentar_novamente))
        }
        OutlinedButton(onClick = onFechar, modifier = Modifier.height(48.dp)) {
            Text(stringResource(R.string.transcricao_fechar))
        }
        Spacer(Modifier.weight(1f))
    }
}
