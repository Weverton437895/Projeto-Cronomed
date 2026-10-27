package br.com.cronomed.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import br.com.cronomed.R
import br.com.cronomed.dao.PacienteDao
import br.com.cronomed.dao.UsuarioDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.util.Autenticacao
import br.com.cronomed.util.Formatadores
import br.com.cronomed.util.MascaraTextWatcher
import br.com.cronomed.util.Validadores
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

/** Edita os dados pessoais. O CPF é fixo; trocar o e-mail exige confirmar a senha e o novo endereço. */
class AlterarCadastroActivity : BaseActivity() {

    private lateinit var etNome: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefone: EditText
    private lateinit var etTelefone2: EditText
    private lateinit var etWhatsapp: EditText
    private lateinit var etEmailOpcional: EditText
    private lateinit var etNascimento: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alterar_cadastro)
        if (!sessaoValida()) return
        configurarVoltar(getString(R.string.menu_cadastro))

        etNome = findViewById(R.id.etNome)
        etEmail = findViewById(R.id.etEmail)
        etTelefone = findViewById(R.id.etTelefone)
        etTelefone2 = findViewById(R.id.etTelefone2)
        etWhatsapp = findViewById(R.id.etWhatsapp)
        etEmailOpcional = findViewById(R.id.etEmailOpcional)
        etNascimento = findViewById(R.id.etNascimento)

        MascaraTextWatcher.aplicar(etTelefone, MascaraTextWatcher.TELEFONE)
        MascaraTextWatcher.aplicar(etTelefone2, MascaraTextWatcher.TELEFONE)
        MascaraTextWatcher.aplicar(etWhatsapp, MascaraTextWatcher.TELEFONE)
        MascaraTextWatcher.aplicar(etNascimento, MascaraTextWatcher.DATA)

        val u = Sessao.usuario
        if (u != null) {
            etNome.setText(u.nome)
            val cpf = findViewById<EditText>(R.id.etCpf)
            MascaraTextWatcher.aplicar(cpf, MascaraTextWatcher.CPF)
            cpf.setText(u.cpf)
            etEmail.setText(u.email)
            etTelefone.setText(u.telefone)
            etTelefone2.setText(u.telefone2)
            etWhatsapp.setText(u.whatsapp)
            etEmailOpcional.setText(u.emailOpcional)
            if (u.nascimento.isNotEmpty()) etNascimento.setText(Formatadores.dataDoBanco(u.nascimento))
        }
        findViewById<Button>(R.id.btnSalvar).setOnClickListener { salvar() }
    }

    private fun salvar() {
        val atual = Sessao.usuario ?: return
        val nome = etNome.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val opcional = etEmailOpcional.text.toString().trim()
        val nascimento = etNascimento.text.toString().trim()

        if (nome.length < 3) {
            etNome.error = getString(R.string.erro_nome_obrigatorio)
            return
        }
        if (!Validadores.emailValido(email)) {
            etEmail.error = getString(R.string.erro_email_invalido)
            return
        }
        if (opcional.isNotEmpty() && !Validadores.emailValido(opcional)) {
            etEmailOpcional.error = getString(R.string.erro_email_invalido)
            return
        }
        if (nascimento.isNotEmpty() && !Validadores.dataValida(nascimento)) {
            etNascimento.error = getString(R.string.erro_data_invalida)
            return
        }

        val campos = hashMapOf<String, Any>(
            "nome" to nome,
            "telefone" to etTelefone.text.toString().trim(),
            "telefone2" to etTelefone2.text.toString().trim(),
            "whatsapp" to etWhatsapp.text.toString().trim(),
            "emailOpcional" to opcional,
            "nascimento" to if (nascimento.isEmpty()) "" else Formatadores.dataParaBanco(nascimento)
        )
        val trocouEmail = !email.equals(atual.email, ignoreCase = true)
        if (trocouEmail) {
            pedirSenha(getString(R.string.confirmar_senha_titulo)) { senha ->
                carregando(true)
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    carregando(false)
                    return@pedirSenha
                }
                Autenticacao.reautenticar(user, senha, {
                    user.verifyBeforeUpdateEmail(email)
                        .addOnSuccessListener { gravar(atual.uid, campos, true) }
                        .addOnFailureListener { mostrarErro(it as Exception) }
                }, { mostrarErro(it) })
            }
        } else {
            gravar(atual.uid, campos, false)
        }
    }

    private fun gravar(uid: String, campos: Map<String, Any>, emailPendente: Boolean) {
        carregando(true)
        UsuarioDao().atualizar(uid, campos, {
            val nome = campos["nome"] as String
            FirebaseAuth.getInstance().currentUser
                ?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nome).build())
            val atual = Sessao.usuario
            if (atual != null) {
                Sessao.usuario = atual.copy(
                    nome = nome,
                    telefone = campos["telefone"] as String,
                    telefone2 = campos["telefone2"] as String,
                    whatsapp = campos["whatsapp"] as String,
                    emailOpcional = campos["emailOpcional"] as String,
                    nascimento = campos["nascimento"] as String
                )
            }
            val concluir = {
                carregando(false)
                mensagem(if (emailPendente) R.string.cadastro_salvo_email else R.string.cadastro_salvo)
                finish()
            }
            if (atual != null && atual.tipo == "TITULAR") {
                PacienteDao().atualizarNome(uid, nome, { concluir() }, { mostrarErro(it) })
            } else {
                concluir()
            }
        }, { mostrarErro(it) })
    }
}
