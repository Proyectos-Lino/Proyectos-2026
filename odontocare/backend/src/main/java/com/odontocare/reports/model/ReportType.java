package com.odontocare.reports.model;

public enum ReportType {
  APPOINTMENTS("APPOINTMENTS_READ"),
  ABSENCES("APPOINTMENTS_READ"),
  TREATMENTS("PLANS_READ"),
  SERVICES("CLINICAL_READ"),
  COLLECTIONS("FINANCES_READ"),
  CHARGES("FINANCES_READ"),
  EXPENSES("FINANCES_READ"),
  BALANCES("FINANCES_READ"),
  INSTALLMENTS("FINANCES_READ");

  private final String permission;

  ReportType(String permission) {
    this.permission = permission;
  }

  public String permission() {
    return permission;
  }
}
