package com.odontocare.reports.repository;

import org.springframework.stereotype.Component;

@Component
public class FinancialReportSql {
  public String money(boolean expenses) {
    String kinds = expenses ? "'EXPENSE','EXPENSE_REVERSAL'" : "'PAYMENT','REFUND','REVERSAL'";
    String positive = expenses ? "EXPENSE" : "PAYMENT";
    String gross = expenses ? "spent" : "received";
    String correction = expenses ? "recovered" : "returned";
    return """
    SELECT m.id::text id,m.patient_id "patientId",NULL::uuid "dentistId",m.id "entityId",doc.id "documentId",
      m.occurred_on "date",coalesce(p.full_name,'') patient,coalesce(p.code,'') code,m.kind status,
      m.description description,m.currency currency,m.method method,m.reference reference,
      coalesce(m.receipt_code,'') receipt,m.category_name category,m.supplier supplier,
    """
        + " CASE WHEN m.kind='"
        + positive
        + "' THEN m.amount ELSE -m.amount END amount,"
        + " CASE WHEN m.kind='"
        + positive
        + "' THEN m.amount ELSE 0 END "
        + gross
        + ","
        + " CASE WHEN m.kind<>'"
        + positive
        + "' THEN m.amount ELSE 0 END "
        + correction
        + " "
        + """
        FROM money_movement m LEFT JOIN patient p ON p.id=m.patient_id
        LEFT JOIN LATERAL (SELECT id FROM financial_document WHERE movement_id=m.id AND generated ORDER BY created_at,id LIMIT 1) doc ON true
        WHERE m.occurred_on BETWEEN :from AND :to AND m.kind IN (
        """
        + kinds
        + ")";
  }

  public String charges() {
    return """
    SELECT c.id::text id,c.patient_id "patientId",coalesce(t.dentist_id,e.dentist_id) "dentistId",
      coalesce(root.plan_id,root.encounter_id,c.id) "entityId",NULL::uuid "documentId",
      c.created_at AT TIME ZONE :zone "date",p.full_name patient,p.code code,
      coalesce(t.dentist_name,r.dentist_name,'') dentist,c.kind status,c.description description,c.currency currency,c.amount amount,
      CASE WHEN c.original_id IS NULL THEN c.amount ELSE 0 END issued,
      CASE WHEN c.original_id IS NOT NULL THEN c.amount ELSE 0 END adjusted
    FROM charge_entry c JOIN patient p ON p.id=c.patient_id
    JOIN charge_entry root ON root.id=coalesce(c.original_id,c.id)
    LEFT JOIN treatment_plan t ON t.id=root.plan_id
    LEFT JOIN clinical_encounter e ON e.id=root.encounter_id
    LEFT JOIN encounter_revision r ON r.encounter_id=e.id AND r.number=1
    WHERE c.created_at>=:startAt AND c.created_at<:endAt
    """;
  }

  /**
   * One ledger cut shared by balance and installment reporting. Applications are not cash inflows.
   */
  public static final String CHARGE_CUT =
      """
      WITH charge_cut AS (
        SELECT c.id,c.patient_id,c.currency,c.description,
          c.amount+coalesce((SELECT sum(a.amount) FROM charge_entry a WHERE a.original_id=c.id AND a.created_at<:endAt),0) debt,
          coalesce((SELECT sum(a.amount) FROM money_application a JOIN money_movement m ON m.id=a.payment_id
            WHERE a.charge_id=c.id AND a.created_at<:endAt AND m.occurred_on<=:to),0) applied
        FROM charge_entry c WHERE c.original_id IS NULL AND c.created_at<:endAt)
      """;

  public String balances() {
    return CHARGE_CUT
        + """
        , debt_cut AS (SELECT patient_id,currency,sum(debt) debt,sum(applied) applied FROM charge_cut GROUP BY patient_id,currency),
        money_cut AS (SELECT patient_id,currency,sum(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END) received
          FROM money_movement WHERE patient_id IS NOT NULL AND created_at<:endAt AND occurred_on<=:to GROUP BY patient_id,currency),
        account_keys AS (SELECT patient_id,currency FROM debt_cut UNION SELECT patient_id,currency FROM money_cut)
        SELECT p.id::text||':'||k.currency id,p.id "patientId",NULL::uuid "dentistId",p.id "entityId",NULL::uuid "documentId",
          p.full_name patient,p.code code,k.currency currency,'' status,
          coalesce(d.debt,0) debt,coalesce(m.received,0) received,coalesce(d.applied,0) applied,
          coalesce(d.debt,0)-coalesce(d.applied,0) pending,coalesce(m.received,0)-coalesce(d.applied,0) advance
        FROM account_keys k JOIN patient p ON p.id=k.patient_id
        LEFT JOIN debt_cut d ON d.patient_id=k.patient_id AND d.currency=k.currency
        LEFT JOIN money_cut m ON m.patient_id=k.patient_id AND m.currency=k.currency
        """;
  }

  public String installments(boolean boundedDueDate) {
    return CHARGE_CUT
        + """
        , schedule_cut AS (SELECT DISTINCT ON(charge_id) * FROM installment_schedule WHERE created_at<:endAt ORDER BY charge_id,created_at DESC,id DESC),
        due_cut AS (SELECT i.*,s.patient_id,s.charge_id,s.total,c.currency,c.description,c.debt,c.applied,
          coalesce(sum(i.amount) OVER(PARTITION BY i.schedule_id ORDER BY i.position ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),0) preceding
          FROM installment i JOIN schedule_cut s ON s.id=i.schedule_id JOIN charge_cut c ON c.id=s.charge_id),
        due_paid AS (SELECT d.*,greatest(0,least(d.amount,d.applied-d.preceding)) paid FROM due_cut d)
        SELECT d.id::text id,d.patient_id "patientId",NULL::uuid "dentistId",d.charge_id "entityId",NULL::uuid "documentId",
          d.due_on "date",p.full_name patient,p.code code,d.description description,d.position position,d.currency currency,
          d.amount amount,d.paid paid,d.amount-d.paid pending,
          CASE WHEN d.total<>d.debt THEN 'REVIEW' WHEN d.paid=d.amount THEN 'PAID'
            WHEN d.due_on<:to THEN 'OVERDUE' WHEN d.paid>0 THEN 'PARTIAL' ELSE 'PENDING' END status
        FROM due_paid d JOIN patient p ON p.id=d.patient_id
        """
        + (boundedDueDate ? " WHERE d.due_on BETWEEN :from AND :to" : "");
  }
}
