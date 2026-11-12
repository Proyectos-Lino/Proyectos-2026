ALTER TABLE reglas_calificacion ADD COLUMN formula_nota_oficial varchar(200);
ALTER TABLE resultados ADD COLUMN nota_oficial numeric(14,3);
ALTER TABLE versiones_resultado ADD COLUMN nota_oficial numeric(14,3);
