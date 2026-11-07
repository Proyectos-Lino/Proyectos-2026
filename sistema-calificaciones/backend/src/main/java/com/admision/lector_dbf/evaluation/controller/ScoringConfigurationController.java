package com.admision.lector_dbf.evaluation.controller;

import com.admision.lector_dbf.evaluation.model.ScoringModels.RankingPolicy;
import com.admision.lector_dbf.evaluation.model.ScoringModels.SectionConfiguration;
import com.admision.lector_dbf.evaluation.model.ScoringRequests.SaveRankingPolicy;
import com.admision.lector_dbf.evaluation.model.ScoringRequests.SaveSections;
import com.admision.lector_dbf.evaluation.service.RankingPolicyService;
import com.admision.lector_dbf.evaluation.service.SectionConfigurationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}")
public class ScoringConfigurationController {
    private final RankingPolicyService policies;
    private final SectionConfigurationService sections;

    public ScoringConfigurationController(RankingPolicyService policies, SectionConfigurationService sections) {
        this.policies = policies; this.sections = sections;
    }

    @GetMapping("/politica-resultados")
    public RankingPolicy currentPolicy(@PathVariable long organizationId, @PathVariable long evaluationId,
                                       Authentication auth) {
        return policies.current(organizationId, evaluationId, auth);
    }

    @PostMapping("/politica-resultados")
    public RankingPolicy savePolicy(@PathVariable long organizationId, @PathVariable long evaluationId,
                                    @Valid @RequestBody SaveRankingPolicy request, Authentication auth) {
        return policies.save(organizationId, evaluationId, request, auth);
    }

    @GetMapping("/formularios/{formId}/secciones/actual")
    public SectionConfiguration currentSections(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long formId, Authentication auth) {
        return sections.current(organizationId, evaluationId, formId, auth);
    }

    @PostMapping("/formularios/{formId}/secciones")
    public SectionConfiguration saveSections(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long formId, @Valid @RequestBody SaveSections request, Authentication auth) {
        return sections.save(organizationId, evaluationId, formId, request, auth);
    }
}
