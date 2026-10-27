package br.com.cronomed.data

import android.content.Context
import br.com.cronomed.dao.PacienteDao
import br.com.cronomed.dao.UsuarioDao
import br.com.cronomed.model.Contexto
import br.com.cronomed.model.Nivel
import br.com.cronomed.model.Paciente
import br.com.cronomed.model.Usuario
import br.com.cronomed.util.AoErro
import com.google.firebase.auth.FirebaseAuth

/** Carrega do Firestore quem está logado e quais pacientes ele pode acompanhar. */
object SessaoCarregador {

    /** [aoCarregar] recebe false quando o usuário não tem nenhum paciente liberado (acesso pausado). */
    fun carregar(contexto: Context, aoCarregar: (Boolean) -> Unit, aoErro: AoErro) {
        val atual = FirebaseAuth.getInstance().currentUser
        if (atual == null) {
            aoCarregar(false)
            return
        }
        val uid = atual.uid
        val prefs = Preferencias(contexto)
        val usuarioDao = UsuarioDao()
        val pacienteDao = PacienteDao()

        usuarioDao.buscar(uid, { encontrado ->
            val seguir = { usuario: Usuario ->
                Sessao.usuario = usuario
                pacienteDao.carregarContextos(uid, { lista ->
                    if (lista.isEmpty() && usuario.tipo == "TITULAR") {
                        // Titular sem documento de paciente (cadastro interrompido): cria agora
                        val paciente = Paciente(uid, usuario.nome, uid)
                        pacienteDao.criar(paciente, {
                            Sessao.definirContextos(listOf(Contexto(paciente, Nivel.TITULAR)), prefs.pacienteEscolhido)
                            aoCarregar(true)
                        }, aoErro)
                    } else {
                        Sessao.definirContextos(lista, prefs.pacienteEscolhido)
                        aoCarregar(lista.isNotEmpty())
                    }
                }, aoErro)
            }
            if (encontrado != null) {
                seguir(encontrado)
            } else {
                val nome = atual.displayName ?: (atual.email ?: "Usuário").substringBefore("@")
                val novo = Usuario(uid = uid, nome = nome, email = atual.email ?: "")
                usuarioDao.salvar(novo, { seguir(novo) }, aoErro)
            }
        }, aoErro)
    }
}
