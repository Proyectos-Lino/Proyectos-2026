package com.odontocare.installation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "installation_profile")
public class InstallationProfile {
  @Id private Short id;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(name = "time_zone", nullable = false, length = 60)
  private String timeZone;

  @Column(nullable = false, columnDefinition = "char(3)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "legal_name", nullable = false)
  private String legalName = "";

  @Column(name = "address", nullable = false)
  private String address = "";

  @Column(name = "phone", nullable = false)
  private String phone = "";

  @Column(name = "email", nullable = false)
  private String email = "";

  @Column(name = "brand_color", nullable = false)
  private String brandColor = "#215e4d";

  @Column(name = "accent_color", nullable = false)
  private String accentColor = "#edf2e9";

  @Column(name = "date_format", nullable = false)
  private String dateFormat = "DMY";

  @Column(name = "document_header", nullable = false)
  private String documentHeader = "";

  @Column(name = "document_footer", nullable = false)
  private String documentFooter = "";

  @Column(name = "appointment_instructions", nullable = false)
  private String appointmentInstructions = "";

  @Column(name = "patient_prefix", nullable = false)
  private String patientPrefix = "PAC";

  @Column(name = "patient_next_number", nullable = false)
  private int patientNextNumber = 1;

  @Column(name = "receipt_prefix", nullable = false)
  private String receiptPrefix = "REC";

  @Column(name = "receipt_next_number", nullable = false)
  private int receiptNextNumber = 1;

  @Column(name = "budget_prefix", nullable = false)
  private String budgetPrefix = "PRE";

  @Column(name = "budget_next_number", nullable = false)
  private int budgetNextNumber = 1;

  @Column(name = "minimum_lead_minutes", nullable = false)
  private int minimumLeadMinutes = 120;

  @Column(name = "appointment_gap_minutes", nullable = false)
  private int appointmentGapMinutes = 0;

  @Column(name = "logo_revision", nullable = false)
  private int logoRevision;

  public int getLogoRevision() {
    return logoRevision;
  }

  public void advanceLogoRevision() {
    logoRevision++;
  }

  @jakarta.persistence.Version private long version;

  protected InstallationProfile() {}

  public String getDisplayName() {
    return displayName;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public String getCurrency() {
    return currency;
  }

  public long getVersion() {
    return version;
  }

  public void setDisplayName(String value) {
    displayName = value;
  }

  public void setTimeZone(String value) {
    timeZone = value;
  }

  public void setCurrency(String value) {
    currency = value;
  }

  public String getLegalName() {
    return legalName;
  }

  public void setLegalName(String value) {
    legalName = value;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String value) {
    address = value;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String value) {
    phone = value;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    email = value;
  }

  public String getBrandColor() {
    return brandColor;
  }

  public void setBrandColor(String value) {
    brandColor = value;
  }

  public String getAccentColor() {
    return accentColor;
  }

  public void setAccentColor(String value) {
    accentColor = value;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String value) {
    dateFormat = value;
  }

  public String getDocumentHeader() {
    return documentHeader;
  }

  public void setDocumentHeader(String value) {
    documentHeader = value;
  }

  public String getDocumentFooter() {
    return documentFooter;
  }

  public void setDocumentFooter(String value) {
    documentFooter = value;
  }

  public String getAppointmentInstructions() {
    return appointmentInstructions;
  }

  public void setAppointmentInstructions(String value) {
    appointmentInstructions = value;
  }

  public String getPatientPrefix() {
    return patientPrefix;
  }

  public void setPatientPrefix(String value) {
    patientPrefix = value;
  }

  public int getPatientNextNumber() {
    return patientNextNumber;
  }

  public void setPatientNextNumber(int value) {
    patientNextNumber = value;
  }

  public String getReceiptPrefix() {
    return receiptPrefix;
  }

  public void setReceiptPrefix(String value) {
    receiptPrefix = value;
  }

  public int getReceiptNextNumber() {
    return receiptNextNumber;
  }

  public void setReceiptNextNumber(int value) {
    receiptNextNumber = value;
  }

  public String getBudgetPrefix() {
    return budgetPrefix;
  }

  public void setBudgetPrefix(String value) {
    budgetPrefix = value;
  }

  public int getBudgetNextNumber() {
    return budgetNextNumber;
  }

  public void setBudgetNextNumber(int value) {
    budgetNextNumber = value;
  }

  public int getMinimumLeadMinutes() {
    return minimumLeadMinutes;
  }

  public void setMinimumLeadMinutes(int value) {
    minimumLeadMinutes = value;
  }

  public int getAppointmentGapMinutes() {
    return appointmentGapMinutes;
  }

  public void setAppointmentGapMinutes(int value) {
    appointmentGapMinutes = value;
  }
}
