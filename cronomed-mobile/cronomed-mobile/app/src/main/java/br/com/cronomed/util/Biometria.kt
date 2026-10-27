package br.com.cronomed.util

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/** Bloqueio do app por digital ou rosto, usando a biblioteca androidx.biometric. */
object Biometria {

    private const val AUTENTICADORES = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun disponivel(contexto: Context): Boolean =
        BiometricManager.from(contexto).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(
        activity: AppCompatActivity,
        titulo: String,
        textoCancelar: String,
        aoAutenticar: () -> Unit,
        aoFalhar: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                aoAutenticar()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                aoFalhar()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setAllowedAuthenticators(AUTENTICADORES)
            .setNegativeButtonText(textoCancelar)
            .build()
        prompt.authenticate(info)
    }
}
