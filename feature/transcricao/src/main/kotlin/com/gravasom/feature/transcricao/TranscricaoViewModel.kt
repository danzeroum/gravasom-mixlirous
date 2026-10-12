package com.gravasom.feature.transcricao

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gravasom.core.data.modelmanager.CatalogoModelos
import com.gravasom.core.data.modelmanager.ModelManager
import com.gravasom.core.data.modelmanager.ModelMetadata
import com.gravasom.core.data.modelmanager.ModeloInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado da tela de transcrição (F7) — máquina de estados fechada, um caminho por transição. */
sealed class TranscricaoEstado {
    /** Aberta sem modelo disponível: consentimento com tamanho/origem/SHA-256 (ADR-017). */
    data class Consentimento(val modeloSelecionado: ModeloInfo) : TranscricaoEstado()

    /** Download em curso (única conexão de rede do app). */
    data class Baixando(val modelo: ModeloInfo, val progresso: Int) : TranscricaoEstado()

    /** Modelo presente e verificado: pronto para transcrever. */
    data class Pronto(val modelo: ModelMetadata) : TranscricaoEstado()

    /** Decodificando/reamostrando o áudio (cancelável). */
    data class Preparando(val modelo: ModelMetadata) : TranscricaoEstado()

    /** Inferência whisper em curso (não interrompível; tempo decorrido exibido). */
    data class Transcrevendo(val modelo: ModelMetadata, val inicioElapsedMs: Long) : TranscricaoEstado()

    /** Sucesso: resumo editável + botão salvar (salvoId != null depois de salvo). */
    data class Sucesso(val resultado: TranscricaoResultado, val salvoId: Long? = null) : TranscricaoEstado()

    /** Falha com mensagem (downloading, hash, decode, inferência). */
    data class Erro(val mensagem: String) : TranscricaoEstado()
}

