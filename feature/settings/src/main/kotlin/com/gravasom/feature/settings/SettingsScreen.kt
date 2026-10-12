package com.gravasom.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.preset.FormatPreset
import com.gravasom.core.ui.Paleta
import com.gravasom.core.ui.esquemaDeCores

private val Bitrates = listOf(64, 128, 192)
private val MIN_TOUCH_TARGET = 48.dp

@Composable
fun SettingsScreen(onOpenNumbers: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var showLicenses by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section(R.string.settings_section_recording)
        PresetGroup(s.preset, viewModel::setPreset)
        FormatGroup(s.format, viewModel::setFormat)
        Text(stringResource(R.string.settings_quality), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Bitrates.forEach { kbps ->
                FilterChip(
                    selected = s.bitrate == kbps,
                    onClick = { viewModel.setBitrate(kbps) },
                    label = { Text("$kbps") },
                    modifier = Modifier.testTag("qualidade_$kbps"),
                )
            }
        }
        Text(stringResource(R.string.settings_quality_note), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Section(R.string.settings_section_aparencia)
        PaletteSelectorRow(s.paletteId, viewModel::setPalette)
        HorizontalDivider()
        Section(R.string.settings_section_privacy)
        SwitchRow(R.string.settings_private, R.string.settings_private_desc, s.privateMode, "chave_privado", viewModel::setPrivate)
        SwitchRow(
            R.string.settings_numbers_optin, R.string.settings_numbers_optin_desc,
            s.numbersOptIn, "chave_numeros", viewModel::setNumbersOptIn,
        )
        OutlinedButton(onClick = onOpenNumbers, modifier = Modifier.fillMaxWidth().testTag("abrir_numeros")) {
            Text(stringResource(R.string.settings_numbers_open))
        }
        HorizontalDivider()
        BackupSection(viewModel)
        HorizontalDivider()
        Section(R.string.settings_section_about)
        Text(stringResource(R.string.settings_no_internet), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showLicenses = true }, modifier = Modifier.testTag("abrir_licencas")) {
            Text(stringResource(R.string.settings_licenses))
        }
    }
    if (showLicenses) LicensesDialog { showLicenses = false }
}

@Composable
private fun Section(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/**
 * F8 — seletor de paleta em Ajustes › Aparência. As paletas disponíveis são as
 * do design system ([com.gravasom.core.ui.Paleta.disponiveis]): Mixlirous
 * (roxo, padrão), Gravasom (teal) e Cores do sistema (Material You, Android 12+).
 * A escolha vai para a DataStore ([PreferencesRepository.setPalette]) e o tema
 * reage na hora ([GravasomApp] observa a preferência).
 */
@Composable
private fun PaletteSelectorRow(selecionada: String, onSelect: (String) -> Unit) {
    val paletas = remember { Paleta.disponiveis() }
    Column {
        Text(stringResource(R.string.settings_aparencia_desc), style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("seletor_paleta"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            paletas.forEach { paleta ->
                FilterChip(
                    selected = selecionada == paleta.id,
                    onClick = { onSelect(paleta.id) },
                    label = { Text(stringResource(nomeDaPaleta(paleta))) },
                    leadingIcon = { BolinhaDaPaleta(paleta) },
                    modifier = Modifier.testTag("paleta_${paleta.id}"),
                )
            }
        }
    }
}

/** Amostra da cor primária da paleta (prévia da identidade visual). */
@Composable
private fun BolinhaDaPaleta(paleta: Paleta) {
    val escuro = isSystemInDarkTheme()
    val cor: Color = remember(paleta, escuro) { esquemaDeCores(paleta, escuro).primary }
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(cor, shape = CircleShape),
    )
}

private fun nomeDaPaleta(paleta: Paleta): Int = when (paleta) {
    Paleta.MIXLIROUS -> R.string.settings_paleta_mixlirous
    Paleta.GRAVASOM -> R.string.settings_paleta_gravasom
    Paleta.SISTEMA -> R.string.settings_paleta_sistema
}

@Composable
private fun FormatGroup(selected: RecordingFormat, onSelect: (RecordingFormat) -> Unit) {
    Text(stringResource(R.string.settings_format), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        RecordingFormat.entries.forEach { f ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET) // area de toque minima de 48 dp (acessibilidade)
                    .selectable(selected == f, onClick = { onSelect(f) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp).testTag("formato_${f.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == f, onClick = null)
                Text(stringResource(formatLabel(f)), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

private fun formatLabel(f: RecordingFormat) = when (f) {
    RecordingFormat.M4A -> R.string.settings_format_M4A
    RecordingFormat.WAV -> R.string.settings_format_WAV
    RecordingFormat.MP3 -> R.string.settings_format_MP3
}

@Composable
private fun SwitchRow(title: Int, desc: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

/** Aviso LGPL do LAME + oferta de fonte (ADR-007), lido de assets/licencas. */
@Composable
private fun LicensesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember { runCatching { context.assets.open("licencas/LAME-LGPL.txt").bufferedReader().readText() }.getOrDefault("") }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.settings_licenses_close)) } },
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState()).testTag("texto_licencas")) },
    )
}

/**
 * Presets de gravacao: um toque ajusta formato, taxa, bitrate e canais. Mostra o resumo do preset escolhido e o que ele liga por padrao
 * ao melhorar o som; mexer no formato ou na qualidade a mao tira do preset ("personalizado").
 */
@Composable
internal fun PresetGroup(selected: FormatPreset?, onSelect: (FormatPreset) -> Unit) {
    Text(stringResource(R.string.settings_preset_title), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormatPreset.entries.forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(stringResource(presetLabel(p))) },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag("preset_${p.name}"),
            )
        }
    }
    val summary = selected?.let {
        val channels = stringResource(if (it.spec.channels == 1) R.string.settings_preset_mono else R.string.settings_preset_stereo)
        stringResource(R.string.settings_preset_summary, it.spec.sampleRateHz / HZ_PER_KHZ, it.spec.bitrateKbps, channels)
    }
    if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("preset_resumo"))
    Text(
        stringResource(
            when {
                selected == null -> R.string.settings_preset_custom
                selected.effects.denoise || selected.effects.normalize -> R.string.settings_preset_effects_on
                else -> R.string.settings_preset_effects_off
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("preset_efeitos"),
    )
}

private fun presetLabel(p: FormatPreset) = when (p) {
    FormatPreset.VOICE -> R.string.settings_preset_voice
    FormatPreset.CLASS -> R.string.settings_preset_class
    FormatPreset.INTERVIEW -> R.string.settings_preset_interview
    FormatPreset.MUSIC -> R.string.settings_preset_music
    FormatPreset.NATURE -> R.string.settings_preset_nature
    FormatPreset.WATER -> R.string.settings_preset_water
}

private const val HZ_PER_KHZ = 1_000
