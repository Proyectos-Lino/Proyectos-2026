package com.odontocare.reports.repository;

import org.springframework.stereotype.Component;

/** Fixed projections: administrative columns only, never the clinical narrative. */
@Component
public class OperationalReportSql {
  public String appointments(boolean absences) {
    return """
    SELECT a.id::text id,a.patient_id "patientId",a.dentist_id "dentistId",a.id "entityId",NULL::uuid "documentId",
      a.starts_at AT TIME ZONE :zone "date",p.full_name patient,p.code code,a.dentist_name dentist,
      a.service_name description,a.status status,a.duration_minutes minutes
    FROM appointment a JOIN patient p ON p.id=a.patient_id
    WHERE a.starts_at>=:startAt AND a.starts_at<:endAt
    """
        + (absences ? " AND a.status='NO_SHOW'" : "");
  }

  public String treatments() {
    return """
    SELECT t.id::text id,t.patient_id "patientId",t.dentist_id "dentistId",t.id "entityId",NULL::uuid "documentId",
      t.created_at AT TIME ZONE :zone "date",t.patient_name patient,t.code code,t.dentist_name dentist,
      t.title description,t.status status,t.currency currency,
      CASE WHEN t.accepted_at<:endAt THEN t.accepted_total ELSE coalesce(i.budget,0) END budget,
      coalesce(ch.charged,0) charged,coalesce(i.sessions,0) "totalSessions",
      coalesce(s.completed,0) completed,coalesce(s.period_sessions,0) "periodSessions"
    FROM treatment_plan t
    LEFT JOIN LATERAL (SELECT sum(unit_price*quantity) budget,sum(sessions) sessions FROM treatment_item
      WHERE plan_id=t.id AND created_at<:endAt) i ON true
    LEFT JOIN LATERAL (SELECT sum(amount) charged FROM charge_entry WHERE plan_id=t.id AND created_at<:endAt) ch ON true
    LEFT JOIN LATERAL (SELECT sum(sessions) completed,sum(sessions) FILTER(WHERE created_at>=:startAt) period_sessions
      FROM treatment_session WHERE plan_id=t.id AND created_at<:endAt) s ON true
    WHERE t.created_at<:endAt AND (
      t.created_at>=:startAt OR (t.accepted_at>=:startAt AND t.accepted_at<:endAt)
      OR EXISTS(SELECT 1 FROM treatment_session x WHERE x.plan_id=t.id AND x.created_at>=:startAt AND x.created_at<:endAt)
      OR EXISTS(SELECT 1 FROM treatment_operation x WHERE x.plan_id=t.id AND x.created_at>=:startAt AND x.created_at<:endAt)
      OR EXISTS(SELECT 1 FROM charge_entry x WHERE x.plan_id=t.id AND x.created_at>=:startAt AND x.created_at<:endAt))
    """;
  }

  public String services() {
    return """
    SELECT e.id::text||':'||proc.ordinality::text id,e.patient_id "patientId",e.dentist_id "dentistId",
      e.id "entityId",NULL::uuid "documentId",e.attended_on "date",r.patient_name patient,r.patient_code code,
      r.dentist_name dentist,coalesce(proc.item->>'serviceName','') service,proc.item->>'description' description,
      CASE WHEN nullif(r.payload::jsonb->'content'->'procedures'->((proc.ordinality-1)::int)->>'planItemId','') IS NULL THEN 'INDIVIDUAL' ELSE 'PLAN' END status,
      CASE WHEN nullif(r.payload::jsonb->'content'->'procedures'->((proc.ordinality-1)::int)->>'planItemId','') IS NULL THEN (proc.item->>'quantity')::int ELSE 0 END units,
      CASE WHEN nullif(r.payload::jsonb->'content'->'procedures'->((proc.ordinality-1)::int)->>'planItemId','') IS NOT NULL THEN (proc.item->>'quantity')::int ELSE 0 END "planSessions",
      coalesce(proc.item->>'tooth','') tooth
    FROM clinical_encounter e JOIN encounter_revision r ON r.encounter_id=e.id AND r.number=e.revision
    CROSS JOIN LATERAL jsonb_array_elements(r.payload::jsonb->'procedures') WITH ORDINALITY proc(item,ordinality)
    WHERE e.status='FINAL' AND e.attended_on BETWEEN :from AND :to
    """;
  }
}
