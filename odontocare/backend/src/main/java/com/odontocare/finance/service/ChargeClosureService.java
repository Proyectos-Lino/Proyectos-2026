package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeClosureService {
  private final ChargeEntryRepository charges;
  private final PatientRepository patients;
  private final ChargeStateRepository states;
  private final MoneyApplicationRepository applications;
  private final FinanceOperationService operations;
  private final AccountQueryService queries;
  private final AuditService audit;

  public ChargeClosureService(
      ChargeEntryRepository charges,
      PatientRepository patients,
      ChargeStateRepository states,
      MoneyApplicationRepository applications,
      FinanceOperationService operations,
      AccountQueryService queries,
      AuditService audit) {
    this.charges = charges;
    this.patients = patients;
    this.states = states;
    this.applications = applications;
    this.operations = operations;
    this.queries = queries;
    this.audit = audit;
  }

  @Transactional
  public OpenCharge change(UUID id, ChargeStateRequest r, boolean close) {
    var charge = charges.findById(id).orElseThrow(ApiException::notFound);
    patients.lockById(charge.getPatientId()).orElseThrow();
    if (charge.getOriginalId() != null)
      throw ApiException.badRequest("Selecciona el cargo original.");
    String action = close ? "CHARGE_CLOSED" : "CHARGE_REOPENED";
    var body = List.of(id, r, close);
    if (operations.previous(r.requestKey(), action, body).isPresent()) return queries.charge(id);
    if (close && charges.originDebt(id).compareTo(applications.chargeApplied(id)) != 0)
      throw ApiException.badRequest("Solo puedes finalizar un cargo sin saldo pendiente.");
    if (states.closed(id) != close) {
      states.setClosed(id, close);
      audit.record(action, "CHARGE", id, r.reason());
    }
    operations.record(r.requestKey(), action, body, id, r.reason());
    return queries.charge(id);
  }
}
