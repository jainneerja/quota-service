CREATE TABLE quotas (
    org_id UUID NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    limit_amount INT NOT NULL,
    used_amount INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, resource_type)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reservations_org ON reservations (org_id);
CREATE INDEX idx_reservations_expires ON reservations (status, expires_at);

CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key VARCHAR(100) NOT NULL UNIQUE,
    org_id UUID NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'ORG'))
);
