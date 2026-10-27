package br.com.cronomed.dao

import br.com.cronomed.model.Acesso
import br.com.cronomed.model.Nivel
import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.FirebaseFirestore

/** Quem pode acompanhar o tratamento de um paciente (compartilhamento e permissões). */
class AcessoDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun listarPorPaciente(pacienteId: String, aoListar: (List<Acesso>) -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).whereEqualTo("pacienteId", pacienteId).get()
            .addOnSuccessListener { snap ->
                aoListar(snap.documents.map { Acesso.deDocumento(it) }.sortedBy { it.criadoEm })
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun listarPorUsuario(uid: String, aoListar: (List<Acesso>) -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).whereEqualTo("uid", uid).get()
            .addOnSuccessListener { snap ->
                aoListar(snap.documents.map { Acesso.deDocumento(it) })
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun salvar(acesso: Acesso, aoSalvar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(acesso.id).set(acesso.paraMapa())
            .addOnSuccessListener { aoSalvar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun alterarNivel(id: String, nivel: Nivel, aoAlterar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(id).update("nivel", nivel.name)
            .addOnSuccessListener { aoAlterar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun alterarStatus(id: String, status: String, aoAlterar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(id).update("status", status)
            .addOnSuccessListener { aoAlterar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun remover(id: String, aoRemover: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(id).delete()
            .addOnSuccessListener { aoRemover() }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val COLECAO = "acessos"
    }
}
