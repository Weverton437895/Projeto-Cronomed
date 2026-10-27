package br.com.cronomed.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.com.cronomed.R
import br.com.cronomed.model.Dose
import br.com.cronomed.model.StatusDose
import br.com.cronomed.util.Formatadores

/** Mostra a lista de doses de um dia (tela inicial). */
class DoseAdapter(
    private var doses: List<Dose>,
    private var podeConfirmar: Boolean,
    private val aoConfirmar: (Dose) -> Unit,
    private val aoAbrir: (Dose) -> Unit
) : RecyclerView.Adapter<DoseAdapter.DoseHolder>() {

    class DoseHolder(visao: View) : RecyclerView.ViewHolder(visao) {
        val icone: CompartimentoIconView = visao.findViewById(R.id.ivCompartimento)
        val nome: TextView = visao.findViewById(R.id.tvNome)
        val detalhe: TextView = visao.findViewById(R.id.tvDetalhe)
        val hora: TextView = visao.findViewById(R.id.tvHora)
        val status: TextView = visao.findViewById(R.id.tvStatus)
        val instrucoes: TextView = visao.findViewById(R.id.tvInstrucoes)
        val confirmar: Button = visao.findViewById(R.id.btnConfirmar)
    }

    fun atualizar(novas: List<Dose>, podeConfirmar: Boolean) {
        this.doses = novas
        this.podeConfirmar = podeConfirmar
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): DoseHolder {
        val visao = LayoutInflater.from(pai.context).inflate(R.layout.item_dose, pai, false)
        return DoseHolder(visao)
    }

    override fun getItemCount(): Int = doses.size

    override fun onBindViewHolder(holder: DoseHolder, posicao: Int) {
        val dose = doses[posicao]
        val ctx = holder.itemView.context
        holder.icone.definirCompartimento(dose.compartimento)
        holder.nome.text = dose.nome
        holder.detalhe.text = ctx.getString(
            R.string.dose_detalhe, dose.dosagem, Formatadores.quantidade(dose.qtd, dose.unidade)
        )
        holder.hora.text = Formatadores.hora(dose.agendadaEm)

        val atrasada = dose.status == StatusDose.PENDENTE && dose.agendadaEm < System.currentTimeMillis()
        val (texto, fundo, cor) = when {
            dose.status == StatusDose.TOMADA -> Triple(R.string.status_tomada, R.drawable.bg_selo_sucesso, R.color.sucesso)
            dose.status == StatusDose.PERDIDA -> Triple(R.string.status_perdida, R.drawable.bg_selo_perigo, R.color.perigo)
            atrasada -> Triple(R.string.status_atrasada, R.drawable.bg_selo_alerta, R.color.alerta)
            else -> Triple(R.string.status_pendente, R.drawable.bg_selo_neutro, R.color.texto2)
        }
        holder.status.setText(texto)
        holder.status.setBackgroundResource(fundo)
        holder.status.setTextColor(ContextCompat.getColor(ctx, cor))

        if (dose.instrucoes.isNotBlank()) {
            holder.instrucoes.visibility = View.VISIBLE
            holder.instrucoes.text = dose.instrucoes
        } else {
            holder.instrucoes.visibility = View.GONE
        }

        val pendente = dose.status == StatusDose.PENDENTE
        holder.confirmar.visibility = if (pendente && podeConfirmar) View.VISIBLE else View.GONE
        holder.confirmar.setOnClickListener { aoConfirmar(dose) }
        holder.itemView.setOnClickListener { if (pendente) aoAbrir(dose) }
    }
}
