package br.com.cronomed.data

import android.content.Context
import android.util.Log
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.dao.DoseDao
import br.com.cronomed.dao.MedicamentoDao
import br.com.cronomed.util.AoErro

/**
 * Mantém a agenda em dia: cria as doses dos próximos 7 dias, marca as perdidas e
 * atualiza os alarmes do aparelho. Roda ao abrir o app e depois de editar medicamentos.
 */
object Sincronizador {

    private const val SETE_DIAS_MS = 7L * 24 * 60 * 60 * 1000
    private const val UMA_HORA_MS = 60L * 60 * 1000

    fun sincronizar(
        contexto: Context,
        pacienteId: String,
        podeEscrever: Boolean,
        aoConcluir: () -> Unit,
        aoErro: AoErro
    ) {
        Log.d("CronoMed", "Sincronizando agenda do paciente " + pacienteId)
        val app = contexto.applicationContext
        val doseDao = DoseDao()
        MedicamentoDao().listar(pacienteId, { medicamentos ->
            val atualizarAlarmes = {
                val agora = System.currentTimeMillis()
                doseDao.listarPeriodo(pacienteId, agora - UMA_HORA_MS, agora + SETE_DIAS_MS, { doses ->
                    AlarmeScheduler(app).sincronizar(pacienteId, doses)
                    aoConcluir()
                }, aoErro)
            }
            if (podeEscrever) {
                doseDao.garantirDoses(pacienteId, medicamentos, 7, {
                    doseDao.marcarPerdidas(pacienteId, atualizarAlarmes, aoErro)
                }, aoErro)
            } else {
                atualizarAlarmes()
            }
        }, aoErro)
    }
}
