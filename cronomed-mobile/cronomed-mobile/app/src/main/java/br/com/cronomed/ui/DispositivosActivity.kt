package br.com.cronomed.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.dao.DispositivoDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.model.Dispositivo
import br.com.cronomed.util.Formatadores
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Caixas CronoMed vinculadas ao paciente. Aqui o vínculo é um cadastro no Firestore (nome + código
 * da caixa); a configuração de Wi-Fi por Bluetooth da caixa não faz parte deste app.
 */
class DispositivosActivity : BaseActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var tvVazio: TextView
    private val dao = DispositivoDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispositivos)
        if (!sessaoValida()) return
        configurarVoltar(getString(R.string.menu_dispositivos))
        lista = findViewById(R.id.listaDispositivos)
        tvVazio = findViewById(R.id.tvVazio)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdicionar)
        if (Sessao.podeGerenciar) {
            fab.setOnClickListener { abrirDialogo() }
        } else {
            fab.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        val paciente = Sessao.paciente ?: return
        carregando(true)
        dao.listar(paciente.id, { itens ->
            carregando(false)
            mostrar(itens)
        }, { mostrarErro(it) })
    }

    private fun mostrar(itens: List<Dispositivo>) {
        lista.removeAllViews()
        tvVazio.visibility = if (itens.isEmpty()) View.VISIBLE else View.GONE
        val inflador = LayoutInflater.from(this)
        itens.forEach { d ->
            val visao = inflador.inflate(R.layout.item_dispositivo, lista, false)
            visao.findViewById<TextView>(R.id.tvNome).text = d.nome
            visao.findViewById<TextView>(R.id.tvMac).text = d.mac
            val selo = visao.findViewById<TextView>(R.id.tvSelo)
            if (d.online) {
                selo.setText(R.string.selo_online)
                selo.setBackgroundResource(R.drawable.bg_selo_sucesso)
                selo.setTextColor(ContextCompat.getColor(this, R.color.sucesso))
            } else {
                selo.setText(R.string.selo_offline)
                selo.setBackgroundResource(R.drawable.bg_selo_neutro)
                selo.setTextColor(ContextCompat.getColor(this, R.color.texto2))
            }
            val tvBateria = visao.findViewById<TextView>(R.id.tvBateria)
            if (d.bateria != null) {
                var texto = getString(R.string.bateria_nivel, d.bateria)
                if (prefs.avisoBateria && d.bateria < 20) texto += " — " + getString(R.string.bateria_baixa)
                tvBateria.text = texto
            } else {
                tvBateria.setText(R.string.bateria_desconhecida)
            }
            visao.findViewById<TextView>(R.id.tvSinc).text =
                getString(R.string.ultima_sincronizacao, Formatadores.haQuanto(d.ultimoSincEm))

            val revogar = visao.findViewById<Button>(R.id.btnRevogar)
            if (Sessao.podeGerenciar) {
                revogar.setOnClickListener {
                    confirmar(
                        getString(R.string.revogar_dispositivo),
                        getString(R.string.revogar_texto, d.nome),
                        getString(R.string.revogar)
                    ) {
                        val paciente = Sessao.paciente ?: return@confirmar
                        carregando(true)
                        dao.revogar(paciente.id, d.id, { carregar() }, { mostrarErro(it) })
                    }
                }
            } else {
                revogar.visibility = View.GONE
            }
            lista.addView(visao)
        }
    }

    private fun abrirDialogo() {
        val visao = LayoutInflater.from(this).inflate(R.layout.dialog_dispositivo, null)
        val etNome = visao.findViewById<EditText>(R.id.etNomeDispositivo)
        val etMac = visao.findViewById<EditText>(R.id.etMac)
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.adicionar_dispositivo)
            .setView(visao)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialogo.show()
        dialogo.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val nome = etNome.text.toString().trim()
            val mac = etMac.text.toString().trim().uppercase()
            if (nome.isEmpty()) {
                etNome.error = getString(R.string.erro_nome_obrigatorio)
                return@setOnClickListener
            }
            if (!MAC.matches(mac)) {
                etMac.error = getString(R.string.erro_codigo_dispositivo)
                return@setOnClickListener
            }
            val paciente = Sessao.paciente ?: return@setOnClickListener
            dialogo.dismiss()
            carregando(true)
            dao.cadastrar(paciente.id, nome, mac, {
                mensagem(R.string.dispositivo_cadastrado)
                carregar()
            }, { mostrarErro(it) })
        }
    }

    companion object {
        private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    }
}
