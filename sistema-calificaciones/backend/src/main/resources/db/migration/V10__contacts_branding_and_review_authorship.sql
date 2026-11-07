ALTER TABLE participantes ADD COLUMN registro_automatico boolean NOT NULL DEFAULT false;
UPDATE participantes p SET registro_automatico=true
WHERE p.nombres=p.codigo AND nullif(trim(p.apellidos),'') IS NULL AND nullif(trim(p.documento),'') IS NULL AND nullif(trim(p.correo),'') IS NULL
  AND EXISTS (SELECT 1 FROM inscripciones i JOIN hojas_respuesta h ON h.inscripcion_id=i.id
              WHERE i.participante_id=p.id AND h.lote_id IS NOT NULL);

ALTER TABLE organizaciones ADD COLUMN razon_social varchar(180);
ALTER TABLE organizaciones ADD COLUMN identificacion_fiscal varchar(40);
ALTER TABLE organizaciones ADD COLUMN direccion varchar(250);
ALTER TABLE organizaciones ADD COLUMN ciudad varchar(100);
ALTER TABLE organizaciones ADD COLUMN pais varchar(100);
ALTER TABLE organizaciones ADD COLUMN telefono varchar(40);
ALTER TABLE organizaciones ADD COLUMN correo varchar(254);
ALTER TABLE organizaciones ADD COLUMN sitio_web varchar(250);
ALTER TABLE organizaciones ADD COLUMN oficina_responsable varchar(180);
ALTER TABLE organizaciones ADD COLUMN firmante_nombre varchar(160);
ALTER TABLE organizaciones ADD COLUMN firmante_cargo varchar(160);

CREATE TABLE organizaciones_logos (
    organizacion_id bigint PRIMARY KEY REFERENCES organizaciones(id),
    contenido bytea NOT NULL,
    tipo_contenido varchar(40) NOT NULL,
    actualizada_por bigint NOT NULL REFERENCES usuarios(id),
    actualizada_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_logo_tamanio CHECK (octet_length(contenido)>0 AND octet_length(contenido)<=2097152)
);

UPDATE revisiones SET asignada_a=resuelta_por WHERE asignada_a IS NULL AND resuelta_por IS NOT NULL;
