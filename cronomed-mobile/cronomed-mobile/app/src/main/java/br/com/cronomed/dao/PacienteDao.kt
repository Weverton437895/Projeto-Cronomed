package br.com.cronomed.dao

import br.com.cronomed.model.Contexto
import br.com.cronomed.model.Nivel
import br.com.cronomed.model.Paciente
import br.com.cronomed.model.StatusAcesso
import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.FirebaseFirestore

class PacienteDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun criar(paciente: Paciente, aoCriar: () -> Unit, aoErro: AoErro) {
        val dados = hashMapOf<String, Any>(
            "nome" to paciente.nome,
            "titularUid" to paciente.titularUid
        )
        db.collection(COLECAO).document(paciente.id).set(dados)
            .addOnSuccessListener { aoCriar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun atualizarNome(pacienteId: String, nome: String, aoAtualizar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(pacienteId).update("nome", nome)
            .addOnSuccessListener { aoAtualizar() }
            .addOnFailureListener { aoErro(it) }
    }

    /**
     * Descobre quais pacientes o usuário pode acompanhar:
     * o próprio (como titular) e os de quem o convidou (com o nível definido no convite).
     */
    fun carregarContextos(uid: String, aoCarregar: (List<Contexto>) -> Unit, aoErro: AoErro) {
        val lista = mutableListOf<Contexto>()
        db.collection(COLECAO).document(uid).get()
            .addOnSuccessListener { proprio ->
                if (proprio.exists()) lista.add(Contexto(Paciente.deDocumento(proprio), Nivel.TITULAR))
                AcessoDao(db).listarPorUsuario(uid, { acessos ->
                    val ativos = acessos.filter { it.status == StatusAcesso.ATIVO }
                    if (ativos.isEmpty()) {
                        aoCarregar(lista)
                    } else {
                        var restantes = ativos.size
                        ativos.forEach { acesso ->
                            db.collection(COLECAO).document(acesso.pacienteId).get()
                                .addOnSuccessListener { doc ->
                                    if (doc.exists()) lista.add(Contexto(Paciente.deDocumento(doc), acesso.nivel))
                                    restantes--
                                    if (restantes == 0) aoCarregar(lista)
                                }
                                .addOnFailureListener {
                                    restantes--
                                    if (restantes == 0) aoCarregar(lista)
                                }
                        }
                    }
                }, aoErro)
            }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val COLECAO = "pacientes"
    }
}
