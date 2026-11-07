package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.evaluation.model.ParticipantImportRow;
import com.admision.lector_dbf.evaluation.repository.ParticipantImportReportRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ParticipantImportReportService {
    private final ParticipantImportReportRepository reports;
    private final OrganizationAccess access;
    public ParticipantImportReportService(ParticipantImportReportRepository reports,OrganizationAccess access) {
        this.reports=reports; this.access=access;
    }
    public PageResult<ParticipantImportRow> page(long organization,long report,String query,String status,int page,int size,Authentication auth) {
        access.require(organization,auth); new PageRequest(page,size);
        if (!Set.of("","CREADO","COMPLETADO","CONSERVADO").contains(status)) throw ApiException.badRequest("Estado de importación inválido");
        return reports.page(organization,report,query,status,page,size);
    }
    public byte[] csv(long organization,long report,Authentication auth) {
        access.require(organization,auth);
        StringBuilder csv=new StringBuilder("\uFEFFfila;codigo;resultado\r\n");
        // Cada carga admite como máximo 5000 filas. La pantalla siempre las consulta paginadas.
        for (var row:reports.page(organization,report,"","",0,5000).items()) {
            String code=row.code().replaceAll("[\\p{Cntrl}]"," ");
            if (code.stripLeading().matches("^[=+@-].*")) code="'"+code;
            csv.append(row.row()).append(';').append('"').append(code.replace("\"","\"\"")).append("\";")
                    .append(row.status()).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
