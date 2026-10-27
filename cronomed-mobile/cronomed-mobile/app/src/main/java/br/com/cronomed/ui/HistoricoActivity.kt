package br.com.cronomed.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.cronomed.R
import br.com.cronomed.dao.DoseDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.model.Dose
import br.com.cronomed.model.StatusDose
import br.com.cronomed.util.Csv

/** Histórico das doses tomadas e perdidas, com adesão do período e exportação em CSV. */
class HistoricoActivity : BaseActivity() {

    private lateinit var adaptador: HistoricoAdapter
    private lateinit var tvResumo: TextView
    private lateinit var tvVazio: TextView
    private lateinit var spPeriodo: Spinner
    private val periodos = listOf(7, 30, 90)
    private var doses: List<Dose> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico)
        if (!sessaoValida()) return
        configurarTopo(getString(R.string.nav_historico))
        configurarNavegacao(R.id.nav_historico)

        tvResumo = findViewById(R.id.tvResumo)
        tvVazio = findViewById(R.id.tvVazio)
        spPeriodo = findViewById(R.id.spPeriodo)
        adaptador = HistoricoAdapter(emptyList())
        val lista = findViewById<RecyclerView>(R.id.listaHistorico)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adaptador

        spPeriodo.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            periodos.map { getString(R.string.ultimos_dias, it) }
        )
        spPeriodo.setSelection(1)
        spPeriodo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(pai: AdapterView<*>?, v: View?, posicao: Int, id: Long) {
                carregar(periodos[posicao])
            }

            override fun onNothingSelected(pai: AdapterView<*>?) {}
        }

        findViewById<ImageButton>(R.id.btnExportar).setOnClickListener {
            if (doses.isEmpty()) {
                mensagem(R.string.historico_vazio)
            } else {
                Csv.compartilhar(this, doses.sortedBy { it.agendadaEm }, getString(R.string.exportar_csv))
            }
        }
    }

    private fun carregar(dias: Int) {
        val paciente = Sessao.paciente ?: return
        val agora = System.currentTimeMillis()
        carregando(true)
        DoseDao().listarPeriodo(paciente.id, agora - dias * 24L * 60 * 60 * 1000, agora, { lista ->
            carregando(false)
            // Doses pendentes ainda não são "histórico"
            doses = lista.filter { it.status != StatusDose.PENDENTE }
            adaptador.atualizar(doses)
            tvVazio.visibility = if (doses.isEmpty()) View.VISIBLE else View.GONE
            val tomadas = doses.count { it.status == StatusDose.TOMADA }
            val total = doses.size
            if (total > 0) {
                val pct = tomadas * 100 / total
                tvResumo.visibility = View.VISIBLE
                tvResumo.text = getString(R.string.resumo_historico, pct, tomadas, total)
                tvResumo.setBackgroundResource(if (pct >= 80) R.drawable.bg_selo_sucesso else R.drawable.bg_selo_alerta)
                tvResumo.setTextColor(ContextCompat.getColor(this, if (pct >= 80) R.color.sucesso else R.color.alerta))
            } else {
                tvResumo.visibility = View.GONE
            }
        }, { mostrarErro(it) })
    }
}
