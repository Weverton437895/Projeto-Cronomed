package br.com.cronomed.dao

import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Remove todos os dados do usuário no Firestore (usado ao excluir a conta). */
class ContaDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    /**
     * Apaga, na ordem: medicamentos, doses, dispositivos, acessos e convites do paciente,
     * os acessos que o usuário tinha como convidado, o paciente e o cadastro do usuário.
     */
    fun excluirTudo(uid: String, aoConcluir: () -> Unit, aoErro: AoErro) {
        val paciente = db.collection("pacientes").document(uid)
        val etapas: List<Query> = listOf(
            paciente.collection("medicamentos"),
            paciente.collection("doses"),
            paciente.collection("dispositivos"),
            db.collection("acessos").whereEqualTo("pacienteId", uid),
            db.collection("convites").whereEqualTo("pacienteId", uid),
            db.collection("acessos").whereEqualTo("uid", uid)
        )
        executar(etapas, 0, {
            paciente.delete()
                .addOnSuccessListener {
                    db.collection("usuarios").document(uid).delete()
                        .addOnSuccessListener { aoConcluir() }
                        .addOnFailureListener { aoErro(it) }
                }
                .addOnFailureListener { aoErro(it) }
        }, aoErro)
    }

    private fun executar(etapas: List<Query>, indice: Int, aoConcluir: () -> Unit, aoErro: AoErro) {
        if (indice >= etapas.size) {
            aoConcluir()
            return
        }
        apagar(etapas[indice], { executar(etapas, indice + 1, aoConcluir, aoErro) }, aoErro)
    }

    /** Apaga os documentos de uma consulta em lotes; repete até a consulta ficar vazia. */
    private fun apagar(consulta: Query, aoConcluir: () -> Unit, aoErro: AoErro) {
        consulta.limit(LOTE.toLong()).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    aoConcluir()
                } else {
                    val batch = db.batch()
                    snap.documents.forEach { batch.delete(it.reference) }
                    batch.commit()
                        .addOnSuccessListener {
                            if (snap.size() < LOTE) aoConcluir() else apagar(consulta, aoConcluir, aoErro)
                        }
                        .addOnFailureListener { aoErro(it) }
                }
            }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val LOTE = 400
    }
}
