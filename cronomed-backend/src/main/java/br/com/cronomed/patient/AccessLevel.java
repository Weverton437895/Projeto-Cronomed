package br.com.cronomed.patient;

/**
 * Nivel efetivo de um usuario sobre um paciente (ordem crescente de poder):
 * NONE < VIEW (visualizar) < CONFIRM (confirmar doses - o proprio paciente) < MANAGE (gerenciar) < OWNER (titular).
 */
public enum AccessLevel {
    NONE, VIEW, CONFIRM, MANAGE, OWNER;

    public boolean atLeast(AccessLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
