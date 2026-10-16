package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {
  private final ExpenseCategoryRepository categories;
  private final MoneyMovementRepository movements;
  private final CashService cash;
  private final FinanceOperationService operations;
  private final AccountQueryService queries;
  private final ClinicalAccess access;
  private final AuditService audit;

  public ExpenseService(
      ExpenseCategoryRepository categories,
      MoneyMovementRepository movements,
      CashService cash,
      FinanceOperationService operations,
      AccountQueryService queries,
      ClinicalAccess access,
      AuditService audit) {
    this.categories = categories;
    this.movements = movements;
    this.cash = cash;
    this.operations = operations;
    this.queries = queries;
    this.access = access;
    this.audit = audit;
  }

  @Transactional
  public PageResponse<CategoryResponse> categories(Boolean active, PageQuery query) {
    var spec =
        SearchSpecifications.<ExpenseCategory>text(query.getSearch(), "name")
            .and(SearchSpecifications.equal("active", active));
    return PageResponse.of(
        categories
            .findAll(spec, query.pageable(Map.of("name", "name", "createdAt", "createdAt")))
            .map(this::category));
  }

  private CategoryResponse category(ExpenseCategory c) {
    return new CategoryResponse(c.getId(), c.getVersion(), c.getName(), c.getActive());
  }

  @Transactional
  public CategoryResponse saveCategory(UUID id, CategoryRequest r) {
    var c =
        id == null
            ? new ExpenseCategory()
            : categories.findById(id).orElseThrow(ApiException::notFound);
    if (id != null) c.checkVersion(r.version());
    c.setName(r.name().strip());
    c.setActive(r.active());
    categories.saveAndFlush(c);
    audit.record(
        "EXPENSE_CATEGORY_SAVED", "EXPENSE_CATEGORY", c.getId(), "Configuró categoría de egresos");
    return category(c);
  }

  @Transactional
  public MovementResponse register(ExpenseRequest r) {
    FinanceMoney.requireCurrency(r.currency());
    cash.lock();
    var prior = operations.previous(r.requestKey(), "EXPENSE", r);
    if (prior.isPresent())
      return queries.response(movements.findById(prior.get().getResultId()).orElseThrow());
    if (r.occurredOn().isAfter(access.today()))
      throw ApiException.badRequest("La fecha no puede estar en el futuro.");
    var cat = categories.findById(r.categoryId()).orElseThrow(ApiException::notFound);
    if (!cat.getActive()) throw ApiException.badRequest("Selecciona una categoría activa.");
    var session = cash.forMovement(r.currency(), r.method(), r.occurredOn(), true, r.amount());
    var m = new MoneyMovement();
    m.setKind("EXPENSE");
    m.setCategoryId(cat.getId());
    m.setCategoryName(cat.getName());
    m.setCurrency(r.currency());
    m.setAmount(r.amount());
    m.setOccurredOn(r.occurredOn());
    m.setMethod(r.method());
    m.setReference(r.reference().strip());
    m.setDescription(r.description().strip());
    m.setSupplier(r.supplier().strip());
    m.setActorName(access.actor().getDisplayName());
    if (session != null) m.setCashSessionId(session.getId());
    movements.saveAndFlush(m);
    operations.record(r.requestKey(), "EXPENSE", r, m.getId(), r.description());
    audit.record("EXPENSE_REGISTERED", "EXPENSE", m.getId(), "Registró egreso");
    return queries.response(m);
  }

  @Transactional
  public MovementResponse reverse(UUID id, CorrectionRequest r) {
    cash.lock();
    var prior = operations.previous(r.requestKey(), "EXPENSE_REVERSAL", List.of(id, r));
    if (prior.isPresent())
      return queries.response(movements.findById(prior.get().getResultId()).orElseThrow());
    var original = movements.findById(id).orElseThrow(ApiException::notFound);
    if (!original.getKind().equals("EXPENSE")
        || movements.returned(id).signum() != 0
        || r.amount().compareTo(original.getAmount()) != 0)
      throw ApiException.badRequest("Solo se revierte un egreso vigente por su importe completo.");
    if (r.occurredOn().isAfter(access.today()))
      throw ApiException.badRequest("La fecha no puede estar en el futuro.");
    var session =
        cash.forMovement(
            original.getCurrency(), original.getMethod(), r.occurredOn(), false, r.amount());
    var m = new MoneyMovement();
    m.setKind("EXPENSE_REVERSAL");
    m.setOriginalId(id);
    m.setCategoryId(original.getCategoryId());
    m.setCategoryName(original.getCategoryName());
    m.setCurrency(original.getCurrency());
    m.setAmount(original.getAmount());
    m.setOccurredOn(r.occurredOn());
    m.setMethod(original.getMethod());
    m.setReference(original.getReference());
    m.setDescription(original.getDescription());
    m.setSupplier(original.getSupplier());
    m.setActorName(access.actor().getDisplayName());
    if (session != null) m.setCashSessionId(session.getId());
    movements.saveAndFlush(m);
    operations.record(r.requestKey(), "EXPENSE_REVERSAL", List.of(id, r), m.getId(), r.reason());
    audit.record("EXPENSE_REVERSED", "EXPENSE", m.getId(), r.reason());
    return queries.response(m);
  }
}
