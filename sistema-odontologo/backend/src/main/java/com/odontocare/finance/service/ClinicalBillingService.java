package com.odontocare.finance.service;

import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.model.Encounter;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.service.PlanProgressService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalBillingService {
  private final ChargeLedgerService ledger;
  private final PlanProgressService progress;
  private final DentalServiceRepository services;
  private final InstallationProfileRepository profiles;

  public ClinicalBillingService(
      ChargeLedgerService ledger,
      PlanProgressService progress,
      DentalServiceRepository services,
      InstallationProfileRepository profiles) {
    this.ledger = ledger;
    this.progress = progress;
    this.services = services;
    this.profiles = profiles;
  }

  public BigDecimal unitPrice(Procedure procedure) {
    if (procedure.unitPrice() != null) return procedure.unitPrice();
    return procedure.serviceId() == null
        ? BigDecimal.ZERO
        : services.findById(procedure.serviceId()).orElseThrow(ApiException::notFound).getPrice();
  }

  @Transactional
  public void finalizeProcedures(Encounter encounter, List<Procedure> procedures) {
    String currency = profiles.findById((short) 1).orElseThrow().getCurrency();
    for (int index = 0; index < procedures.size(); index++) {
      var procedure = procedures.get(index);
      if (procedure.planItemId() != null) progress.complete(encounter, procedure, index);
      else
        ledger.issue(
            encounter.getPatientId(),
            null,
            null,
            encounter.getId(),
            "encounter:" + encounter.getId() + ":" + index,
            procedure.description(),
            currency,
            unitPrice(procedure),
            procedure.quantity(),
            "Servicio realizado en atención finalizada");
    }
  }
}
