package br.com.cronomed.data

import android.content.Context
import br.com.cronomed.model.Dose
import br.com.cronomed.model.StatusDose
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cópia local das próximas doses. É ela que permite o alarme tocar mesmo sem internet
 * (o receptor de alarme lê daqui, e não do Firestore).
 */
class AgendaLocal(contexto: Context) {
    private val prefs = contexto.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    /** Dose guardada junto com o paciente a quem ela pertence. */
    data class Item(val pacienteId: String, val dose: Dose, val lembretesEnviados: Int)

    fun salvar(pacienteId: String, doses: List<Dose>) {
        // Mantém os contadores de lembretes das doses que já existiam
        val antigos = ler().associateBy { it.dose.id }
        val novos = JSONArray()
        // Preserva itens de outros pacientes
        ler().filter { it.pacienteId != pacienteId }.forEach { novos.put(paraJson(it)) }
        doses.filter { it.status == StatusDose.PENDENTE }.forEach { d ->
            val contador = antigos[d.id]?.lembretesEnviados ?: 0
            novos.put(paraJson(Item(pacienteId, d, contador)))
        }
        prefs.edit().putString(CHAVE, novos.toString()).apply()
    }

    fun listar(): List<Item> = ler().sortedBy { it.dose.agendadaEm }

    fun buscar(doseId: String): Item? = ler().firstOrNull { it.dose.id == doseId }

    fun incrementarLembretes(doseId: String): Int {
        val itens = ler().toMutableList()
        val i = itens.indexOfFirst { it.dose.id == doseId }
        if (i < 0) return 0
        val novo = itens[i].copy(lembretesEnviados = itens[i].lembretesEnviados + 1)
        itens[i] = novo
        gravar(itens)
        return novo.lembretesEnviados
    }

    fun remover(doseId: String) {
        gravar(ler().filter { it.dose.id != doseId })
    }

    fun limpar() {
        prefs.edit().remove(CHAVE).apply()
    }

    private fun gravar(itens: List<Item>) {
        val arr = JSONArray()
        itens.forEach { arr.put(paraJson(it)) }
        prefs.edit().putString(CHAVE, arr.toString()).apply()
    }

    private fun ler(): List<Item> {
        val texto = prefs.getString(CHAVE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length()).map { deJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun paraJson(item: Item): JSONObject {
        val d = item.dose
        return JSONObject()
            .put("pacienteId", item.pacienteId)
            .put("lembretes", item.lembretesEnviados)
            .put("id", d.id)
            .put("comp", d.compartimento)
            .put("nome", d.nome)
            .put("dosagem", d.dosagem)
            .put("unidade", d.unidade)
            .put("qtd", d.qtd)
            .put("instrucoes", d.instrucoes)
            .put("quando", d.agendadaEm)
            .put("repetir", d.repetirMin)
    }

    private fun deJson(o: JSONObject): Item = Item(
        pacienteId = o.getString("pacienteId"),
        lembretesEnviados = o.optInt("lembretes", 0),
        dose = Dose(
            id = o.getString("id"),
            compartimento = o.getInt("comp"),
            nome = o.getString("nome"),
            dosagem = o.optString("dosagem"),
            unidade = o.optString("unidade", "comprimido"),
            qtd = o.optInt("qtd", 1),
            instrucoes = o.optString("instrucoes"),
            agendadaEm = o.getLong("quando"),
            repetirMin = o.optInt("repetir", 5),
            status = StatusDose.PENDENTE
        )
    )

    companion object {
        private const val ARQUIVO = "cronomed_agenda"
        private const val CHAVE = "doses"
    }
}
