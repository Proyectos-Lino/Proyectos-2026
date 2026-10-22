package com.odontocare.finance.service;

import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.model.FinanceOperation;
import com.odontocare.finance.repository.FinanceOperationRepository;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FinanceOperationService {
  private final FinanceOperationRepository operations;
  private final ChargeLedgerService ledger;
  private final ClinicalAccess access;

  public FinanceOperationService(
      FinanceOperationRepository operations, ChargeLedgerService ledger, ClinicalAccess access) {
    this.operations = operations;
    this.ledger = ledger;
    this.access = access;
  }

  public Optional<FinanceOperation> previous(UUID key, String action, Object body) {
    var prior = operations.findByRequestKey(key);
    if (prior.isPresent()
        && (!prior.get().getAction().equals(action)
            || !prior.get().getFingerprint().equals(ledger.fingerprint(body))))
      throw ApiException.conflict("La clave de solicitud ya corresponde a otra operación.");
    return prior;
  }

  public FinanceOperation record(
      UUID key, String action, Object body, UUID resultId, String reason) {
    var op = new FinanceOperation();
    op.setRequestKey(key);
    op.setAction(action);
    op.setFingerprint(ledger.fingerprint(body));
    op.setResultId(resultId);
    op.setReason(reason.strip());
    op.setActorName(access.actor().getDisplayName());
    return operations.saveAndFlush(op);
  }
}
