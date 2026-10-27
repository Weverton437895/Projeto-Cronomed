package br.com.cronomed.dao

import br.com.cronomed.model.Convite
import br.com.cronomed.model.StatusConvite
import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/** Convites enviados pelo titular para familiares, cuidadores e profissionais de saúde. */
class ConviteDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun criar(convite: Convite, aoCriar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(convite.codigo).set(convite.paraMapa())
            .addOnSuccessListener { aoCriar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun buscar(codigo: String, aoBuscar: (Convite?) -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(codigo).get()
            .addOnSuccessListener { doc ->
                aoBuscar(if (doc.exists()) Convite.deDocumento(doc) else null)
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun listarPendentes(pacienteId: String, aoListar: (List<Convite>) -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).whereEqualTo("pacienteId", pacienteId).get()
            .addOnSuccessListener { snap ->
                aoListar(
                    snap.documents.map { Convite.deDocumento(it) }
                        .filter { it.status == StatusConvite.PENDENTE && it.expiraEm > System.currentTimeMillis() }
                        .sortedByDescending { it.criadoEm }
                )
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun marcarAceito(codigo: String, uid: String, aoMarcar: () -> Unit, aoErro: AoErro) {
        val campos = hashMapOf<String, Any>("status" to StatusConvite.ACEITO, "aceitoPorUid" to uid)
        db.collection(COLECAO).document(codigo).update(campos)
            .addOnSuccessListener { aoMarcar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun cancelar(codigo: String, aoCancelar: () -> Unit, aoErro: AoErro) {
        db.collection(COLECAO).document(codigo).delete()
            .addOnSuccessListener { aoCancelar() }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val COLECAO = "convites"

        /** Código aleatório e difícil de adivinhar: ele é o "segredo" do link do convite. */
        fun novoCodigo(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
