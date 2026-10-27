package br.com.cronomed.dao

import br.com.cronomed.model.Medicamento
import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Medicamentos de um paciente. Cada compartimento da caixa (1 a 6) guarda no máximo um medicamento,
 * por isso o número do compartimento é o próprio identificador do documento.
 */
class MedicamentoDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun colecao(pacienteId: String): CollectionReference =
        db.collection(PACIENTES).document(pacienteId).collection(COLECAO)

    fun listar(pacienteId: String, aoListar: (List<Medicamento>) -> Unit, aoErro: AoErro) {
        colecao(pacienteId).get()
            .addOnSuccessListener { snap ->
                aoListar(
                    snap.documents.map { Medicamento.deDocumento(it) }
                        .filter { it.compartimento in 1..6 }
                        .sortedBy { it.compartimento }
                )
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun salvar(pacienteId: String, medicamento: Medicamento, aoSalvar: () -> Unit, aoErro: AoErro) {
        colecao(pacienteId).document(medicamento.compartimento.toString()).set(medicamento.paraMapa())
            .addOnSuccessListener { aoSalvar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun excluir(pacienteId: String, compartimento: Int, aoExcluir: () -> Unit, aoErro: AoErro) {
        colecao(pacienteId).document(compartimento.toString()).delete()
            .addOnSuccessListener { aoExcluir() }
            .addOnFailureListener { aoErro(it) }
    }

    /** Soma a quantidade comprada ao estoque atual. */
    fun reabastecer(pacienteId: String, compartimento: Int, quantidade: Int, aoReabastecer: () -> Unit, aoErro: AoErro) {
        val q = quantidade.toLong()
        colecao(pacienteId).document(compartimento.toString())
            .update("estoque", FieldValue.increment(q), "estoqueInicial", FieldValue.increment(q))
            .addOnSuccessListener { aoReabastecer() }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val PACIENTES = "pacientes"
        private const val COLECAO = "medicamentos"
    }
}
