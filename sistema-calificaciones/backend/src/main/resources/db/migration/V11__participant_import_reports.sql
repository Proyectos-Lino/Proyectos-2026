CREATE TABLE importaciones_directorio (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organizacion_id bigint NOT NULL REFERENCES organizaciones(id),
    registrada_por bigint NOT NULL REFERENCES usuarios(id),
    registrada_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE importaciones_directorio_filas (
    importacion_id bigint NOT NULL REFERENCES importaciones_directorio(id) ON DELETE CASCADE,
    fila integer NOT NULL,
    codigo varchar(80) NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('CREADO','COMPLETADO','CONSERVADO')),
    PRIMARY KEY (importacion_id,fila)
);
CREATE INDEX ix_importacion_directorio_org ON importaciones_directorio(organizacion_id,id);
