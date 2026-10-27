package br.com.cronomed.dao

import br.com.cronomed.model.Usuario
import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.FirebaseFirestore

/**
 * DAO (Data Access Object): concentra o acesso aos dados do usuário.
 * As telas só chamam estes métodos e não precisam saber que os dados estão no Firestore.
 */
class UsuarioDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun salvar(usuario: Usuario, aoSalvar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(usuario.uid).set(usuario.paraMapa())
            .addOnSuccessListener { aoSalvar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun buscar(uid: String, aoBuscar: (Usuario?) -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(uid).get()
            .addOnSuccessListener { doc ->
                aoBuscar(if (doc.exists()) Usuario.deDocumento(doc) else null)
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun atualizar(uid: String, campos: Map<String, Any>, aoAtualizar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(uid).update(campos)
            .addOnSuccessListener { aoAtualizar() }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val COLECAO = "usuarios"
    }
}
