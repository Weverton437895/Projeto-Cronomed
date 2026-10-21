package br.com.cronomed.dose;

public enum DoseEventType {
    /** Abertura fisica captada pelo sensor reed switch - NUNCA e confirmacao de ingestao (RF25/RF28). */
    BOX_OPENED,
    /** Resposta "Sim" do usuario (toque, voz ou cuidador) - unica que baixa o estoque (RF26/RF27). */
    CONFIRMED_YES,
    /** Resposta "Nao": sinaliza no dashboard, sem baixa de estoque. */
    CONFIRMED_NO,
    /** Adiamento com justificativa. */
    POSTPONED,
    /** Registrado pelo servidor: janela de tolerancia esgotada sem abertura nem confirmacao. */
    MISSED,
    /** Registrado pelo servidor: caixa aberta sem confirmacao digital correspondente. */
    OPENED_UNCONFIRMED
}
