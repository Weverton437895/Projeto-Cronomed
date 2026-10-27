package br.com.cronomed.alarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Depois de reiniciar o celular o Android apaga os alarmes; aqui eles são recriados. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AlarmeScheduler(contexto).reagendarTudo()
        }
    }
}
