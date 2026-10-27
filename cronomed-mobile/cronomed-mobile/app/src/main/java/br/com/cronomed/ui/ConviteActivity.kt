package br.com.cronomed.ui

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.dao.AcessoDao
import br.com.cronomed.dao.ConviteDao
import br.com.cronomed.dao.UsuarioDao
import br.com.cronomed.data.SessaoCarregador
import br.com.cronomed.model.Acesso
import br.com.cronomed.model.Convite
import br.com.cronomed.model.Nivel
import br.com.cronomed.model.Papel
import br.com.cronomed.model.StatusAcesso
import br.com.cronomed.model.StatusConvite
import br.com.cronomed.model.Usuario
import br.com.cronomed.util.Formatadores
import br.com.cronomed.util.Validadores
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

/**
 * Cadastro de convidado, aberto pelo link cronomed://convite/<código> ou pelo código digitado.
 * Passo 1: mostra quem convidou. Passo 2: cadastro. Passo 3: cadastro concluído.
 */
class ConviteActivity : BaseActivity() {

    private lateinit var painelConvite: View
    private lateinit var painelCadastro: View
    private lateinit var painelConcluido: View
    private lateinit var grupoCredenciais: View
    private lateinit var grupoProfissional: View
    private lateinit var spPapel: Spinner
    private lateinit var etRegistro: EditText
    private lateinit var etEspecialidade: EditText
    private lateinit var etNome: EditText
    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var etConfirmar: EditText
    private lateinit var tvRequisitos: TextView
    private lateinit var cbTermos: CheckBox

