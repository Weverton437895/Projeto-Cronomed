package br.com.cronomed.util

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser

/** Operações sensíveis do Firebase exigem que o usuário confirme a senha antes. */
object Autenticacao {

    fun reautenticar(usuario: FirebaseUser, senha: String, aoConfirmar: () -> Unit, aoErro: AoErro) {
        val email = usuario.email
        if (email == null) {
            aoErro(IllegalStateException("Conta sem e-mail"))
            return
        }
        val credencial = EmailAuthProvider.getCredential(email, senha)
        usuario.reauthenticate(credencial)
            .addOnSuccessListener { aoConfirmar() }
            .addOnFailureListener { aoErro(it) }
    }
}
