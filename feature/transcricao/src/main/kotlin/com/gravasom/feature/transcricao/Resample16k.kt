package com.gravasom.feature.transcricao

/**
 * Reamostrador linear para 16 kHz mono (F7). Puro Kotlin — testável no host sem Android.
 *
 * Recebe PCM intercalado de 16 bits em pedaços (do [com.gravasom.core.service.analysis.PcmDecoder])
 * e entrega amostras mono 16 kHz via sink. Mantém entre chamadas o quadro anterior E as amostras
 * soltas de um quadro quebrado no fim do pedaço (fila de sobras) — pedaços ímpares não perdem nada.
 *
 * Convenção: mono = média dos canais. step = taxaOrigem/16000 quadros de origem por amostra alvo.
 */
class Resample16k(private val taxaOrigem: Int, private val canais: Int) {

    private val step: Double = taxaOrigem / 16000.0
    private var quadroAnterior: Float = 0f
    private var temAnterior = false
    // Posição ABSOLUTA (em quadros de origem) da próxima amostra alvo, relativa ao início do fluxo.
    private var posicaoProximoAlvo: Double = 0.0
    private var quadrosConsumidos: Long = 0

    // Fila FIFO de amostras soltas do último quadro quebrado (sempre < canais).
    private val fila = IntArray(MAXIMA_CANais)
    private var filaCount = 0
    private val quadro = IntArray(MAXIMA_CANais)

    /** Entrega as amostras alvo geradas por este pedaço em [alvo]; devolve quantas escreveu. */
    fun empurrar(pcm: ShortArray, n: Int, alvo: (Short) -> Unit): Int {
        if (n <= 0) return 0
        var pos = 0
        var emitidas = 0
        while (true) {
            // Monta o próximo quadro: primeiro as sobras, depois o pedaço atual.
            var q = 0
            while (q < canais && filaCount > 0) quadro[q++] = tirarDaFila()
            while (q < canais && pos < n) quadro[q++] = pcm[pos++].toInt()
            if (q < canais) {
                // Quadro incompleto: devolve tudo à fila para a próxima chamada (ordem preservada).
                for (i in 0 until q) fila[filaCount++] = quadro[i]
                break
            }
            var soma = 0
            for (i in 0 until canais) soma += quadro[i]
            val monoAtual = soma.toFloat() / canais
            if (!temAnterior) {
                quadroAnterior = monoAtual
                temAnterior = true
                quadrosConsumidos++
                continue
            }
            // Alvos cuja posição cai no intervalo [anterior, atual).
            while (posicaoProximoAlvo < quadrosConsumidos) {
                val fracao = (posicaoProximoAlvo - (quadrosConsumidos - 1)).coerceIn(0.0, 1.0)
                val interpolada = quadroAnterior + (monoAtual - quadroAnterior) * fracao.toFloat()
                // DOMÍNIO SHORT de ponta a ponta: a entrada é PCM 16 bits cru (soma de shorts), então a
                // saturação é nos LIMITES DO SHORT. O coerceIn(-1f,1f)*32767 antigo achava que a entrada
                // era float normalizado — qualquer amostra não silenciosa virava ±32767 (onda quadrada):
                // frequência contada a zero-crossing continuava certa e o teste não via, mas o whisper
                // receberia ruído puro (achado no teste de regressão do PcmParaWhisper, 2026-10-12).
                val amostra = interpolada.coerceIn(-32768f, 32767f).toInt().coerceIn(-32768, 32767)
                alvo(amostra.toShort())
                emitidas++
                posicaoProximoAlvo += step
            }
            quadroAnterior = monoAtual
            quadrosConsumidos++
        }
        return emitidas
    }

    private fun tirarDaFila(): Int {
        val primeiro = fila[0]
        for (i in 1 until filaCount) fila[i - 1] = fila[i]
        filaCount--
        return primeiro
    }

    companion object {
        private const val MAXIMA_CANais = 8
    }
}
