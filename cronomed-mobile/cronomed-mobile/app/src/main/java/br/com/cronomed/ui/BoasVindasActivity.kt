package br.com.cronomed.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import br.com.cronomed.R
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.data.Sessao
import br.com.cronomed.data.SessaoCarregador
import br.com.cronomed.util.Biometria
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

/**
 * Primeira tela do app. Se já existe um usuário logado no Firebase, carrega os dados dele
 * (pedindo a biometria, se estiver ativada) e vai direto para as doses. Senão, mostra Entrar / Criar conta.
 */
class BoasVindasActivity : BaseActivity() {

    override fun temaDaTela(): Int = R.style.Theme_CronoMed_Azul
    override fun aceitaAltoContraste(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boas_vindas)

        findViewById<Button>(R.id.btnEntrar).setOnClickListener {
            irPara(LoginActivity::class.java)
        }
        findViewById<Button>(R.id.btnCriarConta).setOnClickListener {
            irPara(CadastroActivity::class.java)
        }

        findViewById<Button>(R.id.btnTenhoConvite).setOnClickListener { pedirCodigoConvite() }

        if (FirebaseAuth.getInstance().currentUser != null) {
            if (prefs.biometria && Biometria.disponivel(this)) {
                Biometria.autenticar(
                    this,
                    getString(R.string.biometria_titulo),
                    getString(R.string.cancelar),
                    { entrar() },
                    { /* continua na tela de boas-vindas, sem abrir os dados */ }
                )
            } else {
                entrar()
            }
        }
    }

    /** Quem recebeu o código por mensagem (sem o link) pode digitá-lo aqui. */
    private fun pedirCodigoConvite() {
        val campo = EditText(this)
        campo.hint = getString(R.string.codigo_convite)
        val margem = (20 * resources.displayMetrics.density).toInt()
        val recipiente = FrameLayout(this)
        recipiente.setPadding(margem, margem / 2, margem, 0)
        recipiente.addView(campo)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tenho_convite)
            .setView(recipiente)
            .setPositiveButton(R.string.continuar) { _, _ ->
                val codigo = campo.text.toString().trim()
                if (codigo.isEmpty()) {
                    mensagem(R.string.erro_convite_invalido)
                } else {
                    val intent = Intent(this, ConviteActivity::class.java)
                    intent.putExtra(ConviteActivity.EXTRA_CODIGO, codigo)
                    startActivity(intent)
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun entrar() {
        carregando(true)
        SessaoCarregador.carregar(this, { temAcesso ->
            carregando(false)
            if (temAcesso) {
                irParaLimpandoPilha(DosesActivity::class.java)
            } else {
                // Acesso pausado pelo titular: sai da conta
                AlarmeScheduler(this).cancelarTudo()
                FirebaseAuth.getInstance().signOut()
                Sessao.limpar()
                mensagem(R.string.erro_acesso_pausado)
            }
        }, { mostrarErro(it) })
    }
}
