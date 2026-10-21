-- CronoMed - schema inicial (PostgreSQL)
-- Todos os instantes sao armazenados em UTC (timestamptz). Integridade referencial por FK e
-- indices voltados a historicos/relatorios (secao 3.1-c e 3.4 da documentacao).

CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    email              VARCHAR(180) NOT NULL UNIQUE,
    password_hash      VARCHAR(100) NOT NULL,
    profile_type       VARCHAR(30)  NOT NULL,
    failed_attempts    INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    token_version      INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE TABLE push_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token        VARCHAR(512)  NOT NULL UNIQUE,
    platform     VARCHAR(20)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    last_seen_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_push_tokens_user ON push_tokens (user_id);

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    client_label VARCHAR(80),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_exp ON refresh_tokens (expires_at);

-- Lista negra de Access Tokens (logoff): entradas expiram naturalmente junto com o token.
CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_revoked_tokens_exp ON revoked_tokens (expires_at);

CREATE TABLE password_reset_otps (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    attempts   INTEGER      NOT NULL DEFAULT 0,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_otps_user ON password_reset_otps (user_id);

CREATE TABLE patients (
    id             UUID PRIMARY KEY,
    owner_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    linked_user_id UUID         REFERENCES users (id) ON DELETE SET NULL,
    name           VARCHAR(120) NOT NULL,
    timezone       VARCHAR(60)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_patients_owner ON patients (owner_id);
CREATE INDEX idx_patients_linked ON patients (linked_user_id);

-- Permissao / compartilhamento concedido pelo titular (RF39, RF40, RF41)
CREATE TABLE access_grants (
    id              UUID PRIMARY KEY,
    patient_id      UUID         NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    grantee_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    granted_by      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_level    VARCHAR(10)  NOT NULL,
    relationship    VARCHAR(30)  NOT NULL,
    consent_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_access_grants_active ON access_grants (patient_id, grantee_user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_access_grants_grantee ON access_grants (grantee_user_id) WHERE revoked_at IS NULL;

CREATE TABLE iot_devices (
    id                   UUID PRIMARY KEY,
    owner_id             UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mac_address          VARCHAR(17)  NOT NULL UNIQUE,
    name                 VARCHAR(80)  NOT NULL,
    secret_hash          VARCHAR(100) NOT NULL,
    status               VARCHAR(10)  NOT NULL,
    last_seen_at         TIMESTAMPTZ,
    battery_percent      INTEGER,
    battery_low_notified BOOLEAN      NOT NULL DEFAULT FALSE,
    offline_notified     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL,
    revoked_at           TIMESTAMPTZ
);
CREATE INDEX idx_devices_owner ON iot_devices (owner_id);

CREATE TABLE compartments (
    id           UUID PRIMARY KEY,
    device_id    UUID        NOT NULL REFERENCES iot_devices (id) ON DELETE CASCADE,
    index_number INTEGER     NOT NULL,
    patient_id   UUID        REFERENCES patients (id) ON DELETE SET NULL,
    color_hex    VARCHAR(7)  NOT NULL,
    color_name   VARCHAR(30) NOT NULL,
    pattern      VARCHAR(20) NOT NULL,
    tactile_id   VARCHAR(40) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_compartment_index UNIQUE (device_id, index_number),
    CONSTRAINT ck_compartment_index CHECK (index_number BETWEEN 1 AND 6)
);
CREATE INDEX idx_compartments_patient ON compartments (patient_id);

CREATE TABLE medications (
    id                   UUID PRIMARY KEY,
    patient_id           UUID         NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    compartment_id       UUID         REFERENCES compartments (id) ON DELETE SET NULL,
    name                 VARCHAR(120) NOT NULL,
    measure              VARCHAR(30)  NOT NULL,
    dosage               VARCHAR(60)  NOT NULL,
    dose_quantity        INTEGER      NOT NULL,
    stock_acquired       INTEGER      NOT NULL,
    stock_current        INTEGER      NOT NULL,
    min_days_alert       INTEGER      NOT NULL,
    observations         VARCHAR(500),
    active               BOOLEAN      NOT NULL,
    deleted_at           TIMESTAMPTZ,
    version              BIGINT       NOT NULL,
    last_modified_source VARCHAR(10)  NOT NULL,
    client_updated_at    TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_med_dose_qty CHECK (dose_quantity > 0),
    CONSTRAINT ck_med_stock CHECK (stock_current >= 0 AND stock_acquired >= 0)
);
CREATE INDEX idx_medications_patient ON medications (patient_id, updated_at);
CREATE UNIQUE INDEX uq_medications_compartment ON medications (compartment_id) WHERE compartment_id IS NOT NULL;

CREATE TABLE schedules (
    id            UUID PRIMARY KEY,
    medication_id UUID        NOT NULL REFERENCES medications (id) ON DELETE CASCADE,
    time_of_day   TIME        NOT NULL,
    days_mask     INTEGER     NOT NULL,
    active        BOOLEAN     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_schedule_mask CHECK (days_mask BETWEEN 1 AND 127)
);
CREATE INDEX idx_schedules_medication ON schedules (medication_id);

-- Ocorrencias futuras de dose, pre-geradas (distribuicao previa dos cronogramas - 3.1-b)
CREATE TABLE scheduled_doses (
    id                   UUID PRIMARY KEY,
    medication_id        UUID        NOT NULL REFERENCES medications (id) ON DELETE CASCADE,
    patient_id           UUID        NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    schedule_id          UUID,
    scheduled_at         TIMESTAMPTZ NOT NULL,
    status               VARCHAR(24) NOT NULL,
    dose_quantity        INTEGER     NOT NULL,
    due_notified_at      TIMESTAMPTZ,
    opened_at            TIMESTAMPTZ,
    confirmed_at         TIMESTAMPTZ,
    stock_deducted       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_doses_patient_time ON scheduled_doses (patient_id, scheduled_at);
CREATE INDEX idx_doses_status_time ON scheduled_doses (status, scheduled_at);
CREATE INDEX idx_doses_medication ON scheduled_doses (medication_id, scheduled_at);
CREATE INDEX idx_doses_updated ON scheduled_doses (patient_id, updated_at);

-- Historico de eventos: aberturas fisicas (sensor) separadas das confirmacoes (RF28).
-- O ID e gerado na origem (UUID) e serve de chave de idempotencia na sincronizacao.
CREATE TABLE dose_events (
    id                UUID PRIMARY KEY,
    scheduled_dose_id UUID        REFERENCES scheduled_doses (id) ON DELETE CASCADE,
    medication_id     UUID,
    patient_id        UUID        NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    device_id         UUID        REFERENCES iot_devices (id) ON DELETE SET NULL,
    compartment_index INTEGER,
    type              VARCHAR(24) NOT NULL,
    source            VARCHAR(10) NOT NULL,
    actor_user_id     UUID,
    occurred_at       TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL,
    client_seq        BIGINT,
    justification     VARCHAR(500)
);
CREATE INDEX idx_dose_events_patient_time ON dose_events (patient_id, occurred_at);
CREATE INDEX idx_dose_events_dose ON dose_events (scheduled_dose_id);

CREATE TABLE stock_movements (
    id            UUID PRIMARY KEY,
    medication_id UUID        NOT NULL REFERENCES medications (id) ON DELETE CASCADE,
    delta         INTEGER     NOT NULL,
    balance_after INTEGER     NOT NULL,
    reason        VARCHAR(20) NOT NULL,
    ref_id        UUID,
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_stock_movements_med ON stock_movements (medication_id, created_at);

CREATE TABLE alerts (
    id          UUID PRIMARY KEY,
    patient_id  UUID         NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL,
    message     VARCHAR(300) NOT NULL,
    related_id  UUID,
    created_at  TIMESTAMPTZ  NOT NULL,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_alerts_patient_time ON alerts (patient_id, created_at DESC);
CREATE INDEX idx_alerts_open_related ON alerts (type, related_id) WHERE resolved_at IS NULL;

-- Auditoria imutavel de permissoes e acessos (LGPD Art. 6, X). Sem FK em actor_user_id para
-- que o trigger abaixo possa proibir UPDATE/DELETE de forma absoluta.
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY,
    actor_user_id UUID,
    action        VARCHAR(60) NOT NULL,
    target_type   VARCHAR(40),
    target_id     VARCHAR(64),
    ip            VARCHAR(64),
    details       VARCHAR(1000),
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_created ON audit_logs (created_at);
CREATE INDEX idx_audit_target ON audit_logs (target_type, target_id);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id);

CREATE FUNCTION audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs e imutavel (append-only)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();

CREATE TABLE contact_messages (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120)  NOT NULL,
    email      VARCHAR(180)  NOT NULL,
    message    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

-- Tabela exigida pelo ShedLock (agendamento sem duplicidade entre instancias)
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
