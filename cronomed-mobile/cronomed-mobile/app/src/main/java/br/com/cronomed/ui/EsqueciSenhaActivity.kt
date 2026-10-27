package br.com.cronomed.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import br.com.cronomed.R
import br.com.cronomed.util.Validadores
import com.google.firebase.auth.FirebaseAuth

/** Envia o e-mail de redefinição de senha do Firebase. O reenvio só é liberado após 60 segundos. */
class EsqueciSenhaActivity : BaseActivity() {

    private var contador: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esqueci_senha)
        configurarVoltar(getString(R.string.esqueci_senha_titulo))

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnEnviar = findViewById<Button>(R.id.btnEnviar)
        val tvConfirmacao = findViewById<TextView>(R.id.tvConfirmacao)
        val tvContagem = findViewById<TextView>(R.id.tvContagem)

        btnEnviar.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (!Validadores.emailValido(email)) {
                etEmail.error = getString(R.string.erro_email_invalido)
                return@setOnClickListener
            }
            carregando(true)
            FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(this) { tarefa ->
                carregando(false)
                // Mesmo quando o e-mail não existe, mostramos a mesma mensagem (não revela quem tem conta)
                val naoEncontrado = tarefa.exception is com.google.firebase.auth.FirebaseAuthInvalidUserException
                if (tarefa.isSuccessful || naoEncontrado) {
                    tvConfirmacao.visibility = View.VISIBLE
                    btnEnviar.isEnabled = false
                    iniciarContagem(btnEnviar, tvContagem)
                } else {
                    mostrarErro(tarefa.exception ?: Exception())
                }
            }
        }
    }

    private fun iniciarContagem(botao: Button, texto: TextView) {
        texto.visibility = View.VISIBLE
        contador?.cancel()
        contador = object : CountDownTimer(60_000, 1000) {
            override fun onTick(restante: Long) {
                texto.text = getString(R.string.reenviar_em, (restante / 1000).toInt())
            }

            override fun onFinish() {
                texto.visibility = View.GONE
                botao.isEnabled = true
                botao.setText(R.string.reenviar_link)
            }
        }.start()
    }

    override fun onDestroy() {
        contador?.cancel()
        super.onDestroy()
    }
}
