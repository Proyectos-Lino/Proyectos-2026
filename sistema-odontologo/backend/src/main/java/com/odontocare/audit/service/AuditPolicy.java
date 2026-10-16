package com.odontocare.audit.service;

import java.util.Set;

/** Operational changes and sensitive downloads; routine browsing stays out of audit. */
public final class AuditPolicy {
  private AuditPolicy() {}

  public static final Set<String> OMITTED =
      Set.of(
          "ACCOUNT_READ",
          "MONEY_READ",
          "CHARGES_READ",
          "DEBT_READ",
          "DASHBOARD_READ",
          "REPORT_READ",
          "ENCOUNTER_READ",
          "ENCOUNTER_HISTORY_READ",
          "DOCUMENT_LIST_READ",
          "CONSENT_READ",
          "PLANS_READ",
          "PLAN_READ",
          "PLAN_SESSIONS_READ",
          "CLINICAL_STATE_READ",
          "BACKGROUND_READ",
          "ODONTOGRAM_READ",
          "CASH_REPORT_READ",
          "FINANCIAL_DOCUMENT_LIST_READ",
          "ENCOUNTER_DRAFT_UPDATED",
          "LOGIN",
          "LOGOUT",
          "AGENT_QUEUED",
          "AGENT_COMPLETED",
          "AGENT_REPLY_QUEUED",
          "AGENT_TOOL_USED",
          "AGENT_PREVIEW",
          "AGENT_PROPOSAL",
          "AGENT_CHANGE_PROPOSED",
          "KAPSO_MESSAGE_RECEIVED",
          "KAPSO_DELIVERY_STATUS",
          "KAPSO_SEND_RESULT",
          "KAPSO_REPLY_QUEUED",
          "WHATSAPP_MESSAGE_RECEIVED",
          "WHATSAPP_DELIVERY_STATUS",
          "WHATSAPP_SEND_RESULT",
          "WHATSAPP_REPLY_QUEUED");

  public static boolean essential(String action) {
    return !OMITTED.contains(action) && !action.endsWith("_READ");
  }
}
