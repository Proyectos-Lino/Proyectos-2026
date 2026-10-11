ALTER TABLE client
    ADD COLUMN contact_name VARCHAR(160),
    ADD COLUMN phone VARCHAR(40);

CREATE INDEX idx_client_document_number ON client(document_number);
CREATE INDEX idx_client_email ON client(email);
CREATE INDEX idx_client_active ON client(active);
CREATE INDEX idx_service_order_client_created ON service_order(client_id, created_at DESC);
