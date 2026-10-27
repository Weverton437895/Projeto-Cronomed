package br.com.cronomed.util

import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.EditText

object Validadores {
    /** Requisitos da senha mostrados na tela de cadastro (na mesma ordem do protótipo). */
    val REQUISITOS_SENHA = listOf(
        "8 caracteres",
        "1 letra maiúscula",
        "1 letra minúscula",
        "1 número",
        "1 caractere especial (!@#\$%)"
    )

    fun checarSenha(senha: String): List<Boolean> = listOf(
        senha.length >= 8,
        senha.any { it.isUpperCase() },
        senha.any { it.isLowerCase() },
        senha.any { it.isDigit() },
        senha.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    )

    fun senhaForte(senha: String): Boolean = checarSenha(senha).all { it }

    fun emailValido(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun soDigitos(texto: String): String = texto.filter { it.isDigit() }

    fun cpfValido(cpf: String): Boolean {
        val n = soDigitos(cpf)
        if (n.length != 11 || n.all { it == n[0] }) return false
        fun digito(qtd: Int): Int {
            var soma = 0
            for (i in 0 until qtd) soma += (n[i] - '0') * (qtd + 1 - i)
            val resto = (soma * 10) % 11
            return if (resto == 10) 0 else resto
        }
        return digito(9) == (n[9] - '0') && digito(10) == (n[10] - '0')
    }

    fun horaValida(texto: String): Boolean {
        val partes = texto.split(":")
        if (partes.size != 2) return false
        val h = partes[0].toIntOrNull() ?: return false
        val m = partes[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    /** Data no formato dd/MM/aaaa. */
    fun dataValida(texto: String): Boolean {
        val partes = texto.split("/")
        if (partes.size != 3) return false
        val d = partes[0].toIntOrNull() ?: return false
        val m = partes[1].toIntOrNull() ?: return false
        val a = partes[2].toIntOrNull() ?: return false
        if (a < 1900 || a > 2200 || m !in 1..12 || d < 1) return false
        val dias = intArrayOf(31, if (a % 4 == 0 && (a % 100 != 0 || a % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return d <= dias[m - 1]
    }
}

/** Aplica uma máscara enquanto o usuário digita. # = dígito. Ex.: "###.###.###-##". */
class MascaraTextWatcher(private val campo: EditText, private val mascara: String) : TextWatcher {
    private var editando = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (editando) return
        editando = true
        val digitos = Validadores.soDigitos(s?.toString() ?: "")
        val resultado = StringBuilder()
        var indice = 0
        for (c in mascara) {
            if (indice >= digitos.length) break
            if (c == '#') {
                resultado.append(digitos[indice])
                indice++
            } else {
                resultado.append(c)
            }
        }
        campo.setText(resultado.toString())
        campo.setSelection(resultado.length)
        editando = false
    }

    companion object {
        const val CPF = "###.###.###-##"
        const val DATA = "##/##/####"
        const val TELEFONE = "+## (##) #####-####"

        fun aplicar(campo: EditText, mascara: String) {
            campo.addTextChangedListener(MascaraTextWatcher(campo, mascara))
        }
    }
}
