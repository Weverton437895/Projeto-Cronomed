package br.com.cronomed

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import br.com.cronomed.alarme.Notificacoes

/** Classe Application: roda uma vez quando o app abre, antes de qualquer tela. */
class CronoMedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // O visual do CronoMed é sempre claro (a identidade visual não prevê modo escuro)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Notificacoes.criarCanais(this)
    }
}
