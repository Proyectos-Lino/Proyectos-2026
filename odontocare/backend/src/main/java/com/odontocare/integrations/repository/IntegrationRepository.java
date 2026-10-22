package com.odontocare.integrations.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IntegrationRepository {
  public record Stored(long version, byte[] encrypted) {
    @Override
    public String toString() {
      return "Stored[protected]";
    }
  }

  private final JdbcTemplate jdbc;

  public IntegrationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Stored get(boolean exclusive) {
    return jdbc.queryForObject(
        "SELECT version,encrypted_payload FROM integration_settings WHERE id=1"
            + (exclusive ? " FOR UPDATE" : ""),
        (r, i) -> new Stored(r.getLong(1), r.getBytes(2)));
  }

  public long lockShared() {
    return jdbc.queryForObject(
        "SELECT version FROM integration_settings WHERE id=1 FOR SHARE", Long.class);
  }

  public long taskRevision(String table, UUID id) {
    if (!java.util.Set.of("agent_run", "kapso_message").contains(table))
      throw new IllegalArgumentException();
    return jdbc
        .query(
            "SELECT integration_revision FROM " + table + " WHERE id=?", (r, i) -> r.getLong(1), id)
        .stream()
        .findFirst()
        .orElseThrow(com.odontocare.shared.web.ApiException::notFound);
  }

  public void save(long version, byte[] encrypted) {
    jdbc.update(
        "UPDATE integration_settings SET version=?,encrypted_payload=?,updated_at=now() WHERE id=1",
        version,
        encrypted);
  }

  public void retirePending() {
    jdbc.update(
        "UPDATE agent_run SET"
            + " state='PAUSED',operational_result='CONFIGURATION_CHANGED',updated_at=now() WHERE"
            + " state IN ('QUEUED','PROCESSING')");
    jdbc.update(
        "UPDATE kapso_message SET status=CASE WHEN status='SENDING' THEN 'UNKNOWN' ELSE 'FAILED'"
            + " END,error_code='CONFIGURATION_CHANGED',error_message='La configuración cambió;"
            + " revisa la entrega antes de generar otro envío.',updated_at=now() WHERE"
            + " direction='OUTBOUND' AND status IN ('QUEUED','SENDING')");
    jdbc.update("UPDATE agent_supervision SET generation=generation+1,updated_at=now()");
    jdbc.update("UPDATE agent_proposal SET state='SUPERSEDED' WHERE state='PENDING'");
    jdbc.update("UPDATE agent_change_proposal SET state='SUPERSEDED' WHERE state='PENDING'");
    jdbc.update(
        "UPDATE agent_request_context SET"
            + " state='INFORMATION_PENDING',patient_id=NULL,patient_name='',relationship=NULL,verified_at=NULL,summary='',updated_at=now()"
            + " WHERE state<>'COMPLETED'");
  }
}
