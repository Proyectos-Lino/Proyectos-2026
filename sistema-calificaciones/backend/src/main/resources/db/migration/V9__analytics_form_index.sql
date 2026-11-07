-- Soporta el filtro por tema de los informes sin alterar los resultados guardados.
CREATE INDEX ix_hojas_respuesta_evaluacion_tema ON hojas_respuesta(evaluacion_id,formulario_id);
