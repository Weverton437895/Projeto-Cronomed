package br.com.cronomed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import br.com.cronomed.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ícone de um compartimento da caixa. Cada um tem cor, forma e número diferentes, assim quem
 * não distingue cores também identifica o compartimento (RF16/RF17).
 */
class CompartimentoIconView @JvmOverloads constructor(
    contexto: Context,
    atributos: AttributeSet? = null,
    estilo: Int = 0
) : View(contexto, atributos, estilo) {

    private var numero = 1
    private val pincelForma = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pincelTexto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caminho = Path()

    init {
        pincelTexto.color = ContextCompat.getColor(contexto, R.color.preto)
        pincelTexto.typeface = Typeface.DEFAULT_BOLD
        pincelTexto.textAlign = Paint.Align.CENTER
        atualizarCor()
    }

    fun definirCompartimento(n: Int) {
        numero = n.coerceIn(1, 6)
        atualizarCor()
        invalidate()
    }

    private fun atualizarCor() {
        val cor = when (numero) {
            1 -> R.color.comp_1
            2 -> R.color.comp_2
            3 -> R.color.comp_3
            4 -> R.color.comp_4
            5 -> R.color.comp_5
            else -> R.color.comp_6
        }
        pincelForma.color = ContextCompat.getColor(context, cor)
    }

    override fun onMeasure(larguraSpec: Int, alturaSpec: Int) {
        val padrao = (44 * resources.displayMetrics.density).toInt()
        val l = resolveSize(padrao, larguraSpec)
        val a = resolveSize(padrao, alturaSpec)
        setMeasuredDimension(l, a)
    }

    override fun onDraw(canvas: Canvas) {
        val lado = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val raio = lado / 2f * 0.92f
        when (numero) {
            1 -> canvas.drawCircle(cx, cy, raio, pincelForma)
            2 -> canvas.drawRoundRect(RectF(cx - raio, cy - raio, cx + raio, cy + raio), raio * 0.25f, raio * 0.25f, pincelForma)
            3 -> desenharPoligono(canvas, cx, cy + raio * 0.1f, raio * 1.1f, 3)
            4 -> desenharPoligono(canvas, cx, cy, raio * 1.1f, 4)
            5 -> desenharPoligono(canvas, cx, cy, raio, 5)
            else -> desenharPoligono(canvas, cx, cy, raio, 6)
        }
        pincelTexto.textSize = lado * 0.42f
        val base = cy - (pincelTexto.descent() + pincelTexto.ascent()) / 2f
        canvas.drawText(numero.toString(), cx, base, pincelTexto)
    }

    private fun desenharPoligono(canvas: Canvas, cx: Float, cy: Float, raio: Float, lados: Int) {
        caminho.reset()
        // Losango (4 lados) fica em pé; os demais começam com uma ponta para cima
        val inicio = -PI / 2
        for (i in 0 until lados) {
            val angulo = inicio + 2 * PI * i / lados
            val x = cx + (raio * cos(angulo)).toFloat()
            val y = cy + (raio * sin(angulo)).toFloat()
            if (i == 0) caminho.moveTo(x, y) else caminho.lineTo(x, y)
        }
        caminho.close()
        canvas.drawPath(caminho, pincelForma)
    }
}
