package br.com.cronomed.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/** Nível de acesso do usuário logado ao paciente que está acompanhando. */
enum class Nivel(val rotulo: String) {
    TITULAR("Titular"),
    GERENCIAMENTO("Gerenciamento"),
    VISUALIZACAO("Visualização");

    companion object {
        fun deTexto(texto: String?): Nivel =
            values().firstOrNull { it.name == texto } ?: VISUALIZACAO
    }
}

/** Papel do convidado (tela "Tipo de convidado"). */
enum class Papel(val rotulo: String) {
    FAMILIAR("Familiar"),
    CUIDADOR("Cuidador"),
    ENFERMEIRO("Enfermeiro(a) cuidador(a)"),
    MEDICO("Médico(a)");

    companion object {
        fun deTexto(texto: String?): Papel =
            values().firstOrNull { it.name == texto } ?: FAMILIAR
    }
}

object StatusDose {
    const val PENDENTE = "PENDENTE"
    const val TOMADA = "TOMADA"
    const val PERDIDA = "PERDIDA"
}

object StatusAcesso {
    const val ATIVO = "ATIVO"
    const val PAUSADO = "PAUSADO"
}

object StatusConvite {
    const val PENDENTE = "PENDENTE"
    const val ACEITO = "ACEITO"
}

private fun DocumentSnapshot.texto(campo: String): String = getString(campo) ?: ""

private fun DocumentSnapshot.inteiro(campo: String): Int = getLong(campo)?.toInt() ?: 0

private fun DocumentSnapshot.millis(campo: String): Long? = getTimestamp(campo)?.toDate()?.time

private fun milisParaTimestamp(ms: Long): Timestamp = Timestamp(Date(ms))

data class Usuario(
    val uid: String,
    val nome: String,
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val telefone2: String = "",
    val whatsapp: String = "",
    val emailOpcional: String = "",
    val nascimento: String = "",
    val tipo: String = "TITULAR"
) {
    fun paraMapa(): Map<String, Any> = hashMapOf(
        "nome" to nome,
        "cpf" to cpf,
        "email" to email,
        "telefone" to telefone,
        "telefone2" to telefone2,
        "whatsapp" to whatsapp,
        "emailOpcional" to emailOpcional,
        "nascimento" to nascimento,
        "tipo" to tipo
    )

    companion object {
        fun deDocumento(doc: DocumentSnapshot): Usuario = Usuario(
            uid = doc.id,
            nome = doc.texto("nome"),
            cpf = doc.texto("cpf"),
            email = doc.texto("email"),
            telefone = doc.texto("telefone"),
            telefone2 = doc.texto("telefone2"),
            whatsapp = doc.texto("whatsapp"),
            emailOpcional = doc.texto("emailOpcional"),
            nascimento = doc.texto("nascimento"),
            tipo = doc.texto("tipo").ifEmpty { "TITULAR" }
        )
    }
}

data class Paciente(
    val id: String,
    val nome: String,
    val titularUid: String
) {
    companion object {
        fun deDocumento(doc: DocumentSnapshot): Paciente = Paciente(
            id = doc.id,
            nome = doc.texto("nome"),
            titularUid = doc.texto("titularUid").ifEmpty { doc.id }
        )
    }
}

/** Paciente que o usuário pode acompanhar e o nível de acesso dele. */
data class Contexto(val paciente: Paciente, val nivel: Nivel)

