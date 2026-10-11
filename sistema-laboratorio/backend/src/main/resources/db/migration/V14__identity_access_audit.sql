CREATE TABLE identity_access_log (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    resource_code VARCHAR(50) NOT NULL,
    action VARCHAR(60) NOT NULL,
    reason VARCHAR(250) NOT NULL,
    actor VARCHAR(180) NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_identity_access_actor_time
    ON identity_access_log(actor, accessed_at DESC);

CREATE INDEX idx_identity_access_resource
    ON identity_access_log(resource_type, resource_id, accessed_at DESC);
