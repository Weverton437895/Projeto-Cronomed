package br.com.cronomed.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.text.Normalizer
import java.util.Locale

/**
 * Recursos de voz: lê o lembrete em voz alta (TextToSpeech) e entende "sim" ou "não"
 * (SpeechRecognizer). Precisa da permissão RECORD_AUDIO para ouvir.
 */
class Voz(contexto: Context) {

    enum class Resposta { SIM, NAO, INDEFINIDA }

    private val app = contexto.applicationContext
    private var pronto = false
    private var fila: String? = null
    private var reconhecedor: SpeechRecognizer? = null

    private val fala: TextToSpeech = TextToSpeech(app) { status ->
        if (status == TextToSpeech.SUCCESS) {
            fala0().setLanguage(Locale("pt", "BR"))
            pronto = true
            fila?.let { falar(it) }
            fila = null
        }
    }

    // Evita referência ao campo "fala" dentro do próprio inicializador
    private fun fala0(): TextToSpeech = fala

    fun falar(texto: String) {
        if (!pronto) {
            fila = texto
            return
        }
        fala.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "cronomed")
    }

    fun disponivelParaOuvir(): Boolean = SpeechRecognizer.isRecognitionAvailable(app)

    fun ouvir(aoResponder: (Resposta) -> Unit, aoFalhar: () -> Unit) {
        if (!disponivelParaOuvir()) {
            aoFalhar()
            return
        }
        pararDeOuvir()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        reconhecedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(resultados: Bundle?) {
                val lista = resultados?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val texto = lista?.firstOrNull() ?: ""
                aoResponder(interpretar(texto))
            }

            override fun onError(erro: Int) {
                aoFalhar()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        r.startListening(intent)
    }

    fun pararDeOuvir() {
        reconhecedor?.destroy()
        reconhecedor = null
    }

    fun liberar() {
        pararDeOuvir()
        fala.stop()
        fala.shutdown()
    }

    companion object {
        private val SIM = listOf("sim", "tomei", "ja tomei", "confirmo", "confirmar", "claro", "pode ser")
        private val NAO = listOf("nao", "ainda nao", "depois", "nao tomei", "mais tarde")

        /** Transforma o que a pessoa falou em SIM, NAO ou INDEFINIDA (ignora acentos e maiúsculas). */
        fun interpretar(fala: String): Resposta {
            val limpo = Normalizer.normalize(fala.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .trim()
            if (limpo.isEmpty()) return Resposta.INDEFINIDA
            // "não" vence "sim" quando aparecem juntos ("ainda não tomei" contém "tomei")
            if (NAO.any { limpo == it || limpo.startsWith("$it ") || limpo.contains(it) }) return Resposta.NAO
            if (SIM.any { limpo == it || limpo.contains(it) }) return Resposta.SIM
            return Resposta.INDEFINIDA
        }
    }
}
