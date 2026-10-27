package br.com.cronomed.data

import android.content.Context
import android.content.SharedPreferences

/** Preferências guardadas no aparelho (acessibilidade e alertas) com SharedPreferences. */
class Preferencias(contexto: Context) {
    private val prefs: SharedPreferences =
        contexto.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    var altoContraste: Boolean
        get() = prefs.getBoolean("alto_contraste", false)
        set(v) = prefs.edit().putBoolean("alto_contraste", v).apply()

    /** 0 = padrão, 1 = grande, 2 = extra grande */
    var tamanhoFonte: Int
        get() = prefs.getInt("tamanho_fonte", 0)
        set(v) = prefs.edit().putInt("tamanho_fonte", v).apply()

    var narracaoVoz: Boolean
        get() = prefs.getBoolean("narracao_voz", false)
        set(v) = prefs.edit().putBoolean("narracao_voz", v).apply()

    var comandoVoz: Boolean
        get() = prefs.getBoolean("comando_voz", false)
        set(v) = prefs.edit().putBoolean("comando_voz", v).apply()

    var diasAvisoEstoque: Int
        get() = prefs.getInt("dias_aviso_estoque", 5)
        set(v) = prefs.edit().putInt("dias_aviso_estoque", v).apply()

    var notificacoesDose: Boolean
        get() = prefs.getBoolean("notificacoes_dose", true)
        set(v) = prefs.edit().putBoolean("notificacoes_dose", v).apply()

    var avisoBateria: Boolean
        get() = prefs.getBoolean("aviso_bateria", true)
        set(v) = prefs.edit().putBoolean("aviso_bateria", v).apply()

    var biometria: Boolean
        get() = prefs.getBoolean("biometria", false)
        set(v) = prefs.edit().putBoolean("biometria", v).apply()

    var avisouAlarmeExato: Boolean
        get() = prefs.getBoolean("avisou_alarme_exato", false)
        set(v) = prefs.edit().putBoolean("avisou_alarme_exato", v).apply()

    var pacienteEscolhido: String?
        get() = prefs.getString("paciente_escolhido", null)
        set(v) = prefs.edit().putString("paciente_escolhido", v).apply()

    fun escala(): Float = when (tamanhoFonte) {
        1 -> 1.15f
        2 -> 1.3f
        else -> 1.0f
    }

    companion object {
        private const val ARQUIVO = "cronomed_prefs"
    }
}
