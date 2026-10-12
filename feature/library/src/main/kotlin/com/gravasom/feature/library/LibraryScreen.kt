package com.gravasom.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.service.clip.JoinOutcome
import com.gravasom.feature.player.PlayerController
import com.gravasom.feature.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenEnhance: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCriar: (Long) -> Unit = {},
    onTranscrever: (Long) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val items by viewModel.recordings.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val selectedTags by viewModel.selectedTags.collectAsStateWithLifecycle()
    val mixAnalises by viewModel.mixAnalises.collectAsStateWithLifecycle()
    val filtroBpm by viewModel.filtroBpm.collectAsStateWithLifecycle()
    val filtroTom by viewModel.filtroTom.collectAsStateWithLifecycle()
    val tomsDisponiveis by viewModel.tomsDisponiveis.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // ——— F6/Importação: picker SAF — porta única de leitura do armazenamento ———
    val importPicker = ImportPicker(viewModel, snackbar)
    val player = rememberPlayer()
    var currentId by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var selecao by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var confirmandoJuncao by remember { mutableStateOf(false) }
    val actions = rememberRowActions(
        viewModel, playerViewModel, player, snackbar,
        PlayerSlot({ currentId }, { currentId = it }),
        onRename = { renaming = it },
        onEnhance = onOpenEnhance,
        onCriar = onOpenCriar,
        onTranscrever = onTranscrever,
    )
    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchRow(query, viewModel::setQuery, onImportar = { importPicker.launch(MIMES_DE_AUDIO) })
            TagFilterRow(availableTags, selectedTags, viewModel::toggleTag)
            MixFilterRow(filtroBpm, filtroTom, tomsDisponiveis, viewModel::setFiltroBpm, viewModel::setFiltroTom)
            SelectionBar(
                selecao = selecao,
                onJuntar = { confirmandoJuncao = true },
                onLimpar = { selecao = emptyList() },
            )
            Box(Modifier.weight(1f)) {
                RecordingList(
                    items,
                    ListUi(query, selectedTags, viewModel::clearTags),
                    currentId,
                    actions,
                    selecao,
                    mixAnalises.mapValues { it.value.etiqueta ?: "" },
                ) { r ->
                    selecao = if (r.id in selecao.map { it.id }) selecao.filterNot { it.id == r.id } else selecao + r
                }
            }
            // A gravacao aberta e sempre lida da lista: renomear ou mudar etiquetas aparece no painel sem reabrir.
            PlayerDockSeAberto(items, currentId, player, playerViewModel, onOpenEnhance, onFechar = { currentId = null; playerViewModel.close() })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    PlayerEventsEffect(playerViewModel, snackbar)
    DialogosDaLista(
        renaming, viewModel, snackbar,
        onRenameConcluido = { renaming = null },
        confirmandoJuncao, selecao,
        onJuncaoConcluida = { confirmandoJuncao = false; selecao = emptyList() },
    )
}

/** O dialogo de juncao pendente: confirma a ordem, roda e avisa no snackbar. */
@Composable
private fun JuncaoPendente(
    confirmando: Boolean,
    selecao: List<Recording>,
    viewModel: LibraryViewModel,
    snackbar: SnackbarHostState,
    onConcluir: () -> Unit,
) {
    if (!confirmando) return
    val scope = rememberCoroutineScope()
    val joinOk = stringResource(R.string.library_join_ok)
    val joinFail = stringResource(R.string.library_join_failed)
    JoinDialog(
        selecao,
        onDismiss = onConcluir,
        onConfirm = { r ->
            viewModel.join(r) { resultado ->
                scope.launch {
                    snackbar.showSnackbar(if (resultado is JoinOutcome.Saved) joinOk.format(resultado.title) else joinFail)
                }
            }
            onConcluir()
        },
    )
}

