package br.com.cronomed.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.dao.MedicamentoDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.model.Medicamento
import br.com.cronomed.util.Formatadores
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Quanto resta de cada remédio e para quantos dias dá. Permite registrar uma compra (reabastecer). */
class EstoqueActivity : BaseActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var tvVazio: TextView
    private val medDao = MedicamentoDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estoque)
        if (!sessaoValida()) return
        configurarTopo(getString(R.string.nav_estoque))
        configurarNavegacao(R.id.nav_estoque)
        lista = findViewById(R.id.listaEstoque)
        tvVazio = findViewById(R.id.tvVazio)
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        val paciente = Sessao.paciente ?: return
        carregando(true)
        medDao.listar(paciente.id, { meds ->
            carregando(false)
            mostrar(meds)
        }, { mostrarErro(it) })
    }

    private fun mostrar(meds: List<Medicamento>) {
        lista.removeAllViews()
        tvVazio.visibility = if (meds.isEmpty()) View.VISIBLE else View.GONE
        val inflador = LayoutInflater.from(this)
        // Os que acabam primeiro aparecem no topo
        meds.sortedBy { it.diasRestantes() ?: Int.MAX_VALUE }.forEach { med ->
            val visao = inflador.inflate(R.layout.item_estoque, lista, false)
            visao.findViewById<CompartimentoIconView>(R.id.ivCompartimento).definirCompartimento(med.compartimento)
            visao.findViewById<TextView>(R.id.tvNome).text = med.nome
            visao.findViewById<TextView>(R.id.tvQuantidade).text =
                getString(R.string.restam, Formatadores.quantidade(med.estoque, med.unidade))

            val barra = visao.findViewById<ProgressBar>(R.id.barra)
            barra.progress = if (med.estoqueInicial > 0) {
                (med.estoque * 100 / med.estoqueInicial).coerceIn(0, 100)
            } else 0

            val dias = med.diasRestantes()
            val tvDias = visao.findViewById<TextView>(R.id.tvDias)
            val selo = visao.findViewById<TextView>(R.id.tvSelo)
            if (dias == null) {
                tvDias.setText(R.string.sem_horarios)
                selo.visibility = View.GONE
            } else {
                tvDias.text = resources.getQuantityString(R.plurals.dias_de_estoque, dias, dias)
                when {
                    dias <= 3 -> estiloSelo(selo, R.string.selo_acabando, R.drawable.bg_selo_perigo, R.color.perigo)
                    dias <= 14 -> estiloSelo(selo, R.string.selo_atencao, R.drawable.bg_selo_alerta, R.color.alerta)
                    else -> estiloSelo(selo, R.string.selo_em_dia, R.drawable.bg_selo_sucesso, R.color.sucesso)
                }
            }

            val botao = visao.findViewById<Button>(R.id.btnReabastecer)
            if (Sessao.podeGerenciar) {
                botao.setOnClickListener { pedirQuantidade(med) }
            } else {
                botao.visibility = View.GONE
            }
            lista.addView(visao)
        }
    }

    private fun estiloSelo(selo: TextView, texto: Int, fundo: Int, cor: Int) {
        selo.setText(texto)
        selo.setBackgroundResource(fundo)
        selo.setTextColor(ContextCompat.getColor(this, cor))
    }

    private fun pedirQuantidade(med: Medicamento) {
        val paciente = Sessao.paciente ?: return
        val campo = EditText(this)
        campo.hint = getString(R.string.quantidade_comprada)
        campo.inputType = InputType.TYPE_CLASS_NUMBER
        val margem = (20 * resources.displayMetrics.density).toInt()
        val recipiente = FrameLayout(this)
        recipiente.setPadding(margem, margem / 2, margem, 0)
        recipiente.addView(campo)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reabastecer_titulo, med.nome))
            .setView(recipiente)
            .setPositiveButton(R.string.confirmar) { _, _ ->
                val q = campo.text.toString().toIntOrNull() ?: 0
                if (q < 1) {
                    mensagem(R.string.erro_quantidade)
                } else {
                    carregando(true)
                    medDao.reabastecer(paciente.id, med.compartimento, q, {
                        mensagem(R.string.estoque_atualizado)
                        carregar()
                    }, { mostrarErro(it) })
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}
