package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashService {
  private final CashRegisterRepository register;
  private final CashSessionRepository sessions;
  private final MoneyMovementRepository movements;
  private final FinanceOperationService operations;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final Clock clock;
  private final FinancialDocumentService documents;

  public CashService(
      CashRegisterRepository register,
      CashSessionRepository sessions,
      MoneyMovementRepository movements,
      FinanceOperationService operations,
      ClinicalAccess access,
      AuditService audit,
      Clock clock,
      FinancialDocumentService documents) {
    this.register = register;
    this.sessions = sessions;
    this.movements = movements;
    this.operations = operations;
    this.access = access;
    this.audit = audit;
    this.clock = clock;
    this.documents = documents;
  }

  public void lock() {
    register.lockRegister().orElseThrow();
  }

  public CashSession forMovement(
      String currency, String method, LocalDate date, boolean outflow, BigDecimal amount) {
    lock();
    var session = sessions.findByClosedAtIsNull().orElse(null);
    if (method.equals("CASH")) {
      if (session == null) throw ApiException.conflict("Abre caja antes de registrar efectivo.");
      if (!session.getCurrency().equals(currency))
        throw ApiException.badRequest("El efectivo debe usar la moneda de la caja abierta.");
      if (!date.equals(access.today()))
        throw ApiException.badRequest("El efectivo se registra con la fecha actual.");
      if (outflow && expected(session).compareTo(amount) < 0)
        throw ApiException.badRequest("La caja no tiene efectivo suficiente.");
    }
    return session != null && session.getCurrency().equals(currency) ? session : null;
  }

  public BigDecimal expected(CashSession s) {
    return s.getOpening().add(movements.cashNet(s.getId()));
  }

  public CashResponse response(CashSession s) {
    var expected = s.getClosedAt() == null ? expected(s) : s.getExpected();
    return new CashResponse(
        s.getId(),
        s.getCurrency(),
        s.getOpening(),
        expected,
        s.getCounted(),
        s.getDifference(),
        movements.nonCashNet(s.getId()),
        s.getOpenedBy(),
        s.getClosedBy(),
        s.getCreatedAt(),
        s.getClosedAt(),
        s.getReason());
  }

  @Transactional
  public CashResponse current() {
    var s = sessions.findByClosedAtIsNull();
    return s.map(this::response).orElse(null);
  }

  @Transactional
  public CashResponse open(OpenCashRequest r) {
    FinanceMoney.requireCurrency(r.currency());
    lock();
    var prior = operations.previous(r.requestKey(), "CASH_OPEN", r);
    if (prior.isPresent())
      return response(sessions.findById(prior.get().getResultId()).orElseThrow());
    if (sessions.findByClosedAtIsNull().isPresent())
      throw ApiException.conflict("Ya hay una caja abierta.");
    var s = new CashSession();
    s.setCurrency(r.currency());
    s.setOpening(r.opening());
    s.setOpenedBy(access.actor().getDisplayName());
    s.setReason(r.reason().strip());
    sessions.saveAndFlush(s);
    operations.record(r.requestKey(), "CASH_OPEN", r, s.getId(), r.reason());
    audit.record("CASH_OPENED", "CASH", s.getId(), "Abrió caja");
    return response(s);
  }

  @Transactional
  public CashResponse close(UUID id, CloseCashRequest r) {
    lock();
    var prior = operations.previous(r.requestKey(), "CASH_CLOSE", List.of(id, r));
    if (prior.isPresent()) return response(sessions.findById(id).orElseThrow());
    var s = sessions.findById(id).orElseThrow(ApiException::notFound);
    if (s.getClosedAt() != null) throw ApiException.conflict("La caja ya está cerrada.");
    s.setExpected(expected(s));
    s.setCounted(r.counted());
    s.setDifference(r.counted().subtract(s.getExpected()));
    s.setClosedBy(access.actor().getDisplayName());
    s.setClosedAt(clock.instant());
    s.setReason(r.reason().strip());
    sessions.saveAndFlush(s);
    operations.record(r.requestKey(), "CASH_CLOSE", List.of(id, r), id, r.reason());
    documents.cashReport(s);
    audit.record("CASH_CLOSED", "CASH", id, "Cerró caja y conservó el arqueo");
    return response(s);
  }

  @Transactional
  public Download report(UUID id) {
    return documents.cashReport(id);
  }

  @Transactional
  public PageResponse<CashResponse> list(Boolean closed, PageQuery query) {
    var spec =
        SearchSpecifications.<CashSession>text(query.getSearch(), "openedBy", "closedBy", "reason");
    if (closed != null)
      spec =
          spec.and(
              (r, c, b) -> closed ? b.isNotNull(r.get("closedAt")) : b.isNull(r.get("closedAt")));
    return PageResponse.of(
        sessions
            .findAll(spec, query.pageable(Map.of("name", "openedBy", "createdAt", "createdAt")))
            .map(this::response));
  }
}
