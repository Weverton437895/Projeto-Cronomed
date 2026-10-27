package br.com.cronomed.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import br.com.cronomed.R
import br.com.cronomed.dao.DoseDao
import br.com.cronomed.dao.MedicamentoDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.data.Sincronizador
import br.com.cronomed.model.Medicamento
import br.com.cronomed.util.Formatadores
import br.com.cronomed.util.MascaraTextWatcher
import br.com.cronomed.util.Validadores
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Locale

/** Cadastro e edição de um medicamento em um compartimento da caixa. */
class FormularioMedicamentoActivity : BaseActivity() {

    private lateinit var spCompartimento: Spinner
    private lateinit var spUnidade: Spinner
    private lateinit var spRepetir: Spinner
    private lateinit var icone: CompartimentoIconView
    private lateinit var etNome: EditText
    private lateinit var etDosagem: EditText
    private lateinit var etQtd: EditText
    private lateinit var etInicio: EditText
    private lateinit var etDuracao: EditText
    private lateinit var etEstoque: EditText
    private lateinit var etInstrucoes: EditText
    private lateinit var grupoHorarios: ChipGroup

    private val medDao = MedicamentoDao()
    private val horarios = sortedSetOf<String>()
    private var existentes: List<Medicamento> = emptyList()
    private var original: Medicamento? = null
    private var livres: List<Int> = emptyList()
    private val unidades = listOf("comprimido", "cápsula", "gota", "ml", "sachê", "ampola", "dose")
    private val intervalos = listOf(2, 5, 10, 15)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulario_medicamento)
        if (!sessaoValida()) return
        if (!Sessao.podeGerenciar) {
            finish()
            return
        }
        configurarVoltar(getString(R.string.medicamento))

        spCompartimento = findViewById(R.id.spCompartimento)
        spUnidade = findViewById(R.id.spUnidade)
        spRepetir = findViewById(R.id.spRepetir)
        icone = findViewById(R.id.ivCompartimento)
        etNome = findViewById(R.id.etNome)
        etDosagem = findViewById(R.id.etDosagem)
        etQtd = findViewById(R.id.etQtd)
        etInicio = findViewById(R.id.etInicio)
        etDuracao = findViewById(R.id.etDuracao)
        etEstoque = findViewById(R.id.etEstoque)
        etInstrucoes = findViewById(R.id.etInstrucoes)
        grupoHorarios = findViewById(R.id.grupoHorarios)

        MascaraTextWatcher.aplicar(etInicio, MascaraTextWatcher.DATA)
        spUnidade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unidades)
        spRepetir.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            intervalos.map { getString(R.string.minutos, it) }
        )
        spRepetir.setSelection(intervalos.indexOf(5))

        findViewById<Button>(R.id.btnAddHorario).setOnClickListener { escolherHorario() }
        findViewById<Button>(R.id.btnSalvar).setOnClickListener { salvar() }
        findViewById<Button>(R.id.btnExcluir).setOnClickListener { excluir() }

        carregarDados()
    }

    private fun carregarDados() {
        val paciente = Sessao.paciente ?: return
        val pedido = intent.getIntExtra(EXTRA_COMPARTIMENTO, 1)
        carregando(true)
        medDao.listar(paciente.id, { meds ->
            carregando(false)
            existentes = meds
            original = meds.firstOrNull { it.compartimento == pedido }
            livres = (1..6).filter { n -> meds.none { it.compartimento == n } }
            prepararCompartimentos(pedido)
            original?.let { preencher(it) }
            if (original == null) {
                etInicio.setText(Formatadores.dataDoBanco(Formatadores.hojeParaBanco()))
            }
        }, { mostrarErro(it) })
    }

    private fun prepararCompartimentos(pedido: Int) {
        val opcoes = if (original != null) listOf(pedido) else livres
        if (opcoes.isEmpty()) {
            mensagem(R.string.erro_sem_compartimento_livre)
            finish()
            return
        }
        spCompartimento.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            opcoes.map { getString(R.string.compartimento_numero, it) }
        )
        spCompartimento.isEnabled = original == null && opcoes.size > 1
        val inicial = if (opcoes.contains(pedido)) opcoes.indexOf(pedido) else 0
        spCompartimento.setSelection(inicial)
        icone.definirCompartimento(opcoes[inicial])
        spCompartimento.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(pai: AdapterView<*>?, v: View?, posicao: Int, id: Long) {
                icone.definirCompartimento(opcoes[posicao])
            }

            override fun onNothingSelected(pai: AdapterView<*>?) {}
        }
    }

    private fun compartimentoEscolhido(): Int {
        val opcoes = if (original != null) listOf(original!!.compartimento) else livres
        return opcoes[spCompartimento.selectedItemPosition.coerceAtLeast(0)]
    }

    private fun preencher(med: Medicamento) {
        findViewById<View>(R.id.btnExcluir).visibility = View.VISIBLE
        etNome.setText(med.nome)
        etDosagem.setText(med.dosagem)
        etQtd.setText(med.qtdPorDose.toString())
        etInicio.setText(Formatadores.dataDoBanco(med.dataInicio))
        etDuracao.setText(med.duracaoDias.toString())
        etEstoque.setText(med.estoque.toString())
        etInstrucoes.setText(med.instrucoes)
        spUnidade.setSelection(unidades.indexOf(med.unidade).coerceAtLeast(0))
        spRepetir.setSelection(intervalos.indexOf(med.repetirMin).coerceAtLeast(0))
        horarios.clear()
        horarios.addAll(med.horarios)
        desenharHorarios()
    }

    private fun escolherHorario() {
        TimePickerDialog(this, { _, hora, minuto ->
            horarios.add(String.format(Locale.US, "%02d:%02d", hora, minuto))
            desenharHorarios()
        }, 8, 0, true).show()
    }

    private fun desenharHorarios() {
        grupoHorarios.removeAllViews()
        horarios.forEach { h ->
            val chip = Chip(this)
            chip.text = h
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                horarios.remove(h)
                desenharHorarios()
            }
            grupoHorarios.addView(chip)
        }
    }

    private fun salvar() {
        val paciente = Sessao.paciente ?: return
        val nome = etNome.text.toString().trim()
        val qtd = etQtd.text.toString().toIntOrNull() ?: 0
        val duracao = etDuracao.text.toString().toIntOrNull() ?: 0
        val estoque = etEstoque.text.toString().toIntOrNull()
        val inicioBr = etInicio.text.toString()

        if (nome.isEmpty()) {
            etNome.error = getString(R.string.erro_nome_medicamento)
            return
        }
        if (qtd < 1) {
            etQtd.error = getString(R.string.erro_qtd_dose)
            return
        }
        if (horarios.isEmpty()) {
            mensagem(R.string.erro_sem_horario)
            return
        }
        if (!Validadores.dataValida(inicioBr)) {
            etInicio.error = getString(R.string.erro_data_invalida)
            return
        }
        if (estoque == null) {
            etEstoque.error = getString(R.string.erro_estoque)
            return
        }

        val comp = compartimentoEscolhido()
        val antigo = original
        val estoqueInicial = if (antigo == null) estoque else maxOf(antigo.estoqueInicial, estoque)
        val med = Medicamento(
            compartimento = comp,
            nome = nome,
            dosagem = etDosagem.text.toString().trim(),
            unidade = unidades[spUnidade.selectedItemPosition.coerceAtLeast(0)],
            qtdPorDose = qtd,
            duracaoDias = duracao,
            dataInicio = Formatadores.dataParaBanco(inicioBr),
            estoque = estoque,
            estoqueInicial = estoqueInicial,
            horarios = horarios.toList(),
            repetirMin = intervalos[spRepetir.selectedItemPosition.coerceAtLeast(0)],
            instrucoes = etInstrucoes.text.toString().trim(),
            ativo = true
        )

        carregando(true)
        val doseDao = DoseDao()
        medDao.salvar(paciente.id, med, {
            // Os horários podem ter mudado: apaga as doses futuras e gera de novo
            doseDao.removerFuturas(paciente.id, comp, {
                Sincronizador.sincronizar(this, paciente.id, true, {
                    carregando(false)
                    mensagem(R.string.medicamento_salvo)
                    finish()
                }, { mostrarErro(it) })
            }, { mostrarErro(it) })
        }, { mostrarErro(it) })
    }

    private fun excluir() {
        val paciente = Sessao.paciente ?: return
        val comp = original?.compartimento ?: return
        confirmar(
            getString(R.string.excluir_medicamento),
            getString(R.string.excluir_medicamento_texto, original?.nome ?: ""),
            getString(R.string.excluir)
        ) {
            carregando(true)
            medDao.excluir(paciente.id, comp, {
                DoseDao().removerFuturas(paciente.id, comp, {
                    Sincronizador.sincronizar(this, paciente.id, true, {
                        carregando(false)
                        mensagem(R.string.medicamento_excluido)
                        finish()
                    }, { mostrarErro(it) })
                }, { mostrarErro(it) })
            }, { mostrarErro(it) })
        }
    }

    companion object {
        const val EXTRA_COMPARTIMENTO = "compartimento"
    }
}
