package com.admision.lector_dbf.report.controller;

import com.admision.lector_dbf.report.model.AnalyticsModels.*;
import com.admision.lector_dbf.report.service.AnalyticsService;
import com.admision.lector_dbf.report.service.AnalyticsCsvService;
import com.admision.lector_dbf.report.service.AnalyticsPdfService;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/informes")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final AnalyticsCsvService csv;
    private final AnalyticsPdfService pdf;
    public AnalyticsController(AnalyticsService analytics,AnalyticsCsvService csv,AnalyticsPdfService pdf) {
        this.analytics=analytics; this.csv=csv; this.pdf=pdf;
    }

    @GetMapping("/resumen")
    public Dashboard dashboard(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,Authentication auth) {
        return analytics.dashboard(new Filter(organizationId,evaluationId,groupId,formId),auth);
    }

    @GetMapping("/grupos/pagina")
    public PageResult<GroupStats> groups(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,
            @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String mode,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Authentication auth) {
        return analytics.groups(new Filter(organizationId,evaluationId,groupId,formId),query,mode,page,size,auth);
    }

    @GetMapping("/preguntas/pagina")
    public PageResult<QuestionStats> questions(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,
            @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String mode,
            @RequestParam(defaultValue="errores") String order,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size,Authentication auth) {
        return analytics.questions(new Filter(organizationId,evaluationId,groupId,formId),query,mode,order,page,size,auth);
    }

    @GetMapping("/resultados.csv")
    public ResponseEntity<StreamingResponseBody> results(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,Authentication auth) {
        Filter filter=new Filter(organizationId,evaluationId,groupId,formId);
        analytics.validate(filter,auth);
        return csvResponse("resultados",output -> csv.results(filter,auth,output));
    }

    @GetMapping("/grupos.csv")
    public ResponseEntity<StreamingResponseBody> groupsCsv(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,
            @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String mode,Authentication auth) {
        Filter filter=new Filter(organizationId,evaluationId,groupId,formId);
        analytics.validate(filter,auth); analytics.validateGroupMode(mode);
        return csvResponse("grupos",output -> csv.groups(filter,query,mode,auth,output));
    }

    @GetMapping("/preguntas.csv")
    public ResponseEntity<StreamingResponseBody> questionsCsv(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,
            @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String mode,
            @RequestParam(defaultValue="errores") String order,Authentication auth) {
        Filter filter=new Filter(organizationId,evaluationId,groupId,formId);
        analytics.validate(filter,auth); analytics.validateQuestionOptions(mode,order);
        return csvResponse("preguntas",output -> csv.questions(filter,query,mode,order,auth,output));
    }

    @GetMapping("/resumen.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long organizationId,@PathVariable long evaluationId,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) Long formId,Authentication auth) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=analisis-"+evaluationId+".pdf")
                .body(pdf.summary(new Filter(organizationId,evaluationId,groupId,formId),auth));
    }

    private ResponseEntity<StreamingResponseBody> csvResponse(String name,StreamingResponseBody body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+name+".csv").body(body);
    }
}
