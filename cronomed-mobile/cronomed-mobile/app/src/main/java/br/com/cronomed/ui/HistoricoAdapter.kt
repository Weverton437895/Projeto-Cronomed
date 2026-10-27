package br.com.cronomed.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.com.cronomed.R
import br.com.cronomed.model.Dose
import br.com.cronomed.model.StatusDose
import br.com.cronomed.util.Formatadores

/** Lista do histórico: cabeçalhos de dia (texto) alternados com as doses daquele dia. */
class HistoricoAdapter(private var itens: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class CabecalhoHolder(visao: View) : RecyclerView.ViewHolder(visao)

    class DoseHolder(visao: View) : RecyclerView.ViewHolder(visao) {
        val icone: CompartimentoIconView = visao.findViewById(R.id.ivCompartimento)
        val nome: TextView = visao.findViewById(R.id.tvNome)
        val detalhe: TextView = visao.findViewById(R.id.tvDetalhe)
        val hora: TextView = visao.findViewById(R.id.tvHora)
        val status: TextView = visao.findViewById(R.id.tvStatus)
    }

    /** Agrupa as doses por dia (mais recentes primeiro), intercalando os cabeçalhos. */
    fun atualizar(doses: List<Dose>) {
        val resultado = mutableListOf<Any>()
        var diaAtual = -1L
        doses.sortedByDescending { it.agendadaEm }.forEach { d ->
            val dia = Formatadores.inicioDoDia(d.agendadaEm)
            if (dia != diaAtual) {
                diaAtual = dia
                resultado.add(Formatadores.diaRelativo(d.agendadaEm))
            }
            resultado.add(d)
        }
        itens = resultado
        notifyDataSetChanged()
    }

    override fun getItemViewType(posicao: Int): Int = if (itens[posicao] is String) TIPO_CABECALHO else TIPO_DOSE

    override fun getItemCount(): Int = itens.size

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(pai.context)
        return if (tipo == TIPO_CABECALHO) {
            CabecalhoHolder(inflador.inflate(R.layout.item_historico_dia, pai, false))
        } else {
            DoseHolder(inflador.inflate(R.layout.item_dose, pai, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, posicao: Int) {
        val item = itens[posicao]
        if (holder is CabecalhoHolder && item is String) {
            (holder.itemView as TextView).text = item
        } else if (holder is DoseHolder && item is Dose) {
            val ctx = holder.itemView.context
            holder.icone.definirCompartimento(item.compartimento)
            holder.nome.text = item.nome
            holder.detalhe.text = ctx.getString(
                R.string.dose_detalhe, item.dosagem, Formatadores.quantidade(item.qtd, item.unidade)
            )
            holder.hora.text = Formatadores.hora(item.agendadaEm)
            val (texto, fundo, cor) = when (item.status) {
                StatusDose.TOMADA -> Triple(R.string.status_tomada, R.drawable.bg_selo_sucesso, R.color.sucesso)
                StatusDose.PERDIDA -> Triple(R.string.status_perdida, R.drawable.bg_selo_perigo, R.color.perigo)
                else -> Triple(R.string.status_pendente, R.drawable.bg_selo_neutro, R.color.texto2)
            }
            holder.status.setText(texto)
            holder.status.setBackgroundResource(fundo)
            holder.status.setTextColor(ContextCompat.getColor(ctx, cor))
        }
    }

    companion object {
        private const val TIPO_CABECALHO = 0
        private const val TIPO_DOSE = 1
    }
}
