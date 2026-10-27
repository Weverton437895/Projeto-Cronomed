package br.com.cronomed.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.dao.MedicamentoDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.model.Medicamento
import br.com.cronomed.util.Formatadores

/** Os 6 compartimentos da caixa: cada um mostra o medicamento ou aparece como "livre". */
class MedicamentosActivity : BaseActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var tvResumo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicamentos)
        if (!sessaoValida()) return
        configurarTopo(getString(R.string.nav_medicamentos))
        configurarNavegacao(R.id.nav_medicamentos)
        lista = findViewById(R.id.listaMedicamentos)
        tvResumo = findViewById(R.id.tvResumo)
    }

    override fun onResume() {
        super.onResume()
        val paciente = Sessao.paciente ?: return
        carregando(true)
        MedicamentoDao().listar(paciente.id, { meds ->
            carregando(false)
            mostrar(meds)
        }, { mostrarErro(it) })
    }

    private fun mostrar(meds: List<Medicamento>) {
        lista.removeAllViews()
        tvResumo.text = getString(R.string.compartimentos_ocupados, meds.size, 6)
        val inflador = LayoutInflater.from(this)
        for (numero in 1..6) {
            val med = meds.firstOrNull { it.compartimento == numero }
            val visao = inflador.inflate(R.layout.item_medicamento, lista, false)
            visao.findViewById<CompartimentoIconView>(R.id.ivCompartimento).definirCompartimento(numero)
            val nome = visao.findViewById<TextView>(R.id.tvNome)
            val detalhe = visao.findViewById<TextView>(R.id.tvDetalhe)
            val horarios = visao.findViewById<TextView>(R.id.tvHorarios)
            val selo = visao.findViewById<TextView>(R.id.tvSelo)
            if (med == null) {
                nome.text = getString(R.string.compartimento_livre, numero)
                detalhe.text = if (Sessao.podeGerenciar) getString(R.string.toque_para_adicionar) else ""
                horarios.visibility = View.GONE
                selo.visibility = View.GONE
            } else {
                nome.text = med.nome
                detalhe.text = getString(
                    R.string.dose_detalhe, med.dosagem,
                    Formatadores.quantidade(med.qtdPorDose, med.unidade)
                )
                horarios.visibility = View.VISIBLE
                horarios.text = med.horarios.joinToString(" • ")
                val dias = med.diasRestantes()
                selo.visibility = View.VISIBLE
                when {
                    dias == null -> selo.visibility = View.GONE
                    dias <= 3 -> estiloSelo(selo, R.string.selo_acabando, R.drawable.bg_selo_perigo, R.color.perigo)
                    dias <= 14 -> estiloSelo(selo, R.string.selo_atencao, R.drawable.bg_selo_alerta, R.color.alerta)
                    else -> estiloSelo(selo, R.string.selo_em_dia, R.drawable.bg_selo_sucesso, R.color.sucesso)
                }
            }
            if (Sessao.podeGerenciar) {
                visao.setOnClickListener {
                    val intent = Intent(this, FormularioMedicamentoActivity::class.java)
                    intent.putExtra(FormularioMedicamentoActivity.EXTRA_COMPARTIMENTO, numero)
                    startActivity(intent)
                }
            }
            lista.addView(visao)
        }
    }

    private fun estiloSelo(selo: TextView, texto: Int, fundo: Int, cor: Int) {
        selo.setText(texto)
        selo.setBackgroundResource(fundo)
        selo.setTextColor(ContextCompat.getColor(this, cor))
    }
}
