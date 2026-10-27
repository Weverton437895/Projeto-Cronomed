package br.com.cronomed.alarme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.cronomed.data.AgendaLocal
import br.com.cronomed.model.Dose

/** Agenda no Android os alarmes que disparam os lembretes de dose. */
class AlarmeScheduler(private val contexto: Context) {

    private val gerente = contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun intencao(doseId: String): PendingIntent {
        val intent = Intent(contexto, AlarmeReceiver::class.java)
        intent.action = ACAO_DOSE
        intent.putExtra(Notificacoes.EXTRA_DOSE, doseId)
        return PendingIntent.getBroadcast(
            contexto,
            doseId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun agendar(doseId: String, emMillis: Long) {
        val pendente = intencao(doseId)
        val exatoPermitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gerente.canScheduleExactAlarms()
        try {
            if (exatoPermitido) {
                gerente.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, emMillis, pendente)
            } else {
                gerente.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, emMillis, pendente)
            }
        } catch (e: SecurityException) {
            gerente.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, emMillis, pendente)
        }
    }

    fun cancelar(doseId: String) {
        gerente.cancel(intencao(doseId))
    }

    /**
     * Recria os alarmes de todas as doses guardadas na agenda local.
     * Doses atrasadas há menos de 60 minutos tocam logo em seguida.
     */
    fun reagendarTudo() {
        val agora = System.currentTimeMillis()
        AgendaLocal(contexto).listar().forEach { item ->
            val quando = item.dose.agendadaEm
            if (quando >= agora) {
                agendar(item.dose.id, quando)
            } else if (agora - quando < TOLERANCIA_MS) {
                agendar(item.dose.id, agora + 5000)
            }
        }
    }

    /** Guarda as doses pendentes no aparelho e agenda os alarmes futuros (até 7 dias). */
    fun sincronizar(pacienteId: String, doses: List<Dose>) {
        val agenda = AgendaLocal(contexto)
        val antes = agenda.listar().filter { it.pacienteId == pacienteId }.map { it.dose.id }.toSet()
        agenda.salvar(pacienteId, doses)
        val depois = agenda.listar().filter { it.pacienteId == pacienteId }.map { it.dose.id }.toSet()
        (antes - depois).forEach {
            cancelar(it)
            Notificacoes.cancelar(contexto, it)
        }
        val limite = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        agenda.listar().filter { it.pacienteId == pacienteId }.forEach { item ->
            if (item.dose.agendadaEm in System.currentTimeMillis()..limite) {
                agendar(item.dose.id, item.dose.agendadaEm)
            }
        }
    }

    /** Chamado quando a dose é confirmada: some o alarme, a notificação e o item da agenda. */
    fun concluirDose(doseId: String) {
        cancelar(doseId)
        Notificacoes.cancelar(contexto, doseId)
        AgendaLocal(contexto).remover(doseId)
    }

    fun cancelarTudo() {
        val agenda = AgendaLocal(contexto)
        agenda.listar().forEach {
            cancelar(it.dose.id)
            Notificacoes.cancelar(contexto, it.dose.id)
        }
        agenda.limpar()
    }

    companion object {
        const val ACAO_DOSE = "br.com.cronomed.DOSE"
        const val MAX_LEMBRETES = 12
        private const val TOLERANCIA_MS = 60L * 60 * 1000
    }
}
