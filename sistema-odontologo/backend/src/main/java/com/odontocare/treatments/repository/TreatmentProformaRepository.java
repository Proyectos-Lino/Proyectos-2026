package com.odontocare.treatments.repository;

import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class TreatmentProformaRepository {
  public record Document(
      UUID id,
      UUID planId,
      long planVersion,
      String planStatus,
      String code,
      String createdBy,
      int byteSize,
      Instant createdAt) {}

  private final JdbcTemplate jdbc;

  public TreatmentProformaRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Document> ROW =
      (r, i) ->
          new Document(
              r.getObject("id", UUID.class),
              r.getObject("plan_id", UUID.class),
              r.getLong("plan_version"),
              r.getString("plan_status"),
              r.getString("code"),
              r.getString("created_by"),
              r.getInt("byte_size"),
              r.getTimestamp("created_at").toInstant());

  public Optional<Document> version(UUID plan, long version) {
    return jdbc
        .query(
            "SELECT * FROM treatment_proforma WHERE plan_id=? AND plan_version=?",
            ROW,
            plan,
            version)
        .stream()
        .findFirst();
  }

  public Document save(
      UUID plan, long version, String state, String code, String actor, byte[] data, Instant now) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " treatment_proforma(id,plan_id,plan_version,plan_status,code,created_by,byte_size,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        plan,
        version,
        state,
        code,
        actor,
        data.length,
        Timestamp.from(now));
    jdbc.update(
        "INSERT INTO treatment_proforma_content(document_id,content) VALUES(?,?)", id, data);
    return version(plan, version).orElseThrow();
  }

  public Document get(UUID plan, UUID id) {
    return jdbc
        .query("SELECT * FROM treatment_proforma WHERE plan_id=? AND id=?", ROW, plan, id)
        .stream()
        .findFirst()
        .orElseThrow(ApiException::notFound);
  }

  public byte[] content(UUID id) {
    return jdbc.queryForObject(
        "SELECT content FROM treatment_proforma_content WHERE document_id=?", byte[].class, id);
  }

  public PageResponse<Document> list(UUID plan, PageQuery query) {
    if (!Set.of("name", "createdAt", "version").contains(query.getSort()))
      throw ApiException.badRequest("Ordenación no permitida.");
    var args = new ArrayList<Object>();
    args.add(plan);
    String where =
        " WHERE plan_id=? AND strpos(lower(code||' '||created_by||' '||plan_status),?)>0";
    args.add(query.getSearch().toLowerCase(Locale.ROOT));
    long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM treatment_proforma" + where, Long.class, args.toArray());
    args.add(query.getSize());
    args.add((long) query.getPage() * query.getSize());
    var rows =
        jdbc.query(
            "SELECT * FROM treatment_proforma"
                + where
                + " ORDER BY "
                + (query.getSort().equals("version") ? "plan_version" : "created_at")
                + (query.getDirection().equals("asc") ? " ASC" : " DESC")
                + ",id LIMIT ? OFFSET ?",
            ROW,
            args.toArray());
    return new PageResponse<>(
        rows,
        query.getPage(),
        query.getSize(),
        total,
        (int) ((total + query.getSize() - 1) / query.getSize()));
  }
}
