package br.com.cronomed.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import br.com.cronomed.R
import br.com.cronomed.data.Preferencias
import br.com.cronomed.data.Sessao
import br.com.cronomed.util.Erros
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Base de todas as telas: aplica o tema (normal ou alto contraste), o tamanho da fonte escolhido
 * nas configurações e reúne funções que várias telas usam (mensagens, diálogos, navegação).
 */
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var prefs: Preferencias

    /** Telas azuis (boas-vindas e lembrete) sobrescrevem este tema. */
    protected open fun temaDaTela(): Int = R.style.Theme_CronoMed

    /** Telas azuis ignoram o alto contraste, que já é de alto contraste por natureza. */
    protected open fun aceitaAltoContraste(): Boolean = true

    override fun attachBaseContext(base: Context) {
        val configuracao = Configuration(base.resources.configuration)
        configuracao.fontScale = Preferencias(base).escala()
        super.attachBaseContext(base.createConfigurationContext(configuracao))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Preferencias(this)
        if (aceitaAltoContraste() && prefs.altoContraste) {
            setTheme(R.style.Theme_CronoMed_AltoContraste)
        } else {
            setTheme(temaDaTela())
        }
        super.onCreate(savedInstanceState)
    }

    /**
     * Se o Android recriou o app depois de fechá-lo, a sessão em memória foi perdida.
     * Nesse caso voltamos para a tela inicial, que recarrega tudo.
     */
    protected fun sessaoValida(): Boolean {
        if (Sessao.paciente != null) return true
        val intent = Intent(this, BoasVindasActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        return false
    }

    // ---------- Cabeçalhos ----------

    /** Cabeçalho das telas principais (layout view_topo): título e botão do menu lateral. */
    protected fun configurarTopo(titulo: String) {
        findViewById<TextView>(R.id.tvTopoTitulo).text = titulo
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }
    }

    /** Cabeçalho das telas internas (layout view_topo_voltar): botão flutuante que chama finish(). */
    protected fun configurarVoltar(titulo: String) {
        findViewById<TextView>(R.id.tvTopoTitulo).text = titulo
        findViewById<FloatingActionButton>(R.id.btnVoltar).setOnClickListener { finish() }
    }

    /** Liga a barra de navegação inferior. Cada aba é uma tela; trocar de aba usa Intent + finish(). */
    protected fun configurarNavegacao(selecionado: Int) {
        val navegacao = findViewById<BottomNavigationView>(R.id.navegacao)
        navegacao.selectedItemId = selecionado
        navegacao.setOnItemSelectedListener { item ->
            if (item.itemId != selecionado) {
                val destino: Class<*>? = when (item.itemId) {
                    R.id.nav_doses -> DosesActivity::class.java
                    R.id.nav_medicamentos -> MedicamentosActivity::class.java
                    R.id.nav_estoque -> EstoqueActivity::class.java
                    R.id.nav_historico -> HistoricoActivity::class.java
                    else -> null
                }
                if (destino != null) {
                    startActivity(Intent(this, destino))
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
            true
        }
        if (selecionado != R.id.nav_doses) {
            // Voltar em qualquer aba leva para a tela de doses, em vez de sair do app
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    startActivity(Intent(this@BaseActivity, DosesActivity::class.java))
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
        }
    }

    // ---------- Feedback ----------

    protected fun carregando(ativo: Boolean) {
        findViewById<View>(R.id.carregando)?.visibility = if (ativo) View.VISIBLE else View.GONE
    }

    protected fun mensagem(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    protected fun mensagem(recurso: Int) {
        Toast.makeText(this, recurso, Toast.LENGTH_LONG).show()
    }

    protected fun mostrarErro(erro: Exception) {
        carregando(false)
        Toast.makeText(this, Erros.mensagem(erro), Toast.LENGTH_LONG).show()
    }

    protected fun confirmar(titulo: String, texto: String, rotuloOk: String, aoConfirmar: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(texto)
            .setPositiveButton(rotuloOk) { _, _ -> aoConfirmar() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /** Pede a senha atual numa janela (usado antes de trocar senha, e-mail ou excluir a conta). */
    protected fun pedirSenha(titulo: String, aoInformar: (String) -> Unit) {
        val campo = EditText(this)
        campo.hint = getString(R.string.senha_atual)
        campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val margem = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        container.setPadding(margem, margem / 2, margem, 0)
        container.addView(campo)
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setView(container)
            .setPositiveButton(R.string.confirmar) { _, _ ->
                val senha = campo.text.toString()
                if (senha.isEmpty()) mensagem(R.string.erro_senha_obrigatoria) else aoInformar(senha)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    protected fun irPara(destino: Class<*>) {
        startActivity(Intent(this, destino))
    }

    /** Abre uma tela nova e limpa todas as anteriores (login, logout). */
    protected fun irParaLimpandoPilha(destino: Class<*>) {
        val intent = Intent(this, destino)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
