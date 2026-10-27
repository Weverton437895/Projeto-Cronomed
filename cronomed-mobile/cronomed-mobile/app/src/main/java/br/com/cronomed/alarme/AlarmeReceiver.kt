package br.com.cronomed.alarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.cronomed.data.AgendaLocal
import br.com.cronomed.data.Preferencias

/**
 * Recebe o aviso do AlarmManager na hora da dose. Mostra a notificação e, enquanto a dose
 * não for confirmada, agenda o próximo lembrete (até 12, no intervalo escolhido).
 */
class AlarmeReceiver : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        val doseId = intent.getStringExtra(Notificacoes.EXTRA_DOSE) ?: return
        val agenda = AgendaLocal(contexto)
        val item = agenda.buscar(doseId)
        if (item == null) {
            Log.w("CronoMed", "Alarme de dose que já não está na agenda: " + doseId)
            return
        }
        if (!Preferencias(contexto).notificacoesDose) return

        val enviados = agenda.incrementarLembretes(doseId)
        Log.v("CronoMed", "Lembrete " + enviados + " da dose " + doseId)
        Notificacoes.mostrarDose(contexto, item)

        if (enviados < AlarmeScheduler.MAX_LEMBRETES) {
            val proximo = System.currentTimeMillis() + item.dose.repetirMin * 60_000L
            AlarmeScheduler(contexto).agendar(doseId, proximo)
        }
    }
}
