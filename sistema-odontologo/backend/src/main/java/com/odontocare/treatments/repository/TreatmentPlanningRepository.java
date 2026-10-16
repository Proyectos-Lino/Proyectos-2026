package com.odontocare.treatments.repository;

import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.dto.ScheduledSessionContracts.Session;
import com.odontocare.treatments.dto.TreatmentContracts.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class TreatmentPlanningRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public TreatmentPlanningRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public String encode(Scheduling scheduling) {
    return mapper.writeValueAsString(scheduling == null ? Scheduling.none() : scheduling);
  }

  public int duration(UUID id) {
    return jdbc.queryForObject(
        "SELECT duration_minutes FROM treatment_item WHERE id=?", Integer.class, id);
  }

  public Scheduling scheduling(UUID id) {
    return mapper.readValue(
        jdbc.queryForObject(
            "SELECT scheduling::text FROM treatment_item WHERE id=?", String.class, id),
        Scheduling.class);
  }

  public PaymentAgreement agreement(UUID id) {
    return mapper.readValue(
        jdbc.queryForObject(
            "SELECT payment_agreement::text FROM treatment_plan WHERE id=?", String.class, id),
        PaymentAgreement.class);
  }

  public void agreement(UUID id, PaymentAgreement value) {
    jdbc.update(
        "UPDATE treatment_plan SET payment_agreement=?::jsonb WHERE id=?",
        mapper.writeValueAsString(value),
        id);
  }

  public UUID payment(UUID id) {
    return jdbc.queryForObject(
        "SELECT initial_payment_id FROM treatment_plan WHERE id=?", UUID.class, id);
  }

  public void payment(UUID id, UUID payment) {
    jdbc.update("UPDATE treatment_plan SET initial_payment_id=? WHERE id=?", payment, id);
  }

  public void create(UUID plan, UUID item, int number, LocalDate date, int duration) {
    jdbc.update(
        "INSERT INTO"
            + " treatment_scheduled_session(id,plan_id,item_id,session_number,planned_on,duration_minutes)"
            + " VALUES(?,?,?,?,?,?) ON CONFLICT(item_id,session_number) DO NOTHING",
        UUID.randomUUID(),
        plan,
        item,
        number,
        date,
        duration);
  }

  private static final String SELECT =
      "SELECT"
          + " s.*,p.code,p.title,p.patient_id,p.patient_name,p.dentist_id,p.dentist_name,i.description,i.tooth,i.service_id,i.sessions"
          + " FROM treatment_scheduled_session s JOIN treatment_plan p ON p.id=s.plan_id JOIN"
          + " treatment_item i ON i.id=s.item_id";
  private static final RowMapper<Session> ROW =
      (r, n) ->
          new Session(
              r.getObject("id", UUID.class),
              r.getObject("plan_id", UUID.class),
              r.getString("code"),
              r.getString("title"),
              r.getObject("item_id", UUID.class),
              r.getString("description"),
              (Integer) r.getObject("tooth"),
              r.getObject("service_id", UUID.class),
              r.getObject("patient_id", UUID.class),
              r.getString("patient_name"),
              r.getObject("dentist_id", UUID.class),
              r.getString("dentist_name"),
              r.getInt("session_number"),
              r.getInt("sessions"),
              r.getObject("planned_on", LocalDate.class),
              r.getInt("duration_minutes"),
              r.getString("status"),
              r.getObject("appointment_id", UUID.class),
              r.getLong("version"),
              r.getString("code")
                  + " · "
                  + r.getString("description")
                  + " · Sesión "
                  + r.getInt("session_number")
                  + " de "
                  + r.getInt("sessions"));

  public Optional<Session> session(UUID id, boolean lock) {
    return jdbc.query(SELECT + " WHERE s.id=?" + (lock ? " FOR UPDATE OF s" : ""), ROW, id).stream()
        .findFirst();
  }

  public Optional<Session> firstPending(UUID item) {
    return jdbc
        .query(
            SELECT
                + " WHERE s.item_id=? AND s.status='PENDING' ORDER BY s.session_number LIMIT 1 FOR"
                + " UPDATE OF s",
            ROW,
            item)
        .stream()
        .findFirst();
  }

  public record CoordinationSummary(long upcoming, long overdue, long undated) {}

  public CoordinationSummary coordination(UUID dentist, LocalDate today) {
    return jdbc.queryForObject(
        "SELECT count(*) FILTER(WHERE s.planned_on BETWEEN ? AND ?) upcoming,count(*) FILTER(WHERE"
            + " s.planned_on<?) overdue,count(*) FILTER(WHERE s.planned_on IS NULL) undated FROM"
            + " treatment_scheduled_session s JOIN treatment_plan p ON p.id=s.plan_id WHERE"
            + " s.status='PENDING' AND p.status IN ('ACCEPTED','IN_PROGRESS') AND (?::uuid IS NULL"
            + " OR p.dentist_id=?::uuid)",
        (r, i) ->
            new CoordinationSummary(
                r.getLong("upcoming"), r.getLong("overdue"), r.getLong("undated")),
        today,
        today.plusDays(7),
        today,
        dentist,
        dentist);
  }

  public void ensureRemaining(com.odontocare.treatments.model.PlanItem item, long completed) {
    if (jdbc.queryForObject(
            "SELECT count(*) FROM treatment_scheduled_session WHERE item_id=?",
            Integer.class,
            item.getId())
        == 0)
      for (int index = (int) completed + 1; index <= item.getSessions(); index++)
        create(item.getPlanId(), item.getId(), index, null, duration(item.getId()));
  }

  public PageResponse<Session> list(
      UUID patient,
      UUID dentist,
      UUID plan,
      LocalDate from,
      LocalDate to,
      String status,
      PageQuery query) {
    return list(patient, dentist, plan, from, to, status, false, query);
  }

  public PageResponse<Session> list(
      UUID patient,
      UUID dentist,
      UUID plan,
      LocalDate from,
      LocalDate to,
      String status,
      boolean undated,
      PageQuery query) {
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.badRequest("Intervalo de fechas no válido.");
    if (status != null
        && !Set.of("PENDING", "SCHEDULED", "COMPLETED", "CANCELLED").contains(status))
      throw ApiException.badRequest("Estado de sesión no válido.");
    if (!Set.of("name", "date", "plannedOn").contains(query.getSort()))
      throw ApiException.badRequest("Ordenación no permitida.");
    var conditions = new StringBuilder(" WHERE true");
    if (undated) conditions.append(" AND s.planned_on IS NULL");
    var args = new ArrayList<Object>();
    if ("PENDING".equals(status)) conditions.append(" AND p.status IN ('ACCEPTED','IN_PROGRESS')");
    for (var value :
        List.of(
            new Object[] {"p.patient_id", patient},
            new Object[] {"p.dentist_id", dentist},
            new Object[] {"s.plan_id", plan},
            new Object[] {"s.status", status}))
      if (value[1] != null) {
        conditions.append(" AND ").append(value[0]).append("=?");
        args.add(value[1]);
      }
    if (from != null) {
      conditions.append(" AND s.planned_on>=?");
      args.add(from);
    }
    if (to != null) {
      conditions.append(" AND s.planned_on<=?");
      args.add(to);
    }
    for (String word : query.getSearch().toLowerCase(Locale.ROOT).split("\\s+"))
      if (!word.isBlank()) {
        conditions.append(
            " AND strpos(lower(p.patient_name||' '||p.dentist_name||' '||p.title||' '||p.code||'"
                + " '||i.description),?)>0");
        args.add(word);
      }
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM (" + SELECT + conditions + ") q", Long.class, args.toArray());
    var pageArgs = new ArrayList<>(args);
    pageArgs.add(query.getSize());
    pageArgs.add(Math.multiplyExact((long) query.getPage(), query.getSize()));
    var rows =
        jdbc.query(
            SELECT
                + conditions
                + " ORDER BY "
                + (query.getSort().equals("name") ? "p.patient_name" : "s.planned_on")
                + (query.getDirection().equals("desc") ? " DESC" : " ASC")
                + " NULLS LAST,s.id LIMIT ? OFFSET ?",
            ROW,
            pageArgs.toArray());
    return new PageResponse<>(
        rows,
        query.getPage(),
        query.getSize(),
        count,
        (int) ((count + query.getSize() - 1) / query.getSize()));
  }

  public void assigned(UUID id, UUID appointment) {
    jdbc.update(
        "UPDATE treatment_scheduled_session SET"
            + " appointment_id=?,status='SCHEDULED',version=version+1,updated_at=now() WHERE id=?",
        appointment,
        id);
  }

  public void date(UUID id, LocalDate date) {
    jdbc.update(
        "UPDATE treatment_scheduled_session SET planned_on=?,version=version+1,updated_at=now()"
            + " WHERE id=?",
        date,
        id);
  }

  public void appointmentState(UUID appointment, String status, LocalDate date) {
    if (status.equals("CANCELLED") || status.equals("NO_SHOW"))
      jdbc.update(
          "UPDATE treatment_scheduled_session s SET status=CASE WHEN p.status='CANCELLED' THEN"
              + " 'CANCELLED' ELSE 'PENDING'"
              + " END,appointment_id=NULL,version=s.version+1,updated_at=now() FROM treatment_plan"
              + " p WHERE p.id=s.plan_id AND s.appointment_id=?",
          appointment);
    else
      jdbc.update(
          "UPDATE treatment_scheduled_session SET planned_on=?,version=version+1,updated_at=now()"
              + " WHERE appointment_id=?",
          date,
          appointment);
  }

  public List<UUID> futureAppointments(UUID plan, Instant now) {
    return jdbc.queryForList(
        "SELECT a.id FROM treatment_scheduled_session s JOIN appointment a ON a.id=s.appointment_id"
            + " WHERE s.plan_id=? AND a.starts_at>=? AND a.status IN"
            + " ('RESERVED','CONFIRMED','WAITING') ORDER BY a.id",
        UUID.class,
        plan,
        Timestamp.from(now));
  }

  public long appointmentVersion(UUID id) {
    return jdbc.queryForObject("SELECT version FROM appointment WHERE id=?", Long.class, id);
  }

  public void cancelPending(UUID plan) {
    jdbc.update(
        "UPDATE treatment_scheduled_session SET"
            + " status='CANCELLED',version=version+1,updated_at=now() WHERE plan_id=? AND"
            + " status='PENDING'",
        plan);
  }

  public void complete(UUID item, UUID appointment, int count) {
    if (appointment != null
        && jdbc.queryForObject(
                "SELECT count(*) FROM treatment_scheduled_session WHERE item_id=? AND"
                    + " appointment_id=?",
                Integer.class,
                item,
                appointment)
            > 0) {
      if (count != 1)
        throw ApiException.badRequest("La cita corresponde a una sola sesión del tratamiento.");
      int changed =
          jdbc.update(
              "UPDATE treatment_scheduled_session SET"
                  + " status='COMPLETED',version=version+1,updated_at=now() WHERE item_id=? AND"
                  + " appointment_id=? AND status='SCHEDULED'",
              item,
              appointment);
      if (changed != 1) throw ApiException.conflict("La sesión de esta cita ya está cerrada.");
    } else if (jdbc.queryForObject(
            "SELECT count(*) FROM treatment_scheduled_session WHERE item_id=?", Integer.class, item)
        > 0) {
      int changed =
          jdbc.update(
              "UPDATE treatment_scheduled_session SET"
                  + " status='COMPLETED',version=version+1,updated_at=now() WHERE id IN (SELECT id"
                  + " FROM treatment_scheduled_session WHERE item_id=? AND status='PENDING' ORDER"
                  + " BY session_number LIMIT ? FOR UPDATE)",
              item,
              count);
      if (changed != count)
        throw ApiException.conflict(
            "Hay sesiones con cita: atiende desde su cita o libera su horario antes de registrarlas"
                + " sin cita.");
    }
  }
}
