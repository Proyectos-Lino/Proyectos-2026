package com.labcore.platform.workorders;

import com.labcore.platform.catalog.SampleType;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SampleCodeGenerator {

  private final JdbcTemplate jdbcTemplate;

  public SampleCodeGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public GeneratedSampleCode next(SampleType sampleType) {
    String prefix = sampleType.getLaboratoryCodePrefix().trim().toUpperCase(Locale.ROOT);
    if (!prefix.matches("[A-Z0-9]{1,20}")) {
      throw new BusinessRuleViolationException(
          "Corrige la abreviatura del tipo de muestra para que contenga de 1 a 20 letras o números.");
    }
    Integer nextValue =
        jdbcTemplate.queryForObject(
            """
                INSERT INTO technical_sample_code_counter (prefix, last_value)
                VALUES (?, 1)
                ON CONFLICT (prefix)
                DO UPDATE SET last_value = technical_sample_code_counter.last_value + 1
                RETURNING last_value
                """,
            Integer.class,
            prefix);
    return new GeneratedSampleCode("%s-%d".formatted(prefix, nextValue), nextValue);
  }

  public record GeneratedSampleCode(String value, int sequence) {}
}