data class Medicamento(
    val compartimento: Int,
    val nome: String,
    val dosagem: String,
    val unidade: String,
    val qtdPorDose: Int,
    val duracaoDias: Int,
    val dataInicio: String, // yyyy-MM-dd
    val estoque: Int,
    val estoqueInicial: Int,
    val horarios: List<String>, // "HH:mm"
    val repetirMin: Int,
    val instrucoes: String,
    val ativo: Boolean = true
) {
    /** Dias de estoque restantes, considerando todas as doses do dia. Null se não há horários. */
    fun diasRestantes(): Int? {
        if (horarios.isEmpty() || qtdPorDose <= 0) return null
        return estoque / (qtdPorDose * horarios.size)
    }

    fun paraMapa(): Map<String, Any> = hashMapOf(
        "compartimento" to compartimento,
        "nome" to nome,
        "dosagem" to dosagem,
        "unidade" to unidade,
        "qtdPorDose" to qtdPorDose,
        "duracaoDias" to duracaoDias,
        "dataInicio" to dataInicio,
        "estoque" to estoque,
        "estoqueInicial" to estoqueInicial,
        "horarios" to horarios,
        "repetirMin" to repetirMin,
        "instrucoes" to instrucoes,
        "ativo" to ativo
    )

    companion object {
        fun deDocumento(doc: DocumentSnapshot): Medicamento {
            val horarios = (doc.get("horarios") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            return Medicamento(
                compartimento = doc.inteiro("compartimento"),
                nome = doc.texto("nome"),
                dosagem = doc.texto("dosagem"),
                unidade = doc.texto("unidade").ifEmpty { "comprimido" },
                qtdPorDose = doc.inteiro("qtdPorDose").coerceAtLeast(1),
                duracaoDias = doc.inteiro("duracaoDias"),
                dataInicio = doc.texto("dataInicio"),
                estoque = doc.inteiro("estoque").coerceAtLeast(0),
                estoqueInicial = doc.inteiro("estoqueInicial"),
                horarios = horarios,
                repetirMin = doc.inteiro("repetirMin").let { if (it == 0) 5 else it },
                instrucoes = doc.texto("instrucoes"),
                ativo = doc.getBoolean("ativo") ?: true
            )
        }
    }
}

data class Dose(
    val id: String,
    val compartimento: Int,
    val nome: String,
    val dosagem: String,
    val unidade: String,
    val qtd: Int,
    val instrucoes: String,
    val agendadaEm: Long,
    val repetirMin: Int,
    val status: String,
    val confirmadaEm: Long? = null,
    val caixaAbertaEm: Long? = null
) {
    fun paraMapa(): Map<String, Any> = hashMapOf(
        "compartimento" to compartimento,
        "nome" to nome,
        "dosagem" to dosagem,
        "unidade" to unidade,
        "qtd" to qtd,
        "instrucoes" to instrucoes,
        "agendadaEm" to milisParaTimestamp(agendadaEm),
        "repetirMin" to repetirMin,
        "status" to status
    )

    companion object {
        fun deDocumento(doc: DocumentSnapshot): Dose = Dose(
            id = doc.id,
            compartimento = doc.inteiro("compartimento"),
            nome = doc.texto("nome"),
            dosagem = doc.texto("dosagem"),
            unidade = doc.texto("unidade").ifEmpty { "comprimido" },
            qtd = doc.inteiro("qtd").coerceAtLeast(1),
            instrucoes = doc.texto("instrucoes"),
            agendadaEm = doc.millis("agendadaEm") ?: 0L,
            repetirMin = doc.inteiro("repetirMin").let { if (it == 0) 5 else it },
            status = doc.texto("status").ifEmpty { StatusDose.PENDENTE },
            confirmadaEm = doc.millis("confirmadaEm"),
            caixaAbertaEm = doc.millis("caixaAbertaEm")
        )
    }
}

data class Acesso(
    val id: String,
    val pacienteId: String,
    val uid: String,
    val nome: String,
    val email: String,
    val papel: Papel,
    val nivel: Nivel,
    val status: String,
    val registro: String = "",
    val especialidade: String = "",
    val codigoConvite: String = "",
    val criadoEm: Long = 0L
) {
    fun paraMapa(): Map<String, Any> = hashMapOf(
        "pacienteId" to pacienteId,
        "uid" to uid,
        "nome" to nome,
        "email" to email,
        "papel" to papel.name,
        "nivel" to nivel.name,
        "status" to status,
        "registro" to registro,
        "especialidade" to especialidade,
        "codigoConvite" to codigoConvite,
        "criadoEm" to milisParaTimestamp(criadoEm)
    )

    companion object {
        fun idDe(pacienteId: String, uid: String): String = pacienteId + "_" + uid

        fun deDocumento(doc: DocumentSnapshot): Acesso = Acesso(
            id = doc.id,
            pacienteId = doc.texto("pacienteId"),
            uid = doc.texto("uid"),
            nome = doc.texto("nome"),
            email = doc.texto("email"),
            papel = Papel.deTexto(doc.getString("papel")),
            nivel = Nivel.deTexto(doc.getString("nivel")),
            status = doc.texto("status").ifEmpty { StatusAcesso.ATIVO },
            registro = doc.texto("registro"),
            especialidade = doc.texto("especialidade"),
            codigoConvite = doc.texto("codigoConvite"),
            criadoEm = doc.millis("criadoEm") ?: 0L
        )
    }
}

data class Convite(
    val codigo: String,
    val pacienteId: String,
    val titularNome: String,
    val nome: String,
    val email: String,
    val whatsapp: String,
    val status: String,
    val nivel: String,
    val expiraEm: Long,
    val criadoEm: Long
) {
    fun paraMapa(): Map<String, Any> = hashMapOf(
        "pacienteId" to pacienteId,
        "titularNome" to titularNome,
        "nome" to nome,
        "email" to email,
        "whatsapp" to whatsapp,
        "status" to status,
        "nivel" to nivel,
        "expiraEm" to milisParaTimestamp(expiraEm),
        "criadoEm" to milisParaTimestamp(criadoEm)
    )

    companion object {
        fun deDocumento(doc: DocumentSnapshot): Convite = Convite(
            codigo = doc.id,
            pacienteId = doc.texto("pacienteId"),
            titularNome = doc.texto("titularNome"),
            nome = doc.texto("nome"),
            email = doc.texto("email"),
            whatsapp = doc.texto("whatsapp"),
            status = doc.texto("status").ifEmpty { StatusConvite.PENDENTE },
            nivel = doc.texto("nivel").ifEmpty { Nivel.VISUALIZACAO.name },
            expiraEm = doc.millis("expiraEm") ?: 0L,
            criadoEm = doc.millis("criadoEm") ?: 0L
        )
    }
}

data class Dispositivo(
    val id: String,
    val nome: String,
    val mac: String,
    val ativo: Boolean,
    val bateria: Int?,
    val online: Boolean,
    val ultimoSincEm: Long?
) {
    fun paraMapa(): Map<String, Any> = hashMapOf(
        "nome" to nome,
        "mac" to mac,
        "ativo" to ativo,
        "online" to online
    )

    companion object {
        fun deDocumento(doc: DocumentSnapshot): Dispositivo = Dispositivo(
            id = doc.id,
            nome = doc.texto("nome"),
            mac = doc.texto("mac"),
            ativo = doc.getBoolean("ativo") ?: true,
            bateria = doc.getLong("bateria")?.toInt(),
            online = doc.getBoolean("online") ?: false,
            ultimoSincEm = doc.millis("ultimoSincEm")
        )
    }
}