/** Icone a esquerda da linha: marcacao no modo de selecao; play/pause no resto. */
@Composable
private fun LeadIcon(selecionando: Boolean, marcada: Boolean, playing: Boolean, playDesc: String) {
    if (selecionando) {
        Icon(
            if (marcada) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = stringResource(R.string.library_select_item),
            tint = if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("marca_selecao"),
        )
    } else {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playDesc)
    }
}

/** Barra da selecao multipla (Onda 2, "juntar"): quantas estao marcadas, o botao de juntar e o de sair. */
@Composable
private fun SelectionBar(selecao: List<Recording>, onJuntar: () -> Unit, onLimpar: () -> Unit) {
    if (selecao.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("barra_selecao"),
    ) {
        Text(
            pluralStringResource(R.plurals.library_join_selected, selecao.size, selecao.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onJuntar, enabled = selecao.size >= 2, modifier = Modifier.testTag("botao_juntar")) {
            Text(stringResource(R.string.library_join))
        }
        IconButton(onClick = onLimpar, modifier = Modifier.testTag("limpar_selecao")) {
            Icon(Icons.Filled.Clear, stringResource(R.string.library_join_clear))
        }
    }
}

/** Confirmacao da juncao: mostra a ordem (a gravacao nova tera os audios nessa ordem) e avisa que nada some. */
@Composable
private fun JoinDialog(selecao: List<Recording>, onDismiss: () -> Unit, onConfirm: (List<Recording>) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_join_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.library_join_dialog_hint), style = MaterialTheme.typography.bodySmall)
                selecao.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ", style = MaterialTheme.typography.bodyMedium)
                        Text(r.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selecao) }, modifier = Modifier.testTag("confirmar_juntar")) {
                Text(stringResource(R.string.library_join))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancelar_juntar")) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

@Composable
private fun rememberPlayer(): PlayerController {
    val context = LocalContext.current
    // Sair da aba tambem nao interrompe o audio: o ExoPlayer e o do PlaybackHub, nao deste controlador.
    val player = remember { PlayerController(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}

/** A gravacao aberta no painel: leitura e troca, agrupadas para nao espalhar callbacks. */
private class PlayerSlot(val currentId: () -> Long?, val setCurrentId: (Long?) -> Unit)

/** Acoes de cada linha: tocar (abre o player), melhorar o som, renomear, favoritar, compartilhar, salvar em... e excluir com DESFAZER. */
@Composable
private fun rememberRowActions(
    viewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    player: PlayerController,
    snackbar: SnackbarHostState,
    slot: PlayerSlot,
    onRename: (Recording) -> Unit,
    onEnhance: (Long) -> Unit,
    onCriar: (Long) -> Unit,
    onTranscrever: (Long) -> Unit,
): RowActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf<Recording?>(null) }
    val savedOk = stringResource(R.string.library_saved_ok)
    val savedFail = stringResource(R.string.library_saved_fail)
    val playFail = stringResource(R.string.library_play_fail)
    val deletedText = stringResource(R.string.library_deleted)
    val undoText = stringResource(R.string.library_undo)
    val saveLauncher = rememberLauncherForActivityResult(CreateAudioDocument()) { uri ->
        val target = saving
        saving = null
        if (uri != null && target != null) scope.launch { snackbar.showSnackbar(if (viewModel.saveTo(target, uri)) savedOk else savedFail) }
    }
    return RowActions(
        onPlay = { r ->
            scope.launch {
                if (openAndPlay(viewModel, player, r)) {
                    slot.setCurrentId(r.id)
                    playerViewModel.open(r)
                } else {
                    snackbar.showSnackbar(playFail)
                }
            }
        },
        onEnhance = { r -> onEnhance(r.id) },
        onCriar = { r -> onCriar(r.id) },
        onTranscrever = { r -> onTranscrever(r.id) },
        onRename = onRename,
        onFavorite = viewModel::toggleFavorite,
        onShare = { r -> scope.launch { runCatching { viewModel.shareIntent(r) }.onSuccess(context::startActivity) } },
        onSaveAs = { r -> saving = r; saveLauncher.launch("${r.title}.${r.format.extension}" to r.format.mimeType) },
        onDelete = { r ->
            if (slot.currentId() == r.id) {
                player.pause()
                slot.setCurrentId(null)
                playerViewModel.close()
            }
            viewModel.delete(r)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(r)
            }
        },
    )
}

