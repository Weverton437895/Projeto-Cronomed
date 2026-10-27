package br.com.cronomed.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.alarme.Notificacoes
import br.com.cronomed.dao.DoseDao
import br.com.cronomed.data.AgendaLocal
import br.com.cronomed.data.Sessao
import br.com.cronomed.model.Nivel
import br.com.cronomed.util.Formatadores
import br.com.cronomed.util.Voz

/**
 * Tela cheia que aparece na hora do remédio (aberta pela notificação). Lê os dados da agenda
 * guardada no aparelho, então funciona mesmo sem internet. Pode narrar a dose em voz alta e
 * entender "sim" ou "não" por voz.
 */
class LembreteActivity : BaseActivity() {

    private var voz: Voz? = null
    private var doseId: String = ""
    private var pacienteId: String = ""
    private lateinit var tvVoz: TextView

    override fun temaDaTela(): Int = R.style.Theme_CronoMed_Azul
    override fun aceitaAltoContraste(): Boolean = false

    private val pedirMicrofone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) ouvir() else mensagem(R.string.erro_microfone)
        }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Aparece mesmo com a tela bloqueada e acende a tela
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setContentView(R.layout.activity_lembrete)

        doseId = intent.getStringExtra(Notificacoes.EXTRA_DOSE) ?: ""
        pacienteId = intent.getStringExtra(Notificacoes.EXTRA_PACIENTE) ?: ""
        val item = AgendaLocal(this).buscar(doseId)
        if (item == null) {
            mensagem(R.string.erro_dose_nao_encontrada)
            finish()
            return
        }
        val dose = item.dose
        tvVoz = findViewById(R.id.tvVoz)

        findViewById<CompartimentoIconView>(R.id.ivCompartimento).definirCompartimento(dose.compartimento)
        findViewById<TextView>(R.id.tvHora).text = Formatadores.hora(dose.agendadaEm)
        findViewById<TextView>(R.id.tvNome).text = dose.nome
        findViewById<TextView>(R.id.tvDetalhe).text = getString(
            R.string.dose_detalhe, dose.dosagem, Formatadores.quantidade(dose.qtd, dose.unidade)
        )
        findViewById<TextView>(R.id.tvContagemLembrete).text = getString(
            R.string.lembrete_numero, item.lembretesEnviados.coerceAtLeast(1), AlarmeScheduler.MAX_LEMBRETES
        )
        val tvInstrucoes = findViewById<TextView>(R.id.tvInstrucoes)
        if (dose.instrucoes.isNotBlank()) {
            tvInstrucoes.visibility = View.VISIBLE
            tvInstrucoes.text = dose.instrucoes
        }

        // Quem só visualiza não confirma a dose do outro: pode apenas fechar o aviso
        val podeConfirmar = Sessao.paciente == null || Sessao.podeGerenciar || Sessao.nivel == Nivel.TITULAR
        val btnTomei = findViewById<Button>(R.id.btnTomei)
        val btnVoz = findViewById<Button>(R.id.btnVoz)
        if (!podeConfirmar) {
            btnTomei.visibility = View.GONE
            btnVoz.visibility = View.GONE
        }

        btnTomei.setOnClickListener { confirmarDose() }
        findViewById<Button>(R.id.btnDepois).setOnClickListener {
            mensagem(R.string.lembrar_depois_ok)
            finish()
        }
        btnVoz.setOnClickListener { pedirParaOuvir() }

        if (podeConfirmar && (prefs.narracaoVoz || prefs.comandoVoz)) {
            voz = Voz(this)
            val frase = getString(
                R.string.fala_lembrete, dose.nome, Formatadores.quantidade(dose.qtd, dose.unidade)
            )
            voz?.falar(frase)
            if (prefs.comandoVoz) {
                // Espera a narração terminar (aproximação simples) antes de abrir o microfone
                btnVoz.postDelayed({ if (!isFinishing) pedirParaOuvir() }, 7000)
            }
        }
    }

    private fun pedirParaOuvir() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirMicrofone.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            ouvir()
        }
    }

    private fun ouvir() {
        if (voz == null) voz = Voz(this)
        tvVoz.visibility = View.VISIBLE
        tvVoz.text = getString(R.string.ouvindo)
        voz?.ouvir({ resposta -> tratarResposta(resposta) }, {
            tvVoz.text = getString(R.string.nao_entendi)
        })
    }

    private fun tratarResposta(resposta: Voz.Resposta) {
        if (resposta == Voz.Resposta.SIM) {
            confirmarDose()
        } else if (resposta == Voz.Resposta.NAO) {
            tvVoz.text = getString(R.string.entendi_nao)
            voz?.falar(getString(R.string.fala_vou_lembrar))
        } else {
            tvVoz.text = getString(R.string.nao_entendi)
        }
    }

    private fun confirmarDose() {
        val item = AgendaLocal(this).buscar(doseId)
        if (item == null) {
            finish()
            return
        }
        // Remove alarme e notificação já; o Firestore guarda a escrita se estiver sem internet
        AlarmeScheduler(this).concluirDose(doseId)
        DoseDao().confirmar(pacienteId, item.dose, {}, { mostrarErro(it) })
        mensagem(R.string.dose_confirmada)
        finish()
    }

    override fun onDestroy() {
        voz?.liberar()
        super.onDestroy()
    }
}
