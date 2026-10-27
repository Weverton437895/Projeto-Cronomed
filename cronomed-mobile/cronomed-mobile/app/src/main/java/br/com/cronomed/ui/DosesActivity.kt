package br.com.cronomed.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.cronomed.R
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.alarme.Notificacoes
import br.com.cronomed.dao.DoseDao
import br.com.cronomed.dao.MedicamentoDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.data.Sincronizador
import br.com.cronomed.model.Dose
import br.com.cronomed.model.StatusDose
import br.com.cronomed.util.Formatadores
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tela inicial: doses de hoje, adesão dos últimos 30 dias e aviso de estoque baixo. */
class DosesActivity : BaseActivity() {

    private lateinit var adaptador: DoseAdapter
    private lateinit var tvSaudacao: TextView
    private lateinit var tvPaciente: TextView
    private lateinit var tvAdesao: TextView
    private lateinit var tvBanner: TextView
    private lateinit var tvSecao: TextView
    private lateinit var tvVazio: TextView
    private lateinit var btnTrocar: Button

    private val doseDao = DoseDao()
    private var sincronizado = false
    private var dosesDeHoje: List<Dose> = emptyList()

    private val pedirNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sem ação extra */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doses)
        if (!sessaoValida()) return
        configurarTopo(getString(R.string.nav_doses))
        configurarNavegacao(R.id.nav_doses)

        tvSaudacao = findViewById(R.id.tvSaudacao)
        tvPaciente = findViewById(R.id.tvPaciente)
        tvAdesao = findViewById(R.id.tvAdesao)
        tvBanner = findViewById(R.id.tvBannerEstoque)
        tvSecao = findViewById(R.id.tvSecao)
        tvVazio = findViewById(R.id.tvVazio)
        btnTrocar = findViewById(R.id.btnTrocarPaciente)

        adaptador = DoseAdapter(emptyList(), Sessao.podeGerenciar, { pedirConfirmacao(it) }, { abrirLembrete(it) })
        val lista = findViewById<RecyclerView>(R.id.listaDoses)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adaptador

        tvBanner.setOnClickListener {
            startActivity(Intent(this, EstoqueActivity::class.java))
            finish()
        }
        btnTrocar.setOnClickListener { escolherPaciente() }

        pedirPermissoes()
    }

    override fun onResume() {
        super.onResume()
        if (Sessao.paciente == null) return
        mostrarCabecalho()
        if (!sincronizado) {
            sincronizar()
        } else {
            carregarLista()
        }
    }

    private fun mostrarCabecalho() {
        val usuario = Sessao.usuario
        tvSaudacao.text = getString(R.string.ola, Formatadores.primeiroNome(usuario?.nome ?: ""))
        val paciente = Sessao.paciente
        tvPaciente.text = if (Sessao.ehTitular) {
            Formatadores.dataLonga(System.currentTimeMillis())
        } else {
            getString(R.string.acompanhando, paciente?.nome ?: "")
        }
        btnTrocar.visibility = if (Sessao.contextos.size > 1) View.VISIBLE else View.GONE
        tvSecao.text = getString(R.string.doses_de_hoje)
    }

    private fun sincronizar() {
        val paciente = Sessao.paciente ?: return
        carregando(true)
        Sincronizador.sincronizar(this, paciente.id, Sessao.podeGerenciar, {
            sincronizado = true
            carregarLista()
        }, { mostrarErro(it) })
    }

    private fun carregarLista() {
        val paciente = Sessao.paciente ?: return
        val inicio = Formatadores.inicioDoDia(System.currentTimeMillis())
        carregando(true)
        doseDao.listarPeriodo(paciente.id, inicio, inicio + 24L * 60 * 60 * 1000 - 1, { doses ->
            carregando(false)
            dosesDeHoje = doses
            adaptador.atualizar(doses, Sessao.podeGerenciar)
            tvVazio.visibility = if (doses.isEmpty()) View.VISIBLE else View.GONE
            carregarResumo(paciente.id)
        }, { mostrarErro(it) })
    }

    /** Adesão dos últimos 30 dias e aviso de estoque baixo. */
    private fun carregarResumo(pacienteId: String) {
        val agora = System.currentTimeMillis()
        doseDao.listarPeriodo(pacienteId, agora - 30L * 24 * 60 * 60 * 1000, agora, { doses ->
            val tomadas = doses.count { it.status == StatusDose.TOMADA }
            val perdidas = doses.count { it.status == StatusDose.PERDIDA }
            if (tomadas + perdidas > 0) {
                val pct = tomadas * 100 / (tomadas + perdidas)
                tvAdesao.visibility = View.VISIBLE
                tvAdesao.text = getString(R.string.adesao_30_dias, pct)
                tvAdesao.setBackgroundResource(
                    if (pct >= 80) R.drawable.bg_selo_sucesso else R.drawable.bg_selo_alerta
                )
                tvAdesao.setTextColor(ContextCompat.getColor(this, if (pct >= 80) R.color.sucesso else R.color.alerta))
            } else {
                tvAdesao.visibility = View.GONE
            }
        }, { /* o resumo é opcional: não atrapalha a tela */ })

        MedicamentoDao().listar(pacienteId, { meds ->
            val limite = prefs.diasAvisoEstoque
            val critico = meds.filter { (it.diasRestantes() ?: Int.MAX_VALUE) <= limite }
                .minByOrNull { it.diasRestantes() ?: Int.MAX_VALUE }
            if (critico != null) {
                val dias = critico.diasRestantes() ?: 0
                tvBanner.visibility = View.VISIBLE
                tvBanner.text = if (dias <= 0) {
                    getString(R.string.banner_estoque_acabou, critico.nome)
                } else {
                    getString(R.string.banner_estoque, critico.nome, dias)
                }
            } else {
                tvBanner.visibility = View.GONE
            }
        }, { /* idem */ })
    }

    private fun pedirConfirmacao(dose: Dose) {
        confirmar(
            getString(R.string.confirmar_dose_titulo),
            getString(R.string.confirmar_dose_texto, dose.nome, Formatadores.quantidade(dose.qtd, dose.unidade)),
            getString(R.string.ja_tomei)
        ) { confirmarDose(dose) }
    }

    private fun confirmarDose(dose: Dose) {
        val paciente = Sessao.paciente ?: return
        // Com ou sem internet o alarme e a notificação desta dose somem na hora;
        // o Firestore guarda a escrita e envia assim que houver conexão.
        AlarmeScheduler(this).concluirDose(dose.id)
        doseDao.confirmar(paciente.id, dose, { carregarResumo(paciente.id) }, { mostrarErro(it) })
        dosesDeHoje = dosesDeHoje.map {
            if (it.id == dose.id) it.copy(status = StatusDose.TOMADA, confirmadaEm = System.currentTimeMillis()) else it
        }
        adaptador.atualizar(dosesDeHoje, Sessao.podeGerenciar)
        mensagem(R.string.dose_confirmada)
    }

    private fun abrirLembrete(dose: Dose) {
        val paciente = Sessao.paciente ?: return
        val intent = Intent(this, LembreteActivity::class.java)
        intent.putExtra(Notificacoes.EXTRA_DOSE, dose.id)
        intent.putExtra(Notificacoes.EXTRA_PACIENTE, paciente.id)
        startActivity(intent)
    }

    private fun escolherPaciente() {
        val nomes = Sessao.contextos.map {
            if (it.paciente.id == Sessao.usuario?.uid) getString(R.string.meu_tratamento)
            else it.paciente.nome + " (" + it.nivel.rotulo + ")"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trocar_paciente_titulo)
            .setItems(nomes) { _, indice ->
                val escolhido = Sessao.contextos[indice].paciente.id
                Sessao.escolherPaciente(escolhido)
                prefs.pacienteEscolhido = escolhido
                sincronizado = false
                mostrarCabecalho()
                sincronizar()
            }
            .show()
    }

    // ---------- Permissões necessárias para os lembretes ----------

    private fun pedirPermissoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !prefs.avisouAlarmeExato) {
            val gerente = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!gerente.canScheduleExactAlarms()) {
                prefs.avisouAlarmeExato = true
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.alarme_exato_titulo)
                    .setMessage(R.string.alarme_exato_texto)
                    .setPositiveButton(R.string.abrir_ajustes) { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.agora_nao, null)
                    .show()
            }
        }
    }
}
