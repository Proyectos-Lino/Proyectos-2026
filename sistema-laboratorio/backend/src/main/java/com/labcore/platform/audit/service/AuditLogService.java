package com.labcore.platform.audit;

import com.labcore.platform.common.export.CsvFile;
import com.labcore.platform.common.i18n.SystemLabel;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditLogService {
  private final AuditLogQueryRepository repository;

  public AuditLogService(AuditLogQueryRepository repository) {
    this.repository = repository;
  }

  public Page<AuditLogDtos.Entry> list(
      String module, String actor, Instant from, Instant to, Pageable pageable) {
    List<AuditLogDtos.Entry> entries =
        repository.find(
            module, actor, from, to, Math.toIntExact(pageable.getOffset()), pageable.getPageSize());
    return new PageImpl<>(entries, pageable, repository.count(module, actor, from, to));
  }

  public byte[] export(String module, String actor, Instant from, Instant to) {
    List<AuditLogDtos.Entry> entries = repository.find(module, actor, from, to, 0, 10_000);
    CsvFile csv =
        new CsvFile().row("Fecha", "Módulo", "Código", "Acción", "Usuario", "Descripción");
    entries.forEach(
        entry ->
            csv.row(
                entry.occurredAt(),
                entry.module(),
                entry.resourceCode(),
                SystemLabel.forCode(entry.action()),
                entry.actor(),
                SystemLabel.localizeText(entry.description())));
    return csv.bytes();
  }
}
