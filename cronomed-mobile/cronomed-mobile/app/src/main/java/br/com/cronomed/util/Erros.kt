package br.com.cronomed.util

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Traduz as exceções do Firebase para mensagens simples em português. */
object Erros {
    private const val TAG = "CronoMed"

    fun mensagem(e: Exception?): String {
        // Log.e registra o erro técnico no Logcat; o usuário vê só a mensagem simples
        Log.e(TAG, "Falha: " + (e?.javaClass?.simpleName ?: "desconhecida") + " - " + e?.message)
        return when (e) {
            null -> "Algo deu errado. Tente novamente."
            is FirebaseNetworkException -> "Sem conexão com a internet. Verifique a rede e tente de novo."
            is FirebaseAuthWeakPasswordException -> "A senha é fraca. Use pelo menos 8 caracteres."
            is FirebaseAuthUserCollisionException -> "Já existe uma conta com este e-mail."
            is FirebaseAuthRecentLoginRequiredException -> "Por segurança, entre novamente para concluir esta ação."
            is FirebaseAuthInvalidUserException -> "Não encontramos uma conta com este e-mail."
            is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha incorretos."
            is FirebaseAuthException -> when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "O e-mail informado é inválido."
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "E-mail ou senha incorretos."
                "ERROR_USER_NOT_FOUND" -> "Não encontramos uma conta com este e-mail."
                "ERROR_USER_DISABLED" -> "Esta conta foi desativada."
                "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Aguarde um pouco e tente de novo."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Já existe uma conta com este e-mail."
                else -> "Não foi possível concluir. Tente novamente."
            }
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Você não tem permissão para esta ação."
                FirebaseFirestoreException.Code.UNAVAILABLE -> "Sem conexão. Os dados serão sincronizados quando a internet voltar."
                FirebaseFirestoreException.Code.NOT_FOUND -> "Não encontramos este registro."
                else -> "Não foi possível acessar os dados. Tente novamente."
            }
            else -> e.message?.takeIf { it.isNotBlank() } ?: "Algo deu errado. Tente novamente."
        }
    }
}
