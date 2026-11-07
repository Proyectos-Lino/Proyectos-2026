package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.evaluation.model.Preparation;
import com.admision.lector_dbf.evaluation.model.Preparation.Check;
import com.admision.lector_dbf.evaluation.repository.PreparationRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evidence.repository.ReviewRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreparationService {
    private final PreparationRepository preparation;
    private final EvaluationRepository evaluations;
    private final PublicationRepository publications;
    private final ReviewRepository reviews;
    private final OrganizationAccess access;
    public PreparationService(PreparationRepository preparation, EvaluationRepository evaluations,
            PublicationRepository publications, ReviewRepository reviews, OrganizationAccess access) {
        this.preparation = preparation;
        this.evaluations = evaluations;
        this.publications = publications;
        this.reviews = reviews;
        this.access = access;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Preparation get(long organization, long evaluation, Authentication auth) {
        access.require(organization, auth);
        var current = evaluations.require(organization, evaluation);
        var policy = publications.policy(evaluation);
        boolean approved = policy.approvedAt() != null;
        long forms = preparation.forms(evaluation);
        long missingKeys = preparation.missingKeys(evaluation);
        long pending = reviews.pending(evaluation);
        long ungrouped = publications.ungroupedResults(evaluation);
        return new Preparation(current.status(), approved,
                !approved && !List.of("PUBLICADA","ARCHIVADA").contains(current.status()), List.of(
                check("TEMAS","Temas registrados",forms > 0),
                new Check("CLAVES","Claves de todos los temas",missingKeys==0 && forms > 0,missingKeys),
                check("REGLA","Regla de calificación configurada",evaluations.latestRule(evaluation).isPresent()),
                check("INSCRIPCIONES","Participantes inscritos",preparation.registrations(evaluation)>0)), List.of(
                check("CIERRE","Evaluación cerrada",List.of("CERRADA","PUBLICADA","ARCHIVADA").contains(current.status())),
                check("CONSULTA","Datos y periodo de consulta configurados",policy.configured()),
                new Check("CASOS","Revisiones pendientes resueltas",pending==0,pending),
                new Check("GRUPOS", "Exámenes del ranking por grupo con agrupación asignada", ungrouped==0, ungrouped),
                check("HOJAS","Hojas observadas o recibidas resueltas",!publications.hasBlockingSheets(evaluation)),
                check("LOTES","Importaciones terminadas",!publications.hasUnfinishedBatches(evaluation)),
                check("RESULTADOS","Resultados válidos disponibles",publications.validResults(evaluation)>0),
                check("VERSIONES","Resultados calculados con la configuración vigente",!publications.hasStaleResults(evaluation))));
    }
    private Check check(String code, String label, boolean completed) {
        return new Check(code, label, completed, completed ? 0 : 1);
    }
}
