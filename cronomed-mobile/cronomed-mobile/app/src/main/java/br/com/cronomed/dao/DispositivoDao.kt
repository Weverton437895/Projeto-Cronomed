package br.com.cronomed.dao

import br.com.cronomed.model.Dispositivo
import br.com.cronomed.util.AoErro
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

/** Caixas CronoMed vinculadas ao paciente. */
class DispositivoDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun colecao(pacienteId: String): CollectionReference =
        db.collection(PACIENTES).document(pacienteId).collection(COLECAO)

    fun listar(pacienteId: String, aoListar: (List<Dispositivo>) -> Unit, aoErro: AoErro) {
        colecao(pacienteId).get()
            .addOnSuccessListener { snap ->
                aoListar(snap.documents.map { Dispositivo.deDocumento(it) }.filter { it.ativo })
            }
            .addOnFailureListener { aoErro(it) }
    }

    fun cadastrar(pacienteId: String, nome: String, mac: String, aoCadastrar: () -> Unit, aoErro: AoErro) {
        val dispositivo = Dispositivo("", nome, mac, true, null, false, null)
        // O MAC (sem dois-pontos) é o identificador: a mesma caixa não é cadastrada duas vezes.
        val id = mac.replace(":", "")
        colecao(pacienteId).document(id).set(dispositivo.paraMapa())
            .addOnSuccessListener { aoCadastrar() }
            .addOnFailureListener { aoErro(it) }
    }

    fun renomear(pacienteId: String, id: String, nome: String, aoRenomear: () -> Unit, aoErro: AoErro) {
        colecao(pacienteId).document(id).update("nome", nome)
            .addOnSuccessListener { aoRenomear() }
            .addOnFailureListener { aoErro(it) }
    }

    fun revogar(pacienteId: String, id: String, aoRevogar: () -> Unit, aoErro: AoErro) {
        colecao(pacienteId).document(id).update("ativo", false, "online", false)
            .addOnSuccessListener { aoRevogar() }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val PACIENTES = "pacientes"
        private const val COLECAO = "dispositivos"
    }
}
