package br.com.cronomed.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import br.com.cronomed.R
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.data.Sessao
import br.com.cronomed.data.SessaoCarregador
import br.com.cronomed.util.Validadores
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        configurarVoltar(getString(R.string.entrar))
        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etSenha = findViewById<EditText>(R.id.etSenha)

        findViewById<Button>(R.id.btnEntrar).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val senha = etSenha.text.toString()
            if (!Validadores.emailValido(email)) {
                etEmail.error = getString(R.string.erro_email_invalido)
                return@setOnClickListener
            }
            if (senha.isEmpty()) {
                etSenha.error = getString(R.string.erro_senha_obrigatoria)
                return@setOnClickListener
            }
            entrar(email, senha)
        }
        findViewById<Button>(R.id.btnEsqueciSenha).setOnClickListener {
            irPara(EsqueciSenhaActivity::class.java)
        }
        findViewById<Button>(R.id.btnCriarConta).setOnClickListener {
            irPara(CadastroActivity::class.java)
            finish()
        }
    }

    private fun entrar(email: String, senha: String) {
        carregando(true)
        auth.signInWithEmailAndPassword(email, senha).addOnCompleteListener(this) { tarefa ->
            if (!tarefa.isSuccessful) {
                mostrarErro(tarefa.exception ?: Exception())
                return@addOnCompleteListener
            }
            Log.i("CronoMed", "Login realizado com sucesso")
            SessaoCarregador.carregar(this, { temAcesso ->
                carregando(false)
                if (temAcesso) {
                    irParaLimpandoPilha(DosesActivity::class.java)
                } else {
                    AlarmeScheduler(this).cancelarTudo()
                    auth.signOut()
                    Sessao.limpar()
                    mensagem(R.string.erro_acesso_pausado)
                }
            }, { mostrarErro(it) })
        }
    }
}