/** Filtro por etiquetas: toque numa etiqueta para ver so as gravacoes que a tem (varias = todas). */
@Composable
private fun TagFilterRow(available: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    if (available.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("filtro_etiquetas"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(available, key = { it }) { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(tag) },
                modifier = Modifier.testTag("filtro_etiqueta_$tag"),
            )
        }
    }
}

/** Decifra (se preciso) fora da thread principal e toca NA principal (o ExoPlayer exige); false se nao abriu. */
private suspend fun openAndPlay(viewModel: LibraryViewModel, player: PlayerController, recording: Recording): Boolean {
    val path = runCatching { viewModel.playablePath(recording) }.getOrNull() ?: return false
    withContext(Dispatchers.Main) {
        player.load(path, title = recording.title.takeUnless { recording.isPrivate })
        player.play()
        viewModel.played()
    }
    return true
}

private data class RowActions(
    val onPlay: (Recording) -> Unit,
    val onEnhance: (Recording) -> Unit,
    val onCriar: (Recording) -> Unit,
    val onTranscrever: (Recording) -> Unit,
    val onRename: (Recording) -> Unit,
    val onFavorite: (Recording) -> Unit,
    val onShare: (Recording) -> Unit,
    val onSaveAs: (Recording) -> Unit,
    val onDelete: (Recording) -> Unit,
)

/** Busca + botão “Importar” (F6): a porta SAF do picker de áudio do celular. */
@Composable
private fun SearchRow(query: String, onChange: (String) -> Unit, onImportar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SearchField(query, onChange, Modifier.weight(1f))
        IconButton(
            onClick = onImportar,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp) // alvo de toque ≥ 48 dp (acessibilidade)
                .testTag("botao_importar"),
        ) {
            Icon(Icons.Filled.LibraryAdd, contentDescription = stringResource(R.string.library_import_desc))
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.library_search_clear)) }
            }
        },
        modifier = modifier.padding(16.dp).testTag("campo_busca"),
    )
}

/** Filtro da lista (busca + etiquetas), agrupado para nao espalhar parametros. */
private class ListUi(val query: String, val selectedTags: Set<String>, val onClearTags: () -> Unit)

