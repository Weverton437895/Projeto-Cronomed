package br.com.cronomed.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.com.cronomed.model.Dose
import br.com.cronomed.model.StatusDose
import java.io.File

/** Exporta o histórico de doses em CSV e abre a tela de compartilhar do Android. */
object Csv {

    fun montar(doses: List<Dose>): String {
        val sb = StringBuilder()
        sb.append("Data;Hora;Medicamento;Dosagem;Quantidade;Compartimento;Situação;Confirmada em\n")
        doses.forEach { d ->
            val situacao = when (d.status) {
                StatusDose.TOMADA -> "Tomada"
                StatusDose.PERDIDA -> "Perdida"
                else -> "Pendente"
            }
            sb.append(Formatadores.diaMes(d.agendadaEm)).append(';')
                .append(Formatadores.hora(d.agendadaEm)).append(';')
                .append(campo(d.nome)).append(';')
                .append(campo(d.dosagem)).append(';')
                .append(d.qtd).append(';')
                .append(d.compartimento).append(';')
                .append(situacao).append(';')
                .append(d.confirmadaEm?.let { Formatadores.dataHora(it) } ?: "")
                .append('\n')
        }
        return sb.toString()
    }

    /** Protege campos com ponto e vírgula, aspas ou quebra de linha. */
    fun campo(texto: String): String {
        val limpo = texto.replace("\"", "\"\"")
        return if (limpo.contains(';') || limpo.contains('"') || limpo.contains('\n')) "\"$limpo\"" else limpo
    }

    fun compartilhar(contexto: Context, doses: List<Dose>, titulo: String) {
        val pasta = File(contexto.cacheDir, "exports")
        pasta.mkdirs()
        val arquivo = File(pasta, "historico-cronomed.csv")
        // BOM no início para o Excel reconhecer os acentos
        arquivo.writeText("﻿" + montar(doses), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.putExtra(Intent.EXTRA_SUBJECT, titulo)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        contexto.startActivity(Intent.createChooser(intent, titulo))
    }
}