@HiltViewModel
class TranscricaoViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val useCase: TranscricaoUseCase,
) : ViewModel() {

    val catalogo: List<ModeloInfo> = CatalogoModelos.modelos

    private val _estado = MutableStateFlow<TranscricaoEstado>(TranscricaoEstado.Consentimento(catalogo.first { it.recomendado }))
    val estado: StateFlow<TranscricaoEstado> = _estado.asStateFlow()

    private var trabalho: Job? = null
    private var resultadoPendente: TranscricaoResultado? = null

    /** Modelo presente? Vai direto para Pronto; senão, consentimento (sempre que a tela abre). */
    fun avaliarDisponibilidade() {
        if (trabalho?.isActive == true) return
        val atual = modelManager.modeloAtual()
        _estado.value = if (atual != null) TranscricaoEstado.Pronto(atual) else consentimentoInicial()
    }

    fun selecionarModelo(modelo: ModeloInfo) {
        if (trabalho?.isActive == true) return
        _estado.value = TranscricaoEstado.Consentimento(modelo)
    }

    /** Aceite do consentimento (ADR-017) + download imediato com progresso. */
    fun consentirEBaixar() {
        val estado = _estado.value as? TranscricaoEstado.Consentimento ?: return
        val modelo = estado.modeloSelecionado
        modelManager.registrarConsentimento()
        trabalho = viewModelScope.launch {
            _estado.value = TranscricaoEstado.Baixando(modelo, 0)
            try {
                val resultado = modelManager.baixarModelo(modelo) { progresso ->
                    val atual = _estado.value
                    if (atual is TranscricaoEstado.Baixando) {
                        _estado.value = atual.copy(progresso = (progresso * 100).toInt().coerceIn(0, 100))
                    }
                }
                resultado.fold(
                    onSuccess = { meta -> _estado.value = TranscricaoEstado.Pronto(meta) },
                    onFailure = { _estado.value = TranscricaoEstado.Erro(mensagemDe(it, "Falha no download do modelo")) },
                )
            } catch (e: CancellationException) {
                throw e // cancelar volta para Pronto/Consentimento via [cancelar] — não é erro
            } catch (e: Throwable) {
                // Erros fora de Exception (OOM, linkage) não podem matar o processo (crash de campo 2026-10-12).
                _estado.value = TranscricaoEstado.Erro(mensagemDe(e, "Falha no download do modelo"))
            }
        }
    }

    /** Importação SAF (plano B offline): hash calculado e exibido para conferência manual. */
    fun importarModelo(uri: Uri) {
        trabalho = viewModelScope.launch {
            try {
                modelManager.importarModelo(uri).fold(
                    onSuccess = { meta -> _estado.value = TranscricaoEstado.Pronto(meta) },
                    onFailure = { _estado.value = TranscricaoEstado.Erro(mensagemDe(it, "Falha na importação do modelo")) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _estado.value = TranscricaoEstado.Erro(mensagemDe(e, "Falha na importação do modelo"))
            }
        }
    }

    /** Cancela o trabalho corrente (download ou preparo do áudio). Durante a inferência, o
     *  resultado é descartado quando ela termina (whisper.cpp síncrono — limitação documentada). */
    fun cancelar() {
        trabalho?.cancel()
        trabalho = null
        if (_estado.value is TranscricaoEstado.Baixando || _estado.value is TranscricaoEstado.Preparando) {
            avaliarDisponibilidade()
        }
    }

    /** Salva a anotação com o TEXTO EDITADO da tela (Gate 7). */
    fun salvar(recordingId: Long, titulo: String, textoEditado: String) {
        val resultado = resultadoPendente ?: return
        trabalho = viewModelScope.launch {
            try {
                val id = useCase.salvarAnotacao(recordingId, resultado, titulo = titulo, texto = textoEditado)
                val atual = _estado.value as? TranscricaoEstado.Sucesso
                if (atual != null) _estado.value = atual.copy(salvoId = id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _estado.value = TranscricaoEstado.Erro(mensagemDe(e, "Falha ao salvar anotação"))
            }
        }
    }

    /** Protocolo ADR-016: limpar o modelo para forçar re-download (menu da tela). */
    fun removerModelo() {
        trabalho?.cancel()
        trabalho = null
        modelManager.removerModelo()
        _estado.value = consentimentoInicial()
    }

    fun iniciarTranscricao(recordingId: Long, idioma: String = "pt", comTimestamps: Boolean = true) {
        val pronto = _estado.value as? TranscricaoEstado.Pronto ?: return
        val metadata = pronto.modelo
        resultadoPendente = null
        trabalho = viewModelScope.launch {
            _estado.value = TranscricaoEstado.Preparando(metadata)
            try {
                val resultado = useCase.transcrever(
                    recordingId = recordingId,
                    modeloPath = metadata.path,
                    modeloNome = metadata.nome,
                    idioma = idioma,
                    comTimestamps = comTimestamps,
                    cancelado = { trabalho?.isCancelled == true },
                    onFase = { fase ->
                        when (fase) {
                            FaseTranscricao.PREPARANDO -> _estado.value = TranscricaoEstado.Preparando(metadata)
                            FaseTranscricao.TRANSCREVENDO ->
                                _estado.value = TranscricaoEstado.Transcrevendo(metadata, SystemClock.elapsedRealtime())
                        }
                    },
                )
                if (resultado == null) {
                    _estado.value = TranscricaoEstado.Pronto(metadata) // cancelado durante a inferência
                    return@launch
                }
                resultadoPendente = resultado
                _estado.value = TranscricaoEstado.Sucesso(resultado)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _estado.value = TranscricaoEstado.Pronto(metadata)
                throw e
            } catch (e: Throwable) {
                // Throwable de propósito: OutOfMemoryError/UnsatisfiedLinkError são Error e não eram
                // pegos por catch(Exception) — o app fechava sem explicação (crash de campo 2026-10-12).
                _estado.value = TranscricaoEstado.Erro(mensagemDe(e, "Erro na transcrição"))
            }
        }
    }

    /** Re-transcrever (troca de timestamps/idioma) a partir do estado de erro ou sucesso. */
    fun tentarNovamente(recordingId: Long, comTimestamps: Boolean) {
        val metadata = when (val estado = _estado.value) {
            is TranscricaoEstado.Sucesso -> modelManager.modeloAtual()
            is TranscricaoEstado.Erro -> modelManager.modeloAtual()
            else -> null
        }
        if (metadata != null) {
            _estado.value = TranscricaoEstado.Pronto(metadata)
            iniciarTranscricao(recordingId, comTimestamps = comTimestamps)
        } else {
            avaliarDisponibilidade()
        }
    }

    override fun onCleared() {
        trabalho?.cancel()
        super.onCleared()
    }

    private fun consentimentoInicial(): TranscricaoEstado.Consentimento =
        TranscricaoEstado.Consentimento(catalogo.first { it.recomendado })

    /** Mensagem do Erro para a tela: OOM tem voz própria; o resto usa a mensagem do erro ou o padrão. */
    private fun mensagemDe(e: Throwable, padrao: String): String = when {
        e is OutOfMemoryError -> "Sem memória para concluir — feche outros apps ou escolha o modelo Tiny (menor)"
        else -> e.localizedMessage ?: padrao
    }
}
