package br.com.cronomed.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import br.com.cronomed.dao.AcessoDao
import br.com.cronomed.dao.ConviteDao
import br.com.cronomed.data.Sessao
import br.com.cronomed.model.Acesso
import br.com.cronomed.model.Convite
import br.com.cronomed.model.Nivel
import br.com.cronomed.model.StatusAcesso
import br.com.cronomed.model.StatusConvite
import br.com.cronomed.util.Formatadores
import br.com.cronomed.util.MascaraTextWatcher
import br.com.cronomed.util.Validadores
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Compartilhamento: o titular convida familiares, cuidadores e profissionais, define o nível de
 * acesso de cada um e pode pausar ou remover. Quem foi convidado vê apenas o próprio acesso.
 */
class CompartilhamentoActivity : BaseActivity() {

    private lateinit var listaAcessos: LinearLayout
    private lateinit var listaConvites: LinearLayout
    private lateinit var tvVazio: TextView
    private lateinit var tvTituloConvites: TextView
    private lateinit var btnSair: Button
    private lateinit var fab: FloatingActionButton

    private val acessoDao = AcessoDao()
    private val conviteDao = ConviteDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compartilhamento)
        if (!sessaoValida()) return
        configurarVoltar(getString(R.string.menu_compartilhamento))

        listaAcessos = findViewById(R.id.listaAcessos)
        listaConvites = findViewById(R.id.listaConvites)
        tvVazio = findViewById(R.id.tvVazioAcessos)
        tvTituloConvites = findViewById(R.id.tvTituloConvites)
        btnSair = findViewById(R.id.btnSairAcompanhamento)
        fab = findViewById(R.id.fabConvidar)

        if (Sessao.ehTitular) {
            fab.setOnClickListener { abrirDialogoConvite() }
        } else {
            fab.visibility = View.GONE
            tvTituloConvites.visibility = View.GONE
            listaConvites.visibility = View.GONE
            btnSair.visibility = View.VISIBLE
            btnSair.setOnClickListener { sairDoAcompanhamento() }
        }
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        val paciente = Sessao.paciente ?: return
        val uid = Sessao.usuario?.uid ?: return
        carregando(true)
        if (Sessao.ehTitular) {
            acessoDao.listarPorPaciente(paciente.id, { acessos ->
                mostrarAcessos(acessos)
                conviteDao.listarPendentes(paciente.id, { convites ->
                    carregando(false)
                    mostrarConvites(convites)
                }, { mostrarErro(it) })
            }, { mostrarErro(it) })
        } else {
            // Convidados só conseguem ler o próprio acesso (regra do Firestore)
            acessoDao.listarPorUsuario(uid, { acessos ->
                carregando(false)
                mostrarAcessos(acessos.filter { it.pacienteId == paciente.id })
            }, { mostrarErro(it) })
        }
    }

    private fun mostrarAcessos(acessos: List<Acesso>) {
        listaAcessos.removeAllViews()
        tvVazio.visibility = if (acessos.isEmpty()) View.VISIBLE else View.GONE
        val inflador = LayoutInflater.from(this)
        val titular = Sessao.ehTitular
        acessos.forEach { acesso ->
            val visao = inflador.inflate(R.layout.item_acesso, listaAcessos, false)
            visao.findViewById<TextView>(R.id.tvIniciais).text = Formatadores.iniciais(acesso.nome)
            visao.findViewById<TextView>(R.id.tvNome).text = acesso.nome
            var detalhe = acesso.papel.rotulo + " • " + acesso.nivel.rotulo
            if (acesso.registro.isNotBlank()) detalhe += "\n" + acesso.registro
            if (acesso.especialidade.isNotBlank()) detalhe += " • " + acesso.especialidade
            visao.findViewById<TextView>(R.id.tvDetalhe).text = detalhe

            val selo = visao.findViewById<TextView>(R.id.tvSelo)
            if (acesso.status == StatusAcesso.ATIVO) {
                selo.setText(R.string.selo_ativo)
                selo.setBackgroundResource(R.drawable.bg_selo_sucesso)
                selo.setTextColor(ContextCompat.getColor(this, R.color.sucesso))
            } else {
                selo.setText(R.string.selo_pausado)
                selo.setBackgroundResource(R.drawable.bg_selo_alerta)
                selo.setTextColor(ContextCompat.getColor(this, R.color.alerta))
            }

            val acoes = visao.findViewById<View>(R.id.acoes)
            if (!titular) {
                acoes.visibility = View.GONE
            } else {
                val btnPausar = visao.findViewById<Button>(R.id.btnPausar)
                btnPausar.setText(if (acesso.status == StatusAcesso.ATIVO) R.string.pausar else R.string.reativar)
                btnPausar.setOnClickListener { alternarPausa(acesso) }
                visao.findViewById<Button>(R.id.btnNivel).setOnClickListener { escolherNivel(acesso) }
                visao.findViewById<Button>(R.id.btnRemover).setOnClickListener {
                    confirmar(
                        getString(R.string.remover_acesso_titulo),
                        getString(R.string.remover_acesso_texto, acesso.nome),
                        getString(R.string.remover)
                    ) {
                        carregando(true)
                        acessoDao.remover(acesso.id, { carregar() }, { mostrarErro(it) })
                    }
                }
            }
            listaAcessos.addView(visao)
        }
    }

    private fun mostrarConvites(convites: List<Convite>) {
        listaConvites.removeAllViews()
        tvTituloConvites.visibility = if (convites.isEmpty()) View.GONE else View.VISIBLE
        val inflador = LayoutInflater.from(this)
        convites.forEach { convite ->
            val visao = inflador.inflate(R.layout.item_convite, listaConvites, false)
            visao.findViewById<TextView>(R.id.tvNome).text = convite.nome
            visao.findViewById<TextView>(R.id.tvDetalhe).text = getString(
                R.string.convite_detalhe,
                Nivel.deTexto(convite.nivel).rotulo,
                Formatadores.diaMes(convite.expiraEm)
            )
            visao.findViewById<Button>(R.id.btnReenviar).setOnClickListener { compartilhar(convite) }
            visao.findViewById<Button>(R.id.btnCancelar).setOnClickListener {
                carregando(true)
                conviteDao.cancelar(convite.codigo, { carregar() }, { mostrarErro(it) })
            }
            listaConvites.addView(visao)
        }
    }

    private fun alternarPausa(acesso: Acesso) {
        val novo = if (acesso.status == StatusAcesso.ATIVO) StatusAcesso.PAUSADO else StatusAcesso.ATIVO
        carregando(true)
        acessoDao.alterarStatus(acesso.id, novo, { carregar() }, { mostrarErro(it) })
    }

    private fun escolherNivel(acesso: Acesso) {
        val niveis = listOf(Nivel.VISUALIZACAO, Nivel.GERENCIAMENTO)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.alterar_nivel)
            .setSingleChoiceItems(
                niveis.map { it.rotulo }.toTypedArray(),
                niveis.indexOf(acesso.nivel).coerceAtLeast(0)
            ) { dialogo, indice ->
                dialogo.dismiss()
                carregando(true)
                acessoDao.alterarNivel(acesso.id, niveis[indice], { carregar() }, { mostrarErro(it) })
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun sairDoAcompanhamento() {
        val paciente = Sessao.paciente ?: return
        val uid = Sessao.usuario?.uid ?: return
        confirmar(
            getString(R.string.sair_acompanhamento),
            getString(R.string.sair_acompanhamento_texto, paciente.nome),
            getString(R.string.sair)
        ) {
            carregando(true)
            acessoDao.remover(Acesso.idDe(paciente.id, uid), {
                carregando(false)
                // Volta para o início, que recarrega os pacientes disponíveis
                prefs.pacienteEscolhido = null
                irParaLimpandoPilha(BoasVindasActivity::class.java)
            }, { mostrarErro(it) })
        }
    }

    // ---------- Convite ----------

    private fun abrirDialogoConvite() {
        val visao = LayoutInflater.from(this).inflate(R.layout.dialog_convite, null)
        val etNome = visao.findViewById<EditText>(R.id.etNomeConvidado)
        val etEmail = visao.findViewById<EditText>(R.id.etEmailConvidado)
        val etZap = visao.findViewById<EditText>(R.id.etWhatsappConvidado)
        val spNivel = visao.findViewById<Spinner>(R.id.spNivel)
        val tvExplicacao = visao.findViewById<TextView>(R.id.tvNivelExplicacao)
        MascaraTextWatcher.aplicar(etZap, MascaraTextWatcher.TELEFONE)

        val niveis = listOf(Nivel.VISUALIZACAO, Nivel.GERENCIAMENTO)
        spNivel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, niveis.map { it.rotulo })
        tvExplicacao.setText(R.string.nivel_visualizacao_desc)
        spNivel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(pai: android.widget.AdapterView<*>?, v: View?, posicao: Int, id: Long) {
                tvExplicacao.setText(
                    if (niveis[posicao] == Nivel.VISUALIZACAO) R.string.nivel_visualizacao_desc
                    else R.string.nivel_gerenciamento_desc
                )
            }

            override fun onNothingSelected(pai: android.widget.AdapterView<*>?) {}
        }

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.convidar)
            .setView(visao)
            .setPositiveButton(R.string.criar_convite, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialogo.show()
        // Botão sobrescrito para o diálogo não fechar quando houver erro de validação
        dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val nome = etNome.text.toString().trim()
            val email = etEmail.text.toString().trim()
            if (nome.length < 2) {
                etNome.error = getString(R.string.erro_nome_obrigatorio)
                return@setOnClickListener
            }
            if (email.isNotEmpty() && !Validadores.emailValido(email)) {
                etEmail.error = getString(R.string.erro_email_invalido)
                return@setOnClickListener
            }
            dialogo.dismiss()
            criarConvite(nome, email, etZap.text.toString().trim(), niveis[spNivel.selectedItemPosition])
        }
    }

    private fun criarConvite(nome: String, email: String, whatsapp: String, nivel: Nivel) {
        val paciente = Sessao.paciente ?: return
        val agora = System.currentTimeMillis()
        val convite = Convite(
            codigo = ConviteDao.novoCodigo(),
            pacienteId = paciente.id,
            titularNome = Sessao.usuario?.nome ?: paciente.nome,
            nome = nome,
            email = email,
            whatsapp = whatsapp,
            status = StatusConvite.PENDENTE,
            nivel = nivel.name,
            expiraEm = agora + 7L * 24 * 60 * 60 * 1000,
            criadoEm = agora
        )
        carregando(true)
        conviteDao.criar(convite, {
            carregando(false)
            carregar()
            compartilhar(convite)
        }, { mostrarErro(it) })
    }

    /** Abre a folha de compartilhar do Android (WhatsApp, e-mail, SMS...) com o link do convite. */
    private fun compartilhar(convite: Convite) {
        val texto = getString(
            R.string.convite_mensagem,
            convite.nome, convite.titularNome, convite.codigo, "cronomed://convite/" + convite.codigo
        )
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.convite_assunto))
        intent.putExtra(Intent.EXTRA_TEXT, texto)
        startActivity(Intent.createChooser(intent, getString(R.string.enviar_convite)))
    }
}
