-- Conserva la identificación utilizada por los exámenes existentes.
UPDATE inscripciones i SET codigo_externo = p.codigo
FROM participantes p WHERE p.id = i.participante_id AND nullif(trim(i.codigo_externo), '') IS NULL;
ALTER TABLE inscripciones ALTER COLUMN codigo_externo SET NOT NULL;
ALTER TABLE inscripciones ADD CONSTRAINT ck_inscripcion_codigo CHECK (length(trim(codigo_externo)) > 0);
ALTER TABLE inscripciones ADD CONSTRAINT uq_inscripcion_codigo UNIQUE (evaluacion_id, codigo_externo);

-- El código de participantes queda como referencia interna/compatibilidad; ya no identifica un examen.
ALTER TABLE participantes ADD COLUMN tipo_documento varchar(30) NOT NULL DEFAULT 'DOCUMENTO';
CREATE INDEX ix_persona_identidad ON participantes
    (organizacion_id, tipo_documento, upper(regexp_replace(trim(documento), '\s+', '', 'g')))
    WHERE nullif(trim(documento), '') IS NOT NULL;

CREATE TABLE importaciones_inscripciones (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organizacion_id bigint NOT NULL,
    evaluacion_id bigint NOT NULL,
    modo varchar(20) NOT NULL CHECK (modo IN ('REGISTROS', 'GRUPOS')),
    registrada_por bigint NOT NULL REFERENCES usuarios(id),
    registrada_en timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (evaluacion_id, organizacion_id) REFERENCES evaluaciones(id, organizacion_id)
);
CREATE INDEX ix_importaciones_inscripciones_evaluacion ON importaciones_inscripciones(evaluacion_id, id);
CREATE TABLE importaciones_inscripciones_filas (
    importacion_id bigint NOT NULL REFERENCES importaciones_inscripciones(id),
    fila integer NOT NULL,
    codigo varchar(80) NOT NULL,
    accion varchar(30) NOT NULL,
    detalle text NOT NULL,
    grupo_anterior varchar(180),
    grupo_nuevo varchar(180),
    PRIMARY KEY (importacion_id, fila)
);
