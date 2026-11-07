-- Las publicaciones anteriores a la aprobación explícita no tienen un actor aprobador.
-- Se conserva su estado publicado y se habilita su configuración de consulta individual.
INSERT INTO configuraciones_publicacion(evaluacion_id,mostrar_puntaje,mostrar_puesto,
    mostrar_aprobacion,mostrar_conteos,publicada_en)
SELECT e.id,true,false,true,false,e.actualizada_en
FROM evaluaciones e
WHERE e.estado IN ('PUBLICADA','ARCHIVADA')
ON CONFLICT (evaluacion_id) DO NOTHING;
