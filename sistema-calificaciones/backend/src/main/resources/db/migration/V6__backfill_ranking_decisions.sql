-- De los resultados anteriores a V5 solo se conoce la decisión que estaba vigente.
-- Se conserva ese estado inicial para que la primera recalculación no borre su rastro.
INSERT INTO decisiones_resultado(evaluacion_id,resultado_id,politica_id,posicion,aprobado,cupo_aplicado,minima_aplicada)
SELECT r.evaluacion_id,r.id,p.id,r.posicion,r.aprobado,
       CASE WHEN p.modo_aprobacion IN ('VACANTES','AMBAS','VACANTES_O_MINIMA') THEN g.cupo ELSE NULL END,
       CASE WHEN p.modo_aprobacion IN ('MINIMA','AMBAS') OR
                 (p.modo_aprobacion='VACANTES_O_MINIMA' AND g.cupo IS NULL)
            THEN rc.minima_aprobacion ELSE NULL END
FROM resultados r
JOIN hojas_respuesta h ON h.id=r.hoja_id
JOIN inscripciones i ON i.id=h.inscripcion_id
LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id
JOIN reglas_calificacion rc ON rc.id=r.regla_id
JOIN politicas_resultado p ON p.evaluacion_id=r.evaluacion_id AND p.version=1
WHERE NOT EXISTS (SELECT 1 FROM decisiones_resultado d WHERE d.resultado_id=r.id);
