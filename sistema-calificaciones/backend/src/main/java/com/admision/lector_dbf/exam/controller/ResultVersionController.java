package com.admision.lector_dbf.exam.controller;

import com.admision.lector_dbf.exam.model.ResultVersion;
import com.admision.lector_dbf.exam.model.ResultVersionDetail;
import com.admision.lector_dbf.exam.model.RecalculateRequest;
import com.admision.lector_dbf.exam.service.ResultVersionService;
import com.admision.lector_dbf.shared.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}")
public class ResultVersionController {
    private final ResultVersionService service;
    public ResultVersionController(ResultVersionService service) { this.service = service; }

    @GetMapping("/examenes/{sheetId}/versiones/pagina")
    public PageResult<ResultVersion> page(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size, Authentication auth) {
        return service.page(organizationId, evaluationId, sheetId, page, size, auth);
    }

    @PostMapping("/recalculo")
    public Map<String, Integer> recalculate(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody RecalculateRequest request, Authentication auth) {
        return Map.of("recalculated", service.recalculate(organizationId, evaluationId, request.reason(), auth));
    }

    @GetMapping("/examenes/{sheetId}/versiones/{versionId}")
    public ResultVersionDetail detail(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, @PathVariable long versionId, Authentication auth) {
        return service.detail(organizationId, evaluationId, sheetId, versionId, auth);
    }
}
