package br.com.cronomed.dose;

/** Situacao de uma ocorrencia de dose. TAKEN e terminal (garante baixa de estoque unica). */
public enum DoseStatus {
    PENDING, TAKEN, DECLINED, MISSED, OPENED_UNCONFIRMED, CANCELLED
}
