package com.gravasom.feature.criar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Texture
import androidx.hilt.navigation.compose.hiltViewModel
import com.gravasom.feature.criar.melhores.MelhoresTab
import com.gravasom.feature.criar.mescla.MesclaTab
import com.gravasom.feature.criar.loop.LoopTab
import com.gravasom.feature.criar.textura.TexturaTab
import kotlinx.coroutines.launch

/**
 * Tela de criação (F3 + F6): CINCO abas — Receita | Loops | Texturas | Mescla |
 * Melhores — cada uma com o seu ViewModel e as suas validações de domínio
 * ([CriarSonsUseCases]); o render de todas passa pelo [CriacaoWorker].
 */
@Composable
fun CriarScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CriarSonsViewModel = hiltViewModel(),
) {
    val abas = listOf(
        CriarTab(nome = stringResource(R.string.criar_modo_remix), icone = Icons.Filled.Settings) {
            ReceitaTab(viewModel = viewModel, onDone = onDone)
        },
        CriarTab(nome = stringResource(R.string.criar_aba_loops), icone = Icons.Filled.Repeat) {
            LoopTab(recordingId = viewModel.recordingId, onVoltar = onDone)
        },
        CriarTab(nome = stringResource(R.string.criar_aba_texturas), icone = Icons.Filled.Texture) {
            TexturaTab(recordingId = viewModel.recordingId, onVoltar = onDone)
        },
        CriarTab(nome = stringResource(R.string.criar_aba_mescla), icone = Icons.Filled.CallSplit) {
            MesclaTab(recordingId = viewModel.recordingId, onVoltar = onDone)
        },
        CriarTab(nome = stringResource(R.string.criar_aba_melhores), icone = Icons.Filled.Star) {
            MelhoresTab(recordingId = viewModel.recordingId, onVoltar = onDone)
        },
    )

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.criar_titulo),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        val pagerState = rememberPagerState(pageCount = { abas.size })
        val escopo = rememberCoroutineScope()
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.testTag("abas_criar"),
        ) {
            abas.forEachIndexed { indice, aba ->
                Tab(
                    selected = pagerState.currentPage == indice,
                    onClick = { escopo.launch { pagerState.animateScrollToPage(indice) } },
                    text = { Text(aba.nome) },
                    modifier = Modifier.testTag("aba_criar_$indice"),
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pagina ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                abas[pagina].conteudo()
            }
        }
    }
}
