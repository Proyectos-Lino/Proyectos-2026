package com.odontocare.agent.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentCatalogRepository {
  private final JdbcTemplate jdbc;

  public AgentCatalogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> services(String search, int page) {
    return jdbc.queryForList(
        "SELECT s.id,s.name,s.price,s.duration_minutes FROM dental_service s JOIN service_category"
            + " c ON c.id=s.category_id WHERE s.active AND s.bookable_by_agent AND c.active AND"
            + " lower(s.name) LIKE ? ESCAPE '!' ORDER BY s.name,s.id LIMIT 5 OFFSET ?",
        pattern(search),
        page * 5);
  }

  public List<Map<String, Object>> dentists(UUID service, UUID dentist) {
    return dentists(service, dentist, "");
  }

  public List<Map<String, Object>> dentists(UUID service, UUID dentist, String name) {
    var parameters = new ArrayList<Object>();
    parameters.add(service);
    if (dentist != null) parameters.add(dentist);
    var filter = new StringBuilder();
    for (String word : name.strip().split("\\s+")) {
      if (word.isBlank()) continue;
      filter.append(" AND lower(d.full_name) LIKE ? ESCAPE '!'");
      parameters.add(pattern(word));
    }
    return jdbc.queryForList(
        "SELECT d.id,d.full_name FROM dentist d JOIN user_account u ON u.id=d.user_id JOIN"
            + " dentist_service ds ON ds.dentist_id=d.id WHERE d.active AND u.active AND"
            + " ds.service_id=?"
            + (dentist == null ? "" : " AND d.id=?")
            + filter
            + " ORDER BY d.full_name,d.id LIMIT 5",
        parameters.toArray());
  }

  public List<Map<String, Object>> patients(String phone, String search, int page) {
    return jdbc.queryForList(
        "SELECT DISTINCT p.id,p.full_name,p.code FROM patient p JOIN patient_contact c ON"
            + " c.patient_id=p.id WHERE p.active AND c.phone=? AND lower(p.full_name) LIKE ? ESCAPE"
            + " '!' ORDER BY p.full_name,p.id LIMIT 5 OFFSET ?",
        phone,
        pattern(search),
        page * 5);
  }

  public Optional<Map<String, Object>> patient(String phone, UUID id) {
    return jdbc
        .queryForList(
            "SELECT DISTINCT p.id,p.full_name FROM patient p JOIN patient_contact c ON"
                + " c.patient_id=p.id WHERE p.active AND c.phone=? AND p.id=?",
            phone,
            id)
        .stream()
        .findFirst();
  }

  public List<Map<String, Object>> exactPatients(String phone, String name) {
    return jdbc.queryForList(
        "SELECT DISTINCT p.id,p.full_name FROM patient p JOIN patient_contact c ON"
            + " c.patient_id=p.id WHERE p.active AND c.phone=? AND"
            + " regexp_replace(translate(lower(p.full_name),'áéíóúüñ','aeiouun'),'[^a-z0-9]+','"
            + " ','g')=? ORDER BY p.id LIMIT 5",
        phone,
        name);
  }

  public Optional<Map<String, Object>> service(UUID id) {
    return jdbc
        .queryForList(
            "SELECT s.id,s.name,s.price,s.duration_minutes FROM dental_service s JOIN"
                + " service_category c ON c.id=s.category_id WHERE s.id=? AND s.active AND"
                + " s.bookable_by_agent AND c.active",
            id)
        .stream()
        .findFirst();
  }

  private String pattern(String text) {
    return "%"
        + text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }
}
