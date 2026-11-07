ALTER TABLE evaluaciones ADD COLUMN tipo_grupo varchar(20) NOT NULL DEFAULT 'GRUPO';
ALTER TABLE evaluaciones ADD CONSTRAINT ck_evaluaciones_tipo_grupo
    CHECK (tipo_grupo IN ('GRUPO', 'CARRERA', 'SEDE', 'CATEGORIA', 'PROGRAMA'));
