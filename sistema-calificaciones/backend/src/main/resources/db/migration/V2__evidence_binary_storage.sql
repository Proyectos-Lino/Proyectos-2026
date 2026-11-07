-- El contenido binario vive separado de los metadatos para no cargar imágenes al listar revisiones.
ALTER TABLE evidencias_revision DROP COLUMN ruta_almacenamiento;

CREATE TABLE evidencias_contenido (
    evidencia_id bigint PRIMARY KEY REFERENCES evidencias_revision(id) ON DELETE CASCADE,
    contenido bytea NOT NULL,
    CONSTRAINT ck_evidencias_contenido_no_vacio CHECK (octet_length(contenido) > 0)
);
