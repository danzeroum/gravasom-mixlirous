package com.gravasom

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guarda da causa raiz do erro de campo de 2026-10-13: o app enfileirava o download do modelo
 * no DownloadManager SEM declarar android.permission.INTERNET — o DownloadProvider negava com
 * "Permission Denial … requires android.permission.INTERNET" e a transcrição nunca saía do erro.
 * O download é a ÚNICA conexão de rede do app (ADR-017); a permissão é obrigatória para sempre.
 *
 * Robolectric lê o MANIFEST MESCLADO do app — exatamente o que a plataforma vê na instalação.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ManifestPermissionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `manifesto mesclado declara INTERNET — DownloadManager não pode ser negado de novo`() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declaradas = info.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "O Manifest precisa de android.permission.INTERNET (DownloadProvider nega enqueue " +
                "sem ela — erro de campo 2026-10-13). Declaradas: $declaradas",
            Manifest.permission.INTERNET in declaradas,
        )
    }

    @Test
    fun `manifesto continua pedindo RECORD_AUDIO e POST_NOTIFICATIONS — nada foi trocado pelo caminho`() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declaradas = info.requestedPermissions?.toList() ?: emptyList()
        assertTrue(Manifest.permission.RECORD_AUDIO in declaradas)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in declaradas)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE in declaradas)
    }
}