@Composable
private fun RecordingList(
    items: List<Recording>,
    filtro: ListUi,
    playingId: Long?,
    actions: RowActions,
    selecao: List<Recording>,
    etiquetas: Map<Long, String> = emptyMap(),
    onToggleSelecao: (Recording) -> Unit,
) {
    if (items.isEmpty()) {
        val text = when {
            filtro.selectedTags.isNotEmpty() -> stringResource(R.string.library_no_results_tags)
            filtro.query.isBlank() -> stringResource(R.string.library_empty)
            else -> stringResource(R.string.library_no_results, filtro.query)
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("lista_vazia"))
            if (filtro.selectedTags.isNotEmpty()) {
                TextButton(onClick = filtro.onClearTags, modifier = Modifier.testTag("limpar_filtro_etiquetas")) {
                    Text(stringResource(R.string.library_clear_tag_filter))
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("lista_gravacoes")) {
        items(items, key = { it.id }) { r ->
            RecordingRow(r, playing = r.id == playingId, actions, selecao, etiquetas[r.id], onToggleSelecao)
            HorizontalDivider()
        }
    }
}

/** Filtros da análise musical (F2/Gate 3): faixas de BPM e tons detectados. */
@Composable
private fun MixFilterRow(
    filtroBpm: BpmFaixa?,
    filtroTom: String?,
    toms: List<String>,
    onBpm: (BpmFaixa?) -> Unit,
    onTom: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("filtro_mix"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = filtroBpm == BpmFaixa.LENTA,
                onClick = { onBpm(if (filtroBpm == BpmFaixa.LENTA) null else BpmFaixa.LENTA) },
                label = { Text(stringResource(R.string.library_bpm_lenta)) },
                modifier = Modifier.testTag("filtro_bpm_lenta"),
            )
        }
        item {
            FilterChip(
                selected = filtroBpm == BpmFaixa.MEDIA,
                onClick = { onBpm(if (filtroBpm == BpmFaixa.MEDIA) null else BpmFaixa.MEDIA) },
                label = { Text(stringResource(R.string.library_bpm_media)) },
                modifier = Modifier.testTag("filtro_bpm_media"),
            )
        }
        item {
            FilterChip(
                selected = filtroBpm == BpmFaixa.RAPIDA,
                onClick = { onBpm(if (filtroBpm == BpmFaixa.RAPIDA) null else BpmFaixa.RAPIDA) },
                label = { Text(stringResource(R.string.library_bpm_rapida)) },
                modifier = Modifier.testTag("filtro_bpm_rapida"),
            )
        }
        items(toms, key = { it }) { tom ->
            FilterChip(
                selected = filtroTom == tom,
                onClick = { onTom(if (filtroTom == tom) null else tom) },
                label = { Text(tom) },
                modifier = Modifier.testTag("filtro_tom_${tom.replace(' ', '_')}"),
            )
        }
    }
}

@Composable
private fun RecordingRow(
    r: Recording,
    playing: Boolean,
    actions: RowActions,
    selecao: List<Recording>,
    etiqueta: String? = null,
    onToggleSelecao: (Recording) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val playDesc = stringResource(R.string.library_play_item, r.title)
    val marcada = selecao.any { it.id == r.id }
    val selecionando = selecao.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (marcada) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = { if (selecionando) onToggleSelecao(r) else actions.onPlay(r) },
                onLongClick = { onToggleSelecao(r) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("item_gravacao"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadIcon(selecionando, marcada, playing, playDesc)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("titulo_item"))
                if (r.isFavorite) Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                if (r.isPrivate) {
                    Icon(
                        Icons.Filled.Lock, contentDescription = stringResource(R.string.library_private_badge),
                        modifier = Modifier.padding(start = 4.dp).testTag("selo_cofre"),
                    )
                }
            }
            Text(
                stringResource(R.string.library_item_meta, formatDuration(r.durationMillis), r.format.name, formatSize(r.sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("duracao_item"),
            )
            EtiquetasDaLinha(etiqueta, r.isImported, r.tags)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("mais_acoes")) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.library_more))
            }
            RowMenu(menu, { menu = false }, r, actions)
        }
    }
}

@Composable
private fun RowMenu(expanded: Boolean, dismiss: () -> Unit, r: Recording, a: RowActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
        MenuItem(R.string.library_action_transcrever, "menu_transcrever") { dismiss(); a.onTranscrever(r) }
        MenuItem(R.string.library_action_criar, "menu_criar_versao") { dismiss(); a.onCriar(r) }
        MenuItem(R.string.library_action_enhance, "menu_melhorar_som") { dismiss(); a.onEnhance(r) }
        MenuItem(R.string.library_action_share, "menu_compartilhar") { dismiss(); a.onShare(r) }
        MenuItem(R.string.library_action_save_as, "menu_salvar_em") { dismiss(); a.onSaveAs(r) }
        MenuItem(R.string.library_action_rename, "menu_renomear") { dismiss(); a.onRename(r) }
        val favLabel = if (r.isFavorite) R.string.library_action_unfavorite else R.string.library_action_favorite
        MenuItem(favLabel, "menu_favoritar") { dismiss(); a.onFavorite(r) }
        MenuItem(R.string.library_action_delete, "menu_excluir") { dismiss(); a.onDelete(r) }
    }
}

