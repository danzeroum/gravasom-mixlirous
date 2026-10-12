package com.gravasom.core.service.tile

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Tile "Criar versão" (F8, Android 11+): atalho rápido para o fluxo de criação.
 * O fluxo (F6) precisa de uma gravação de origem — o tile abre a BIBLIOTECA,
 * onde 1 toque na gravação → ⋮ → Criar versão chega lá.
 */
class CriarTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(ACTION_ABRIR).setClassName(this, ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapseCompat(intent)
    }

    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val ACTION_ABRIR = "com.gravasom.action.QUICK_CRIAR"
        const val ACTIVITY = "com.gravasom.MainActivity"
    }
}
