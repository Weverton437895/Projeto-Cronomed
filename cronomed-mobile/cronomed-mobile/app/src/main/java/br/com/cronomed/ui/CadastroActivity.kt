package br.com.cronomed.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.dao.PacienteDao
import br.com.cronomed.dao.UsuarioDao
import br.com.cronomed.data.SessaoCarregador
import br.com.cronomed.model.Paciente
import br.com.cronomed.model.Usuario
import br.com.cronomed.util.Formatadores
import br.com.cronomed.util.MascaraTextWatcher
import br.com.cronomed.util.Validadores
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

/** Cadastro do titular (quem toma os remédios): cria a conta no Firebase Auth e os dados no Firestore. */
class CadastroActivity : BaseActivity() {

    private lateinit var etNome: EditText
    private lateinit var etCpf: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefone: EditText
    private lateinit var etNascimento: EditText
    private lateinit var etSenha: EditText
    private lateinit var etConfirmar: EditText
    private lateinit var tvRequisitos: TextView
    private lateinit var cbTermos: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)
        configurarVoltar(getString(R.string.criar_conta))

        etNome = findViewById(R.id.etNome)
        etCpf = findViewById(R.id.etCpf)
        etEmail = findViewById(R.id.etEmail)
        etTelefone = findViewById(R.id.etTelefone)
        etNascimento = findViewById(R.id.etNascimento)
        etSenha = findViewById(R.id.etSenha)
        etConfirmar = findViewById(R.id.etConfirmarSenha)
        tvRequisitos = findViewById(R.id.tvRequisitos)
        cbTermos = findViewById(R.id.cbTermos)

        MascaraTextWatcher.aplicar(etCpf, MascaraTextWatcher.CPF)
        MascaraTextWatcher.aplicar(etTelefone, MascaraTextWatcher.TELEFONE)
        MascaraTextWatcher.aplicar(etNascimento, MascaraTextWatcher.DATA)

        mostrarRequisitos("")
        etSenha.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                mostrarRequisitos(s?.toString() ?: "")
            }
        })

        findViewById<Button>(R.id.btnCadastrar).setOnClickListener { cadastrar() }
    }

    /** Lista os requisitos da senha com ✓ (cumprido) ou ○ (falta). */
    private fun mostrarRequisitos(senha: String) {
        val marcas = Validadores.checarSenha(senha)
        val sb = StringBuilder(getString(R.string.senha_deve_ter)).append('\n')
        Validadores.REQUISITOS_SENHA.forEachIndexed { i, texto ->
            sb.append(if (marcas[i]) "✓ " else "○ ").append(texto).append('\n')
        }
        tvRequisitos.text = sb.toString().trimEnd()
        val todos = marcas.all { it }
        tvRequisitos.setTextColor(
            ContextCompat.getColor(this, if (todos) R.color.sucesso else R.color.texto2)
        )
    }

    private fun cadastrar() {
        val nome = etNome.text.toString().trim()
        val cpf = Validadores.soDigitos(etCpf.text.toString())
        val email = etEmail.text.toString().trim()
        val telefone = etTelefone.text.toString().trim()
        val nascimento = etNascimento.text.toString().trim()
        val senha = etSenha.text.toString()

        if (nome.length < 3) {
            etNome.error = getString(R.string.erro_nome_obrigatorio)
            return
        }
        if (!Validadores.cpfValido(cpf)) {
            etCpf.error = getString(R.string.erro_cpf_invalido)
            return
        }
        if (!Validadores.emailValido(email)) {
            etEmail.error = getString(R.string.erro_email_invalido)
            return
        }
        if (nascimento.isNotEmpty() && !Validadores.dataValida(nascimento)) {
            etNascimento.error = getString(R.string.erro_data_invalida)
            return
        }
        if (!Validadores.senhaForte(senha)) {
            etSenha.error = getString(R.string.erro_senha_fraca)
            return
        }
        if (senha != etConfirmar.text.toString()) {
            etConfirmar.error = getString(R.string.erro_senhas_diferentes)
            return
        }
        if (!cbTermos.isChecked) {
            mensagem(R.string.erro_aceitar_termos)
            return
        }

        carregando(true)
        val auth = FirebaseAuth.getInstance()
        auth.createUserWithEmailAndPassword(email, senha).addOnCompleteListener(this) { tarefa ->
            val firebaseUser = auth.currentUser
            if (!tarefa.isSuccessful || firebaseUser == null) {
                mostrarErro(tarefa.exception ?: Exception())
                return@addOnCompleteListener
            }
            val uid = firebaseUser.uid
            val usuario = Usuario(
                uid = uid,
                nome = nome,
                cpf = cpf,
                email = email,
                telefone = telefone,
                nascimento = if (nascimento.isEmpty()) "" else Formatadores.dataParaBanco(nascimento),
                tipo = "TITULAR"
            )
            firebaseUser.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nome).build())
            UsuarioDao().salvar(usuario, {
                PacienteDao().criar(Paciente(uid, nome, uid), {
                    SessaoCarregador.carregar(this, {
                        carregando(false)
                        mensagem(R.string.cadastro_sucesso)
                        irParaLimpandoPilha(DosesActivity::class.java)
                    }, { mostrarErro(it) })
                }, { mostrarErro(it) })
            }, { mostrarErro(it) })
        }
    }
}
