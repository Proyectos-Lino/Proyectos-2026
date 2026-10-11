package com.labcore.platform.workorders;

import com.labcore.platform.catalog.SampleType;
import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SampleCodeGenerator {

  private final JdbcTemplate jdbcTemplate;

  public SampleCodeGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public GeneratedSampleCode next(SampleType sampleType) {
    int year = Year.now().getValue();
    Integer nextValue =
        jdbcTemplate.queryForObject(
            """
                INSERT INTO sample_code_counter (sample_type_id, calendar_year, last_value)
                VALUES (?, ?, 1)
                ON CONFLICT (sample_type_id, calendar_year)
                DO UPDATE SET last_value = sample_code_counter.last_value + 1
                RETURNING last_value
                """,
            Integer.class,
            sampleType.getId(),
            year);
    return new GeneratedSampleCode(
        "%s-%d-%04d".formatted(sampleType.getLaboratoryCodePrefix(), year, nextValue), nextValue);
  }

  public record GeneratedSampleCode(String value, int sequence) {}
}
