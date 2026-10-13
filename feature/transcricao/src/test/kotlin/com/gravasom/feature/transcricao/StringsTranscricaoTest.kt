package com.gravasom.feature.transcricao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Crash de campo de 2026-10-12 (parte final): `transcricao_consentimento_texto` tinha % LITERAL
 * ("acontece 100% no aparelho") numa string que também formata %1$s — `String.format` lia "% n"
 * como especificador com flag de espaço e lançava `IllegalFormatFlagsException: Flags = ' '`
 * DURANTE a composição da tela de transcrição: o app fechava ao clicar em Transcrever, antes
 * mesmo do download do modelo. Toda string com argumento posicional precisa formatar sem exceção.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringsTranscricaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Strings com %n$ e os argumentos que a tela passa (mesma forma das chamadas em produção). */
    private val formataveis = mapOf<Int, List<Any>>(
        R.string.transcricao_consentimento_texto to listOf("Base (recomendado, ~60 MB)"),
        R.string.transcricao_consentimento_origem to listOf("https://exemplo/modelo.bin"),
        R.string.transcricao_consentimento_sha to listOf("abcdef0123456789…"),
        R.string.transcricao_consentimento_aceitar to listOf("60 MB"),
        R.string.transcricao_baixando to listOf(45),
        R.string.transcricao_pronto to listOf("ggml-base-q5_1.bin"),
        R.string.transcricao_pronto_tamanho to listOf("abcdef0123456789…"),
        R.string.transcricao_transcrevendo to listOf("12,3 s"),
        R.string.transcricao_sucesso_metrica to listOf("12,3 s", 123, "ggml-base-q5_1.bin"),
    )

    @Test
    fun `toda string com argumento posicional formata sem exceção`() {
        formataveis.forEach { (id, args) ->
            val texto = context.getString(id, *args.toTypedArray())
            assertTrue("string $id deve formatar", texto.isNotBlank())
            assertFalse("string $id não pode vazar o especificador %%", texto.contains("%1$"))
        }
    }

    @Test
    fun `o texto de consentimento mostra 100 por cento LITERAL e não o escape cru`() {
        val texto = context.getString(R.string.transcricao_consentimento_texto, "Base")
        assertTrue("o % literal deve aparecer uma vez: $texto", texto.contains("acontece 100% no aparelho"))
        assertFalse("%% escapado não pode sobrar no texto final: $texto", texto.contains("%%"))
    }
}
