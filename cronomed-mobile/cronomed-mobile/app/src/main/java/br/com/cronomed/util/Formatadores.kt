package br.com.cronomed.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatadores {
    private val PT_BR = Locale("pt", "BR")

    fun hora(ms: Long): String = SimpleDateFormat("HH:mm", PT_BR).format(Date(ms))

    fun diaMes(ms: Long): String = SimpleDateFormat("dd/MM", PT_BR).format(Date(ms))

    fun dataHora(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", PT_BR).format(Date(ms))

    /** "Terça-feira, 20 de outubro" */
    fun dataLonga(ms: Long): String {
        val texto = SimpleDateFormat("EEEE, d 'de' MMMM", PT_BR).format(Date(ms))
        return texto.replaceFirstChar { it.uppercase() }
    }

    /** "Hoje", "Ontem" ou "19/10". */
    fun diaRelativo(ms: Long, agora: Long = System.currentTimeMillis()): String {
        val a = inicioDoDia(agora)
        val d = inicioDoDia(ms)
        val umDia = 24L * 60 * 60 * 1000
        return when {
            d == a -> "Hoje"
            d == a - umDia -> "Ontem"
            else -> diaMes(ms)
        }
    }

    fun inicioDoDia(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** "dd/MM/aaaa" -> "aaaa-MM-dd" (formato guardado no banco). */
    fun dataParaBanco(dataBr: String): String {
        val p = dataBr.split("/")
        if (p.size != 3) return ""
        return p[2] + "-" + p[1].padStart(2, '0') + "-" + p[0].padStart(2, '0')
    }

    /** "aaaa-MM-dd" -> "dd/MM/aaaa". */
    fun dataDoBanco(dataBanco: String): String {
        val p = dataBanco.split("-")
        if (p.size != 3) return ""
        return p[2] + "/" + p[1] + "/" + p[0]
    }

    fun hojeParaBanco(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** "há 3 min", "há 2 h". */
    fun haQuanto(ms: Long?, agora: Long = System.currentTimeMillis()): String {
        if (ms == null) return "sem sinal ainda"
        val min = ((agora - ms) / 60000).coerceAtLeast(0)
        return when {
            min < 1 -> "agora"
            min < 60 -> "há $min min"
            min < 60 * 24 -> "há ${min / 60} h"
            else -> "há ${min / (60 * 24)} d"
        }
    }

    fun iniciais(nome: String): String {
        val partes = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (partes.isEmpty()) return "?"
        val primeira = partes.first().first().uppercaseChar()
        return if (partes.size == 1) primeira.toString() else (primeira.toString() + partes.last().first().uppercaseChar())
    }

    fun primeiroNome(nome: String): String = nome.trim().split(Regex("\\s+")).firstOrNull() ?: ""

    fun quantidade(qtd: Int, unidade: String): String {
        val plural = when (unidade.lowercase(PT_BR)) {
            "comprimido" -> "comprimidos"
            "cápsula" -> "cápsulas"
            "gota" -> "gotas"
            "sachê" -> "sachês"
            "ampola" -> "ampolas"
            "dose" -> "doses"
            else -> unidade
        }
        return if (qtd == 1) "$qtd $unidade" else "$qtd $plural"
    }
}
