package com.admision.lector_dbf.exam.controller;

import com.admision.lector_dbf.exam.model.ExamModels;
import com.admision.lector_dbf.exam.service.ExamService;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/examenes")
public class ExamController {
    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    @GetMapping
    public ExamModels.Page<ExamModels.Exam> page(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestParam(defaultValue = "") String code,
            @RequestParam(required = false) Long groupId, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "puntaje-desc") String order, Authentication auth) {
        return service.page(organizationId, evaluationId, code, groupId, status, page, size, order, auth);
    }

    @GetMapping("/{sheetId}")
    public ExamModels.Exam one(@PathVariable long organizationId, @PathVariable long evaluationId,
                                   @PathVariable long sheetId, Authentication auth) {
        return service.find(organizationId, evaluationId, sheetId, auth);
    }

    @GetMapping("/{sheetId}/explicacion")
    public ExamModels.Explanation explanation(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, Authentication auth) {
        return service.explanation(organizationId, evaluationId, sheetId, auth);
    }

    @GetMapping("/{sheetId}/decisiones/pagina")
    public PageResult<ExamModels.RankingDecision> decisions(@PathVariable long organizationId,
            @PathVariable long evaluationId, @PathVariable long sheetId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return service.decisions(organizationId, evaluationId, sheetId, page, size, auth);
    }
}