    private val conviteDao = ConviteDao()
    private var convite: Convite? = null
    private val papeis = Papel.values().toList()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convite)
        configurarVoltar(getString(R.string.convite_titulo))

        painelConvite = findViewById(R.id.painelConvite)
        painelCadastro = findViewById(R.id.painelCadastro)
        painelConcluido = findViewById(R.id.painelConcluido)
        grupoCredenciais = findViewById(R.id.grupoCredenciais)
        grupoProfissional = findViewById(R.id.grupoProfissional)
        spPapel = findViewById(R.id.spPapel)
        etRegistro = findViewById(R.id.etRegistro)
        etEspecialidade = findViewById(R.id.etEspecialidade)
        etNome = findViewById(R.id.etNome)
        etEmail = findViewById(R.id.etEmail)
        etSenha = findViewById(R.id.etSenha)
        etConfirmar = findViewById(R.id.etConfirmarSenha)
        tvRequisitos = findViewById(R.id.tvRequisitos)
        cbTermos = findViewById(R.id.cbTermos)

        spPapel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, papeis.map { it.rotulo })
        spPapel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(pai: android.widget.AdapterView<*>?, v: View?, posicao: Int, id: Long) {
                val p = papeis[posicao]
                grupoProfissional.visibility =
                    if (p == Papel.MEDICO || p == Papel.ENFERMEIRO) View.VISIBLE else View.GONE
                etEspecialidade.visibility = if (p == Papel.MEDICO) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(pai: android.widget.AdapterView<*>?) {}
        }
        mostrarRequisitos("")
        etSenha.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                mostrarRequisitos(s?.toString() ?: "")
            }
        })

        findViewById<Button>(R.id.btnContinuar).setOnClickListener { mostrarPasso(2) }
        findViewById<Button>(R.id.btnAceitar).setOnClickListener { aceitar() }
        findViewById<Button>(R.id.btnIrApp).setOnClickListener {
            irParaLimpandoPilha(DosesActivity::class.java)
        }

        val codigo = codigoDoIntent()
        if (codigo.isEmpty()) {
            mensagem(R.string.erro_convite_invalido)
            finish()
            return
        }
        buscarConvite(codigo)
    }

    /** O código vem do link (cronomed://convite/abc123) ou do extra, quando foi digitado. */
    private fun codigoDoIntent(): String {
        val dados: Uri? = intent.data
        if (dados != null) {
            val ultimo = dados.lastPathSegment
            if (!ultimo.isNullOrBlank()) return ultimo
        }
        return intent.getStringExtra(EXTRA_CODIGO)?.trim() ?: ""
    }

    private fun buscarConvite(codigo: String) {
        carregando(true)
        conviteDao.buscar(codigo, { encontrado ->
            carregando(false)
            when {
                encontrado == null -> falhar(R.string.erro_convite_invalido)
                encontrado.status != StatusConvite.PENDENTE -> falhar(R.string.erro_convite_usado)
                encontrado.expiraEm < System.currentTimeMillis() -> falhar(R.string.erro_convite_expirado)
                else -> {
                    convite = encontrado
                    preencherConvite(encontrado)
                }
            }
        }, { mostrarErro(it) })
    }

    private fun falhar(recurso: Int) {
        mensagem(recurso)
        finish()
    }

    private fun preencherConvite(c: Convite) {
        findViewById<TextView>(R.id.tvConviteTitulo).text = getString(R.string.convite_chamada, c.titularNome)
        val nivel = Nivel.deTexto(c.nivel)
        findViewById<TextView>(R.id.tvConviteDetalhe).text = getString(
            R.string.convite_detalhe_guest,
            nivel.rotulo,
            Formatadores.diaMes(c.expiraEm)
        )
        etNome.setText(c.nome)
        etEmail.setText(c.email)
        // Quem já está logado só aceita; não precisa criar outra conta
        if (auth.currentUser != null) {
            grupoCredenciais.visibility = View.GONE
        }
        mostrarPasso(1)
    }

    private fun mostrarPasso(passo: Int) {
        painelConvite.visibility = if (passo == 1) View.VISIBLE else View.GONE
        painelCadastro.visibility = if (passo == 2) View.VISIBLE else View.GONE
        painelConcluido.visibility = if (passo == 3) View.VISIBLE else View.GONE
    }

    private fun mostrarRequisitos(senha: String) {
        val marcas = Validadores.checarSenha(senha)
        val sb = StringBuilder(getString(R.string.senha_deve_ter)).append('\n')
        Validadores.REQUISITOS_SENHA.forEachIndexed { i, texto ->
            sb.append(if (marcas[i]) "✓ " else "○ ").append(texto).append('\n')
        }
        tvRequisitos.text = sb.toString().trimEnd()
        tvRequisitos.setTextColor(
            ContextCompat.getColor(this, if (marcas.all { it }) R.color.sucesso else R.color.texto2)
        )
    }

    private fun aceitar() {
        val c = convite ?: return
        val papel = papeis[spPapel.selectedItemPosition.coerceAtLeast(0)]
        val registro = etRegistro.text.toString().trim()
        val especialidade = etEspecialidade.text.toString().trim()
        if ((papel == Papel.MEDICO || papel == Papel.ENFERMEIRO) && registro.isEmpty()) {
            etRegistro.error = getString(R.string.erro_registro)
            return
        }
        if (papel == Papel.MEDICO && especialidade.isEmpty()) {
            etEspecialidade.error = getString(R.string.erro_especialidade)
            return
        }
        if (!cbTermos.isChecked) {
            mensagem(R.string.erro_aceitar_termos)
            return
        }

        val logado = auth.currentUser
        if (logado != null) {
            if (logado.uid == c.pacienteId) {
                mensagem(R.string.erro_convite_proprio)
                return
            }
            val nome = logado.displayName ?: (logado.email ?: "").substringBefore("@")
            criarAcesso(c, logado.uid, nome, logado.email ?: "", papel, registro, especialidade)
            return
        }

        val nome = etNome.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val senha = etSenha.text.toString()
        if (nome.length < 3) {
            etNome.error = getString(R.string.erro_nome_obrigatorio)
            return
        }
        if (!Validadores.emailValido(email)) {
            etEmail.error = getString(R.string.erro_email_invalido)
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

        carregando(true)
        auth.createUserWithEmailAndPassword(email, senha).addOnCompleteListener(this) { tarefa ->
            val novo = auth.currentUser
            if (!tarefa.isSuccessful || novo == null) {
                mostrarErro(tarefa.exception ?: Exception())
                return@addOnCompleteListener
            }
            novo.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nome).build())
            val usuario = Usuario(uid = novo.uid, nome = nome, email = email, tipo = "CONVIDADO")
            UsuarioDao().salvar(usuario, {
                criarAcesso(c, novo.uid, nome, email, papel, registro, especialidade)
            }, { mostrarErro(it) })
        }
    }

    private fun criarAcesso(
        c: Convite, uid: String, nome: String, email: String,
        papel: Papel, registro: String, especialidade: String
    ) {
        carregando(true)
        val acesso = Acesso(
            id = Acesso.idDe(c.pacienteId, uid),
            pacienteId = c.pacienteId,
            uid = uid,
            nome = nome,
            email = email,
            papel = papel,
            nivel = Nivel.deTexto(c.nivel),
            status = StatusAcesso.ATIVO,
            registro = registro,
            especialidade = especialidade,
            codigoConvite = c.codigo,
            criadoEm = System.currentTimeMillis()
        )
        AcessoDao().salvar(acesso, {
            conviteDao.marcarAceito(c.codigo, uid, {
                SessaoCarregador.carregar(this, {
                    carregando(false)
                    findViewById<TextView>(R.id.tvConcluidoDetalhe).text =
                        getString(R.string.concluido_detalhe, c.titularNome)
                    mostrarPasso(3)
                }, { mostrarErro(it) })
            }, { mostrarErro(it) })
        }, { mostrarErro(it) })
    }

    companion object {
        const val EXTRA_CODIGO = "codigo"
    }
}
