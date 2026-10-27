package br.com.cronomed.data

import br.com.cronomed.model.Contexto
import br.com.cronomed.model.Nivel
import br.com.cronomed.model.Paciente
import br.com.cronomed.model.Usuario

/**
 * Dados da sessão atual (quem está logado e qual paciente está sendo acompanhado).
 * Um "object" em Kotlin é um singleton: existe uma única instância durante a vida do app.
 */
object Sessao {
    var usuario: Usuario? = null
    var contextos: List<Contexto> = emptyList()
    private var indiceAtual: Int = 0

    val contextoAtual: Contexto?
        get() = contextos.getOrNull(indiceAtual)

    val paciente: Paciente?
        get() = contextoAtual?.paciente

    val nivel: Nivel
        get() = contextoAtual?.nivel ?: Nivel.VISUALIZACAO

    val ehTitular: Boolean
        get() = nivel == Nivel.TITULAR

    /** Titular e quem tem "Gerenciamento" podem confirmar doses e editar medicamentos. */
    val podeGerenciar: Boolean
        get() = nivel == Nivel.TITULAR || nivel == Nivel.GERENCIAMENTO

    fun definirContextos(lista: List<Contexto>, preferido: String?) {
        contextos = lista
        val i = lista.indexOfFirst { it.paciente.id == preferido }
        indiceAtual = if (i >= 0) i else 0
    }

    fun escolherPaciente(pacienteId: String) {
        val i = contextos.indexOfFirst { it.paciente.id == pacienteId }
        if (i >= 0) indiceAtual = i
    }

    fun limpar() {
        usuario = null
        contextos = emptyList()
        indiceAtual = 0
    }
}
