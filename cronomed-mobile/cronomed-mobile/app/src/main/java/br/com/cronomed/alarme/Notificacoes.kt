package br.com.cronomed.alarme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.cronomed.R
import br.com.cronomed.data.AgendaLocal
import br.com.cronomed.ui.LembreteActivity
import br.com.cronomed.util.Formatadores

object Notificacoes {
    const val CANAL_DOSES = "cronomed_doses"
    const val EXTRA_DOSE = "doseId"
    const val EXTRA_PACIENTE = "pacienteId"

    /** A partir do Android 8 toda notificação precisa de um canal. */
    fun criarCanais(contexto: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_DOSES,
                contexto.getString(R.string.canal_doses_nome),
                NotificationManager.IMPORTANCE_HIGH
            )
            canal.description = contexto.getString(R.string.canal_doses_descricao)
            canal.enableVibration(true)
            val gerente = contexto.getSystemService(NotificationManager::class.java)
            gerente.createNotificationChannel(canal)
        }
    }

    fun idNotificacao(doseId: String): Int = doseId.hashCode()

    fun mostrarDose(contexto: Context, item: AgendaLocal.Item) {
        val dose = item.dose
        val abrir = Intent(contexto, LembreteActivity::class.java)
        abrir.putExtra(EXTRA_DOSE, dose.id)
        abrir.putExtra(EXTRA_PACIENTE, item.pacienteId)
        abrir.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendente = PendingIntent.getActivity(
            contexto,
            idNotificacao(dose.id),
            abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titulo = contexto.getString(R.string.notif_titulo, Formatadores.hora(dose.agendadaEm))
        val texto = dose.nome + " " + dose.dosagem + " • " + Formatadores.quantidade(dose.qtd, dose.unidade)
        val notificacao = NotificationCompat.Builder(contexto, CANAL_DOSES)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendente)
            .setFullScreenIntent(pendente, true)
            .build()
        try {
            NotificationManagerCompat.from(contexto).notify(idNotificacao(dose.id), notificacao)
        } catch (e: SecurityException) {
            // Sem permissão de notificação (Android 13+): o usuário ainda vê a dose na tela inicial
        }
    }

    fun cancelar(contexto: Context, doseId: String) {
        NotificationManagerCompat.from(contexto).cancel(idNotificacao(doseId))
    }
}
