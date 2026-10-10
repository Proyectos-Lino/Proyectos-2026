package com.labcore.platform.orders;

import com.labcore.platform.organization.OrganizationProfileService;
import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentCodeGenerator {

  private final JdbcTemplate jdbcTemplate;
  private final OrganizationProfileService organizationService;

  public DocumentCodeGenerator(
      JdbcTemplate jdbcTemplate, OrganizationProfileService organizationService) {
    this.jdbcTemplate = jdbcTemplate;
    this.organizationService = organizationService;
  }

  public String next(String documentType) {
    int year = Year.now(organizationService.zoneId()).getValue();
    Integer nextValue =
        jdbcTemplate.queryForObject(
            """
                INSERT INTO document_code_counter (document_type, calendar_year, last_value)
                VALUES (?, ?, 1)
                ON CONFLICT (document_type, calendar_year)
                DO UPDATE SET last_value = document_code_counter.last_value + 1
                RETURNING last_value
                """,
            Integer.class,
            documentType,
            year);
    return "%s-%d-%04d".formatted(organizationService.prefixFor(documentType), year, nextValue);
  }
}
