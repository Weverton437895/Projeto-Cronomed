package br.com.cronomed.dao

import br.com.cronomed.model.Dose
import br.com.cronomed.model.Medicamento
import br.com.cronomed.model.StatusDose
import br.com.cronomed.util.AoErro
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Doses (a agenda de remédios). Cada dose tem um id previsível (compartimento + data/hora),
 * assim o app nunca cria a mesma dose duas vezes.
 */
class DoseDao(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun colecao(pacienteId: String): CollectionReference =
        db.collection(PACIENTES).document(pacienteId).collection(COLECAO)

    /** Doses cuja hora marcada está entre [deMs] e [ateMs]. */
    fun listarPeriodo(pacienteId: String, deMs: Long, ateMs: Long, aoListar: (List<Dose>) -> Unit, aoErro: AoErro) {
        colecao(pacienteId)
            .whereGreaterThanOrEqualTo("agendadaEm", Timestamp(Date(deMs)))
            .whereLessThanOrEqualTo("agendadaEm", Timestamp(Date(ateMs)))
            .get()
            .addOnSuccessListener { snap ->
                aoListar(snap.documents.map { Dose.deDocumento(it) }.sortedBy { it.agendadaEm })
            }
            .addOnFailureListener { aoErro(it) }
    }

    /**
     * Cria as doses dos próximos [dias] dias que ainda não existem.
     * Só cria documentos novos: doses já tomadas ou perdidas nunca são sobrescritas.
     */
    fun garantirDoses(
        pacienteId: String,
        medicamentos: List<Medicamento>,
        dias: Int,
        aoConcluir: () -> Unit,
        aoErro: AoErro
    ) {
        val inicio = inicioDoDia(System.currentTimeMillis())
        val fim = inicio + dias * DIA_MS
        val desejadas = gerar(medicamentos, inicio, fim)
        if (desejadas.isEmpty()) {
            aoConcluir()
            return
        }
        listarPeriodo(pacienteId, inicio, fim, { existentes ->
            val ids = existentes.map { it.id }.toSet()
            val novas = desejadas.filter { it.id !in ids }
            gravarEmLotes(pacienteId, novas, aoConcluir, aoErro)
        }, aoErro)
    }

    private fun gravarEmLotes(pacienteId: String, doses: List<Dose>, aoConcluir: () -> Unit, aoErro: AoErro) {
        if (doses.isEmpty()) {
            aoConcluir()
            return
        }
        val lotes = doses.chunked(TAMANHO_LOTE)
        var restantes = lotes.size
        var falhou = false
        lotes.forEach { lote ->
            val batch = db.batch()
            lote.forEach { d -> batch.set(colecao(pacienteId).document(d.id), d.paraMapa()) }
            batch.commit()
                .addOnSuccessListener {
                    restantes--
                    if (restantes == 0 && !falhou) aoConcluir()
                }
                .addOnFailureListener {
                    if (!falhou) {
                        falhou = true
                        aoErro(it)
                    }
                }
        }
    }

    /** Doses pendentes com mais de 60 minutos de atraso viram "perdidas". */
    fun marcarPerdidas(pacienteId: String, aoConcluir: () -> Unit, aoErro: AoErro) {
        val agora = System.currentTimeMillis()
        val limite = agora - TOLERANCIA_MS
        listarPeriodo(pacienteId, agora - 30 * DIA_MS, limite, { doses ->
            val atrasadas = doses.filter { it.status == StatusDose.PENDENTE }
            if (atrasadas.isEmpty()) {
                aoConcluir()
            } else {
                val lotes = atrasadas.chunked(TAMANHO_LOTE)
                var restantes = lotes.size
                var falhou = false
                lotes.forEach { lote ->
                    val batch = db.batch()
                    lote.forEach { d ->
                        batch.update(colecao(pacienteId).document(d.id), "status", StatusDose.PERDIDA)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            restantes--
                            if (restantes == 0 && !falhou) aoConcluir()
                        }
                        .addOnFailureListener {
                            if (!falhou) {
                                falhou = true
                                aoErro(it)
                            }
                        }
                }
            }
        }, aoErro)
    }

    /**
     * Confirma que o remédio foi tomado: marca a dose e desconta do estoque numa única operação
     * (WriteBatch). Ou as duas coisas acontecem, ou nenhuma.
     */
    fun confirmar(pacienteId: String, dose: Dose, aoConfirmar: () -> Unit, aoErro: AoErro) {
        val batch = db.batch()
        val doseRef = colecao(pacienteId).document(dose.id)
        val medRef = db.collection(PACIENTES).document(pacienteId)
            .collection("medicamentos").document(dose.compartimento.toString())
        batch.update(
            doseRef,
            mapOf<String, Any>(
                "status" to StatusDose.TOMADA,
                "confirmadaEm" to Timestamp(Date())
            )
        )
        batch.update(medRef, "estoque", FieldValue.increment(-dose.qtd.toLong()))
        batch.commit()
            .addOnSuccessListener { aoConfirmar() }
            .addOnFailureListener { aoErro(it) }
    }

    /** Apaga as doses pendentes futuras de um compartimento (ao editar ou excluir o medicamento). */
    fun removerFuturas(pacienteId: String, compartimento: Int, aoConcluir: () -> Unit, aoErro: AoErro) {
        val agora = System.currentTimeMillis()
        colecao(pacienteId).whereEqualTo("compartimento", compartimento).get()
            .addOnSuccessListener { snap ->
                val alvo = snap.documents.map { Dose.deDocumento(it) }
                    .filter { it.status == StatusDose.PENDENTE && it.agendadaEm >= agora - TOLERANCIA_MS }
                if (alvo.isEmpty()) {
                    aoConcluir()
                } else {
                    val lotes = alvo.chunked(TAMANHO_LOTE)
                    var restantes = lotes.size
                    var falhou = false
                    lotes.forEach { lote ->
                        val batch = db.batch()
                        lote.forEach { d -> batch.delete(colecao(pacienteId).document(d.id)) }
                        batch.commit()
                            .addOnSuccessListener {
                                restantes--
                                if (restantes == 0 && !falhou) aoConcluir()
                            }
                            .addOnFailureListener {
                                if (!falhou) {
                                    falhou = true
                                    aoErro(it)
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { aoErro(it) }
    }

    companion object {
        private const val PACIENTES = "pacientes"
        private const val COLECAO = "doses"
        private const val DIA_MS = 24L * 60 * 60 * 1000
        const val TOLERANCIA_MS = 60L * 60 * 1000
        private const val TAMANHO_LOTE = 400

        private fun inicioDoDia(ms: Long): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = ms
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        /** Monta as doses esperadas de cada medicamento ativo entre [inicio] e [fim]. */
        fun gerar(medicamentos: List<Medicamento>, inicio: Long, fim: Long): List<Dose> {
            // Doses que já passaram há mais de 60 min não são criadas "de trás para frente":
            // um medicamento cadastrado às 14h não nasce com a dose das 8h marcada como perdida.
            val corte = System.currentTimeMillis() - TOLERANCIA_MS
            val formatoId = SimpleDateFormat("yyyyMMddHHmm", Locale.US)
            val formatoData = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val resultado = mutableListOf<Dose>()
            medicamentos.filter { it.ativo }.forEach { med ->
                val iniTratamento = try {
                    formatoData.parse(med.dataInicio)?.time ?: inicio
                } catch (e: Exception) {
                    inicio
                }
                val fimTratamento = if (med.duracaoDias > 0) {
                    iniTratamento + med.duracaoDias * DIA_MS
                } else {
                    Long.MAX_VALUE
                }
                var dia = inicio
                while (dia < fim) {
                    med.horarios.forEach { hora ->
                        val partes = hora.split(":")
                        val h = partes.getOrNull(0)?.toIntOrNull()
                        val m = partes.getOrNull(1)?.toIntOrNull()
                        if (h != null && m != null) {
                            val c = Calendar.getInstance()
                            c.timeInMillis = dia
                            c.set(Calendar.HOUR_OF_DAY, h)
                            c.set(Calendar.MINUTE, m)
                            val quando = c.timeInMillis
                            if (quando >= iniTratamento && quando < fimTratamento && quando >= corte) {
                                resultado.add(
                                    Dose(
                                        id = med.compartimento.toString() + "_" + formatoId.format(Date(quando)),
                                        compartimento = med.compartimento,
                                        nome = med.nome,
                                        dosagem = med.dosagem,
                                        unidade = med.unidade,
                                        qtd = med.qtdPorDose,
                                        instrucoes = med.instrucoes,
                                        agendadaEm = quando,
                                        repetirMin = med.repetirMin,
                                        status = StatusDose.PENDENTE
                                    )
                                )
                            }
                        }
                    }
                    dia += DIA_MS
                }
            }
            return resultado
        }
    }
}
