package br.com.cronomed.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.alarme.AlarmeScheduler
import br.com.cronomed.dao.ContaDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.util.Autenticacao
import br.com.cronomed.util.Biometria
import br.com.cronomed.util.Validadores
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth

/** Troca de senha, bloqueio por biometria e exclusão da conta (LGPD). */
class SegurancaActivity : BaseActivity() {

    private lateinit var etAtual: EditText
    private lateinit var etNova: EditText
    private lateinit var etConfirmar: EditText
    private lateinit var tvRequisitos: TextView
    private lateinit var swBiometria: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seguranca)
        if (!sessaoValida()) return
        configurarVoltar(getString(R.string.menu_seguranca))

        etAtual = findViewById(R.id.etSenhaAtual)
        etNova = findViewById(R.id.etNovaSenha)
        etConfirmar = findViewById(R.id.etConfirmarSenha)
        tvRequisitos = findViewById(R.id.tvRequisitos)
        swBiometria = findViewById(R.id.swBiometria)

        mostrarRequisitos("")
        etNova.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                mostrarRequisitos(s?.toString() ?: "")
            }
        })
        findViewById<Button>(R.id.btnAlterarSenha).setOnClickListener { alterarSenha() }
        findViewById<Button>(R.id.btnExcluirConta).setOnClickListener { excluirConta() }

        configurarBiometria()
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

    private fun alterarSenha() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val atual = etAtual.text.toString()
        val nova = etNova.text.toString()
        if (atual.isEmpty()) {
            etAtual.error = getString(R.string.erro_senha_obrigatoria)
            return
        }
        if (!Validadores.senhaForte(nova)) {
            etNova.error = getString(R.string.erro_senha_fraca)
            return
        }
        if (nova != etConfirmar.text.toString()) {
            etConfirmar.error = getString(R.string.erro_senhas_diferentes)
            return
        }
        carregando(true)
        Autenticacao.reautenticar(user, atual, {
            user.updatePassword(nova)
                .addOnSuccessListener {
                    carregando(false)
                    etAtual.text.clear()
                    etNova.text.clear()
                    etConfirmar.text.clear()
                    mensagem(R.string.senha_alterada)
                }
                .addOnFailureListener { mostrarErro(it as Exception) }
        }, { mostrarErro(it) })
    }

    private fun configurarBiometria() {
        val ouvinte = CompoundButton.OnCheckedChangeListener { botao, marcado ->
            if (marcado) {
                // Só liga depois de a pessoa provar que consegue usar a biometria
                Biometria.autenticar(
                    this,
                    getString(R.string.biometria_titulo),
                    getString(R.string.cancelar),
                    { prefs.biometria = true },
                    { desmarcar(botao) }
                )
            } else {
                prefs.biometria = false
            }
        }
        if (!Biometria.disponivel(this)) {
            swBiometria.isEnabled = false
            swBiometria.text = getString(R.string.biometria_indisponivel)
            prefs.biometria = false
        } else {
            swBiometria.isChecked = prefs.biometria
        }
        swBiometria.setOnCheckedChangeListener(ouvinte)
    }

    private fun desmarcar(botao: CompoundButton) {
        botao.setOnCheckedChangeListener(null)
        botao.isChecked = false
        prefs.biometria = false
        botao.setOnCheckedChangeListener { b, marcado ->
            if (marcado) {
                Biometria.autenticar(
                    this,
                    getString(R.string.biometria_titulo),
                    getString(R.string.cancelar),
                    { prefs.biometria = true },
                    { desmarcar(b) }
                )
            } else {
                prefs.biometria = false
            }
        }
    }

    /** Apaga os dados no Firestore e depois a conta no Firebase Auth. Não tem volta. */
    private fun excluirConta() {
        confirmar(
            getString(R.string.excluir_conta),
            getString(R.string.excluir_conta_confirmacao),
            getString(R.string.excluir)
        ) {
            pedirSenha(getString(R.string.confirmar_senha_titulo)) { senha ->
                val user = FirebaseAuth.getInstance().currentUser ?: return@pedirSenha
                carregando(true)
                Autenticacao.reautenticar(user, senha, {
                    ContaDao().excluirTudo(user.uid, {
                        user.delete()
                            .addOnSuccessListener {
                                AlarmeScheduler(this).cancelarTudo()
                                Sessao.limpar()
                                prefs.pacienteEscolhido = null
                                prefs.biometria = false
                                mensagem(R.string.conta_excluida)
                                irParaLimpandoPilha(BoasVindasActivity::class.java)
                            }
                            .addOnFailureListener { mostrarErro(it as Exception) }
                    }, { mostrarErro(it) })
                }, { mostrarErro(it) })
            }
        }
    }
}
