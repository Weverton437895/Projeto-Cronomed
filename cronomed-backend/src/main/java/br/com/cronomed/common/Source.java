package br.com.cronomed.common;

/** Origem de um evento ou alteracao (usada na regra de desempate - RF33: mobile prevalece). */
public enum Source {
    DEVICE, MOBILE, WEB, SERVER
}
