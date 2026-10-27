package br.com.cronomed.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import br.com.cronomed.R
import com.google.android.material.materialswitch.MaterialSwitch

/** Acessibilidade (contraste, fonte, voz) e alertas. Tudo é guardado em SharedPreferences. */
class ConfiguracoesActivity : BaseActivity() {

    private val opcoesDias = listOf(3, 5, 7, 10, 14)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes)
        configurarVoltar(getString(R.string.menu_configuracoes))

        val swContraste = findViewById<MaterialSwitch>(R.id.swAltoContraste)
        swContraste.isChecked = prefs.altoContraste
        swContraste.setOnCheckedChangeListener { _, marcado ->
            prefs.altoContraste = marcado
            recreate() // recria a tela com o tema novo
        }

        val grupo = findViewById<RadioGroup>(R.id.rgFonte)
        val botoes = listOf(R.id.rbFontePadrao, R.id.rbFonteGrande, R.id.rbFonteExtra)
        findViewById<RadioButton>(botoes[prefs.tamanhoFonte.coerceIn(0, 2)]).isChecked = true
        grupo.setOnCheckedChangeListener { _, id ->
            val novo = botoes.indexOf(id)
            if (novo >= 0 && novo != prefs.tamanhoFonte) {
                prefs.tamanhoFonte = novo
                recreate()
            }
        }

        ligar(R.id.swNarracao, prefs.narracaoVoz) { prefs.narracaoVoz = it }
        ligar(R.id.swComandoVoz, prefs.comandoVoz) { prefs.comandoVoz = it }
        ligar(R.id.swNotificacoes, prefs.notificacoesDose) { prefs.notificacoesDose = it }
        ligar(R.id.swBateria, prefs.avisoBateria) { prefs.avisoBateria = it }

        val spinner = findViewById<Spinner>(R.id.spDiasEstoque)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            opcoesDias.map { resources.getQuantityString(R.plurals.dias_antes, it, it) }
        )
        spinner.setSelection(opcoesDias.indexOf(prefs.diasAvisoEstoque).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(pai: AdapterView<*>?, v: View?, posicao: Int, id: Long) {
                prefs.diasAvisoEstoque = opcoesDias[posicao]
            }

            override fun onNothingSelected(pai: AdapterView<*>?) {}
        }

        val versao = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        findViewById<TextView>(R.id.tvVersao).text = getString(R.string.versao_app, versao)
    }

    private fun ligar(id: Int, inicial: Boolean, aoMudar: (Boolean) -> Unit) {
        val chave = findViewById<MaterialSwitch>(id)
        chave.isChecked = inicial
        chave.setOnCheckedChangeListener { _, marcado -> aoMudar(marcado) }
    }
}
