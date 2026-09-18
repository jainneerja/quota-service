-- Local/dev seed data only — never ship real keys in a migration.
INSERT INTO api_keys (api_key, org_id, role) VALUES
    ('admin-dev-key', '00000000-0000-0000-0000-000000000000', 'ADMIN'),
    ('org-acme-dev-key', '11111111-1111-1111-1111-111111111111', 'ORG');

INSERT INTO quotas (org_id, resource_type, limit_amount, used_amount) VALUES
    ('11111111-1111-1111-1111-111111111111', 'droplet', 50, 0),
    ('11111111-1111-1111-1111-111111111111', 'volume', 20, 0);
