INSERT INTO role_permission(role_code,permission)
SELECT role_code,permission FROM unnest(ARRAY['ADMIN','DENTIST']) role_code CROSS JOIN unnest(ARRAY['CLINICAL_READ','CLINICAL_WRITE','DOCUMENTS_READ','DOCUMENTS_WRITE','CLINICAL_CONFIG_READ']) permission ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_code,permission) VALUES('ADMIN','CLINICAL_CONFIG_WRITE') ON CONFLICT DO NOTHING;
CREATE TABLE clinical_template(id UUID PRIMARY KEY,name VARCHAR(120) NOT NULL,kind VARCHAR(20) NOT NULL CHECK(kind IN('ENCOUNTER','BACKGROUND','CONSENT')),content TEXT NOT NULL CHECK(length(content)<=12000),active BOOLEAN NOT NULL,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE UNIQUE INDEX uq_clinical_template_name ON clinical_template(lower(name));
CREATE TABLE clinical_state(id UUID PRIMARY KEY,patient_id UUID NOT NULL REFERENCES patient(id),kind VARCHAR(20) NOT NULL CHECK(kind IN('BACKGROUND','ODONTOGRAM')),recorded_on DATE NOT NULL,previous_id UUID REFERENCES clinical_state(id),payload TEXT NOT NULL,actor_name VARCHAR(120) NOT NULL,reason VARCHAR(500) NOT NULL,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_clinical_state_history ON clinical_state(patient_id,kind,created_at DESC,id);
CREATE TABLE clinical_encounter(id UUID PRIMARY KEY,patient_id UUID NOT NULL REFERENCES patient(id),dentist_id UUID NOT NULL REFERENCES dentist(id),appointment_id UUID UNIQUE REFERENCES appointment(id),attended_on DATE NOT NULL,reason VARCHAR(500) NOT NULL,status VARCHAR(16) NOT NULL CHECK(status IN('DRAFT','FINAL')),draft TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_encounter_patient ON clinical_encounter(patient_id,attended_on DESC,id);
CREATE TABLE encounter_revision(id UUID PRIMARY KEY,encounter_id UUID NOT NULL REFERENCES clinical_encounter(id),number INTEGER NOT NULL CHECK(number>0),patient_name VARCHAR(160) NOT NULL,patient_code VARCHAR(30) NOT NULL,birth_date DATE,patient_document VARCHAR(80) NOT NULL,dentist_name VARCHAR(120) NOT NULL,attended_on DATE NOT NULL,reason VARCHAR(500) NOT NULL,payload TEXT NOT NULL,actor_name VARCHAR(120) NOT NULL,correction_reason VARCHAR(500) NOT NULL,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(encounter_id,number));
CREATE INDEX idx_encounter_revision ON encounter_revision(encounter_id,created_at DESC,id);
CREATE TABLE document_category(id UUID PRIMARY KEY,name VARCHAR(120) NOT NULL,active BOOLEAN NOT NULL,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE UNIQUE INDEX uq_document_category_name ON document_category(lower(name));
CREATE TABLE document_policy(id SMALLINT PRIMARY KEY CHECK(id=1),max_file_mi_b INTEGER NOT NULL DEFAULT 20 CHECK(max_file_mi_b BETWEEN 1 AND 60),version BIGINT NOT NULL DEFAULT 0);
INSERT INTO document_policy(id)VALUES(1);
CREATE TABLE patient_document(id UUID PRIMARY KEY,patient_id UUID NOT NULL REFERENCES patient(id),encounter_id UUID REFERENCES clinical_encounter(id),tooth INTEGER CHECK((tooth/10 BETWEEN 1 AND 4 AND tooth%10 BETWEEN 1 AND 8) OR(tooth/10 BETWEEN 5 AND 8 AND tooth%10 BETWEEN 1 AND 5)),category_id UUID NOT NULL REFERENCES document_category(id),category_name VARCHAR(120) NOT NULL,recorded_on DATE NOT NULL,description VARCHAR(1000) NOT NULL,file_name VARCHAR(180) NOT NULL,media_type VARCHAR(40) NOT NULL CHECK(media_type IN('image/jpeg','image/png','image/webp','application/pdf')),byte_size BIGINT NOT NULL CHECK(byte_size>0 AND byte_size<=62914560),sha256 VARCHAR(64) NOT NULL,actor_name VARCHAR(120) NOT NULL,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_patient_document ON patient_document(patient_id,recorded_on DESC,id);
CREATE INDEX idx_document_encounter ON patient_document(encounter_id);
CREATE TABLE document_content(id UUID PRIMARY KEY REFERENCES patient_document(id),content BYTEA NOT NULL);
CREATE TABLE document_consent(id UUID PRIMARY KEY,patient_id UUID NOT NULL REFERENCES patient(id),document_id UUID NOT NULL REFERENCES patient_document(id),name VARCHAR(160) NOT NULL,responsible VARCHAR(160) NOT NULL,relationship VARCHAR(80) NOT NULL,signed_on DATE NOT NULL,actor_name VARCHAR(120) NOT NULL,version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_document_consent ON document_consent(patient_id,signed_on DESC,id);
CREATE FUNCTION protect_clinical_original() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Clinical original is immutable'; END $$;
CREATE TRIGGER clinical_state_immutable BEFORE UPDATE OR DELETE ON clinical_state FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE TRIGGER encounter_revision_immutable BEFORE UPDATE OR DELETE ON encounter_revision FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE TRIGGER patient_document_immutable BEFORE UPDATE OR DELETE ON patient_document FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE TRIGGER document_content_immutable BEFORE UPDATE OR DELETE ON document_content FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE TRIGGER document_consent_immutable BEFORE UPDATE OR DELETE ON document_consent FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();

CREATE FUNCTION protect_final_encounter() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status='FINAL' AND (NEW.patient_id IS DISTINCT FROM OLD.patient_id OR NEW.dentist_id IS DISTINCT FROM OLD.dentist_id OR NEW.appointment_id IS DISTINCT FROM OLD.appointment_id OR NEW.attended_on IS DISTINCT FROM OLD.attended_on OR NEW.reason IS DISTINCT FROM OLD.reason OR NEW.draft IS DISTINCT FROM OLD.draft OR NEW.status IS DISTINCT FROM OLD.status) THEN RAISE EXCEPTION 'Final encounter identity and original are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER clinical_encounter_original BEFORE UPDATE ON clinical_encounter FOR EACH ROW EXECUTE FUNCTION protect_final_encounter();
CREATE FUNCTION validate_document_content() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN IF octet_length(NEW.content) IS DISTINCT FROM (SELECT byte_size FROM patient_document WHERE id=NEW.id) THEN RAISE EXCEPTION 'Document size mismatch'; END IF; RETURN NEW; END $$;
CREATE TRIGGER document_content_size BEFORE INSERT ON document_content FOR EACH ROW EXECUTE FUNCTION validate_document_content();
INSERT INTO document_category(id,name,active) VALUES('30000000-0000-0000-0000-000000000001','Fotografías',true),('30000000-0000-0000-0000-000000000002','Radiografías e imágenes',true),('30000000-0000-0000-0000-000000000003','Consentimientos',true),('30000000-0000-0000-0000-000000000004','Informes y otros documentos',true);
INSERT INTO clinical_template(id,name,kind,content,active) VALUES('30000000-0000-0000-0000-000000000011','Anamnesis básica','BACKGROUND','Motivo de consulta:
Antecedentes personales y familiares:
Alergias informadas:
Medicación informada:
Observaciones:',true),('30000000-0000-0000-0000-000000000012','Registro básico de atención','ENCOUNTER','Síntomas informados:
Exploración clínica:
Observaciones del profesional:',true),('30000000-0000-0000-0000-000000000013','Datos de consentimiento documental','CONSENT','Procedimiento autorizado:
Información proporcionada:
Responsable:
Fecha:
Adjuntar copia del consentimiento otorgado.',true);
