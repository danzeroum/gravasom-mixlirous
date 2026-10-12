package com.gravasom.feature.transcricao

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatação do resumo estruturado (Gate 7: timestamps [mm:ss] e bruto). */
class ResumoFormatterTest {

    private val segmentos = listOf(
        Segmento(0, 4_000, " Primeiro trecho da aula. "),
        Segmento(12_300, 18_500, "Segundo trecho, com pausa."),
        Segmento(75_000, 80_000, "Terceiro trecho após 1 minuto."),
    )

    @Test
    fun resumoComTimestampsFormatoMmss() {
        val esperado = "[00:00] Primeiro trecho da aula.\n" +
            "[00:12] Segundo trecho, com pausa.\n" +
            "[01:15] Terceiro trecho após 1 minuto."
        assertEquals(esperado, ResumoFormatter.resumo(segmentos, comTimestamps = true))
    }

    @Test
    fun resumoSemTimestampsSoTexto() {
        val esperado = "Primeiro trecho da aula.\nSegundo trecho, com pausa.\nTerceiro trecho após 1 minuto."
        assertEquals(esperado, ResumoFormatter.resumo(segmentos, comTimestamps = false))
    }

    @Test
    fun brutoJuntaComEspacoETrim() {
        assertEquals(
            "Primeiro trecho da aula. Segundo trecho, com pausa. Terceiro trecho após 1 minuto.",
            ResumoFormatter.bruto(segmentos),
        )
    }

    @Test
    fun listaVaziaNaoExplode() {
        assertEquals("", ResumoFormatter.resumo(emptyList(), comTimestamps = true))
        assertEquals("", ResumoFormatter.bruto(emptyList()))
    }

    @Test
    fun parseDaSaidaJni() {
        val saida = "0\t400\t Primeiro trecho.\n1230\t1850\tSegundo trecho.\n\nlinha-ruim-sem-tabs\n"
        val segmentos = TranscricaoWhisper.parsear(saida)
        assertEquals(2, segmentos.size)
        assertEquals(Segmento(0, 400, "Primeiro trecho."), segmentos[0])
        assertEquals(Segmento(1_230, 1_850, "Segundo trecho."), segmentos[1])
    }
}
