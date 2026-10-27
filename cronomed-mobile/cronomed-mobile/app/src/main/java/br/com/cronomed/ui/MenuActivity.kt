package br.com.cronomed.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import br.com.cronomed.R
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.data.Sessao
import br.com.cronomed.util.Formatadores
import com.google.firebase.auth.FirebaseAuth

/** Menu lateral: painel que abre sobre a tela atual (tema Painel, translúcido). */
class MenuActivity : BaseActivity() {

    override fun temaDaTela(): Int = R.style.Theme_CronoMed_Painel
    override fun aceitaAltoContraste(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        if (!sessaoValida()) return

        val usuario = Sessao.usuario
        findViewById<TextView>(R.id.tvIniciais).text = Formatadores.iniciais(usuario?.nome ?: "")
        findViewById<TextView>(R.id.tvNomeMenu).text = usuario?.nome ?: ""
        findViewById<TextView>(R.id.tvEmailMenu).text = usuario?.email ?: ""

        // Tocar fora do painel fecha o menu
        findViewById<View>(R.id.fundoMenu).setOnClickListener { finish() }
        findViewById<View>(R.id.btnFechar).setOnClickListener { finish() }

        abrir(R.id.itemCadastro, AlterarCadastroActivity::class.java)
        abrir(R.id.itemCompartilhamento, CompartilhamentoActivity::class.java)
        abrir(R.id.itemSeguranca, SegurancaActivity::class.java)
        abrir(R.id.itemConfiguracoes, ConfiguracoesActivity::class.java)
        abrir(R.id.itemDispositivos, DispositivosActivity::class.java)

        if (!Sessao.podeGerenciar) {
            findViewById<View>(R.id.itemDispositivos).visibility = View.GONE
        }
        findViewById<View>(R.id.itemSair).setOnClickListener {
            confirmar(
                getString(R.string.sair_titulo),
                getString(R.string.sair_texto),
                getString(R.string.menu_sair)
            ) { sair() }
        }
    }

    private fun abrir(idItem: Int, destino: Class<*>) {
        findViewById<View>(idItem).setOnClickListener {
            irPara(destino)
            finish()
        }
    }

    private fun sair() {
        AlarmeScheduler(this).cancelarTudo()
        FirebaseAuth.getInstance().signOut()
        Sessao.limpar()
        prefs.pacienteEscolhido = null
        irParaLimpandoPilha(BoasVindasActivity::class.java)
    }
}