@Composable
private fun MenuItem(label: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

@Composable
private fun RenameDialog(recording: Recording, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(recording.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_title)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.testTag("campo_renomear"))
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("confirmar_renomear"),
            ) { Text(stringResource(R.string.library_rename_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancelar_renomear")) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

/** Mimes aceitos no picker de importação (F6): mp3, wav, aac, flac, m4a. */
private val MIMES_DE_AUDIO = arrayOf(
    "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
    "audio/aac", "audio/flac", "audio/mp4", "audio/x-m4a", "audio/m4a",
)

/**
 * F6/Importação: picker SAF (ACTION_OPEN_DOCUMENT + EXTRA_MIME_TYPES de áudio)
 * e o feedback da importação em snackbars ("Já existe" em duplicata). A porta
 * SAF é a ÚNICA leitura direta de armazenamento do app (Scoped Storage).
 */
@Composable
private fun ImportPicker(
    viewModel: LibraryViewModel,
    snackbar: SnackbarHostState,
): androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?> {
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val snackImportOk = stringResource(R.string.library_import_saf_ok)
    val snackDuplicata = stringResource(R.string.library_import_duplicata)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importar(uri)
    }
    LaunchedEffect(importState, snackImportOk, snackDuplicata) {
        when (val estado = importState) {
            is LibraryViewModel.ImportState.Importado -> {
                snackbar.showSnackbar(snackImportOk)
                viewModel.consumirImportState()
            }
            is LibraryViewModel.ImportState.Duplicata -> {
                snackbar.showSnackbar(snackDuplicata)
                viewModel.consumirImportState()
            }
            is LibraryViewModel.ImportState.Falhou -> {
                snackbar.showSnackbar(estado.mensagem)
                viewModel.consumirImportState()
            }
            else -> Unit
        }
    }
    return launcher
}

/** Etiquetas do item: análise (F2), origem IMPORTED (F6) e tags do usuário. */
@Composable
private fun EtiquetasDaLinha(etiqueta: String?, importada: Boolean, tags: List<String>) {
    if (!etiqueta.isNullOrBlank()) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("etiqueta_mix_item"),
        )
    }
    // Etiqueta de origem (F6/Importação): literal "origin=IMPORTED" do roteiro.
    if (importada) {
        Text(
            "origin=IMPORTED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.testTag("etiqueta_importada"),
        )
    }
    if (tags.isNotEmpty()) {
        Text(
            tags.joinToString("  ") { "#$it" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("etiquetas_item"),
        )
    }
}

/** Diálogos da lista: renomear e confirmação da junção (ordem dos áudios). */
@Composable
private fun DialogosDaLista(
    renaming: Recording?,
    viewModel: LibraryViewModel,
    snackbar: SnackbarHostState,
    onRenameConcluido: () -> Unit,
    confirmandoJuncao: Boolean,
    selecao: List<Recording>,
    onJuncaoConcluida: () -> Unit,
) {
    renaming?.let {
        RenameDialog(
            it,
            onDismiss = onRenameConcluido,
            onConfirm = { t -> viewModel.rename(it, t); onRenameConcluido() },
        )
    }
    JuncaoPendente(confirmandoJuncao, selecao, viewModel, snackbar, onJuncaoConcluida)
}

/** Painel do player quando há gravação aberta. Fechar NÃO pausa (PlaybackService). */
@Composable
private fun PlayerDockSeAberto(
    items: List<Recording>,
    currentId: Long?,
    player: PlayerController,
    playerViewModel: PlayerViewModel,
    onEnhance: (Long) -> Unit,
    onFechar: () -> Unit,
) {
    val current = items.firstOrNull { it.id == currentId } ?: return
    PlayerDock(current, player, playerViewModel, onEnhance = onEnhance, onClose = onFechar)
}
