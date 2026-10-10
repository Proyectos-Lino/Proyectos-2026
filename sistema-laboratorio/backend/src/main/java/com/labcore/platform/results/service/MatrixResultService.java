package com.labcore.platform.results;

import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MatrixResultService {
  private static final String PARTIAL_SAVE_REASON = "Guardado parcial de la evaluación";
  private static final String COMPLETION_REASON = "Cierre de la evaluación";
  private final ResultMatrixRepository matrixRepository;
  private final ResultMatrixColumnRepository columnRepository;
  private final ResultMatrixCellRepository cellRepository;
  private final ResultMatrixChangeLogRepository changeRepository;
  private final ApplicationUserRepository userRepository;

  public MatrixResultService(
      ResultMatrixRepository matrixRepository,
      ResultMatrixColumnRepository columnRepository,
      ResultMatrixCellRepository cellRepository,
      ResultMatrixChangeLogRepository changeRepository,
      ApplicationUserRepository userRepository) {
    this.matrixRepository = matrixRepository;
    this.columnRepository = columnRepository;
    this.cellRepository = cellRepository;
    this.changeRepository = changeRepository;
    this.userRepository = userRepository;
  }

  public boolean save(
      ResultEntry entry,
      ResultDtos.MatrixRequest request,
      TechnicalCatalogDtos.TemplateResponse template,
      String revisionReason,
      UUID actorId,
      String actorRole) {
    validate(request, template, true);
    ResultMatrix matrix = matrixRepository.findByResultEntryId(entry.getId()).orElse(null);
    if (matrix == null) {
      create(entry.getId(), request, actorId, true);
      return true;
    }
    if (matrix.getExecutionStatus() == MatrixExecutionStatus.IN_PROGRESS) {
      boolean declarationChanged =
          matrix.getDeclaredRowCount() != request.rowCount()
              || matrix.getDeclaredColumnCount() != request.columnCount();
      String reason = declarationChanged ? trimToNull(revisionReason) : COMPLETION_REASON;
      if (declarationChanged && reason == null) {
        throw new BusinessRuleViolationException(
            "Debe explicar el ajuste de la cantidad declarada de yemas o cargadores.");
      }
      ensureSafeReduction(matrix, request);
      boolean changed = hasChanges(matrix, request);
      if (changed) {
        update(matrix, request, reason, actorId, actorRole, true);
      }
      audit(
          matrix.getId(),
          "EXECUTION",
          null,
          null,
          "status",
          MatrixExecutionStatus.IN_PROGRESS.name(),
          MatrixExecutionStatus.COMPLETED.name(),
          declarationChanged
              ? "Cierre de la evaluación después del ajuste: " + reason
              : COMPLETION_REASON,
          actorId,
          actorRole);
      matrix.complete(actorId);
      return true;
    }
    if (request.rowCount() < matrix.getRowCount()
        || request.columnCount() < matrix.getColumnCount()) {
      throw new BusinessRuleViolationException(
          "Una matriz registrada no puede reducir sus dimensiones.");
    }
    if (!hasChanges(matrix, request)) {
      return false;
    }
    String reason = trimToNull(revisionReason);
    if (reason == null) {
      throw new BusinessRuleViolationException(
          "Debe registrar el motivo al corregir datos de la matriz.");
    }
    update(matrix, request, reason, actorId, actorRole, false);
    return true;
  }

  public ResultDtos.MatrixResponse start(
      ResultEntry entry,
      int declaredRows,
      int declaredColumns,
      TechnicalCatalogDtos.TemplateResponse template,
      UUID actorId,
      String actorRole) {
    validateDimensions(declaredRows, declaredColumns, template);
    if (matrixRepository.findByResultEntryId(entry.getId()).isPresent()) {
      throw new BusinessRuleViolationException("La evaluación matricial ya fue iniciada.");
    }
    ResultMatrix matrix = createDraft(entry.getId(), declaredRows, declaredColumns, actorId);
    audit(
        matrix.getId(),
        "EXECUTION",
        null,
        null,
        "status",
        null,
        MatrixExecutionStatus.IN_PROGRESS.name(),
        "Inicio de la evaluación",
        actorId,
        actorRole);
    return toResponse(matrix, template);
  }

  public ResultDtos.MatrixResponse saveDraft(
      ResultEntry entry,
      ResultDtos.MatrixRequest request,
      TechnicalCatalogDtos.TemplateResponse template,
      String adjustmentReason,
      UUID actorId,
      String actorRole) {
    validate(request, template, false);
    ResultMatrix matrix =
        matrixRepository
            .findByResultEntryId(entry.getId())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Primero debe iniciar la evaluación matricial."));
    if (matrix.getExecutionStatus() != MatrixExecutionStatus.IN_PROGRESS) {
      throw new BusinessRuleViolationException(
          "Una evaluación finalizada no admite guardados parciales.");
    }
    boolean declarationChanged =
        matrix.getDeclaredRowCount() != request.rowCount()
            || matrix.getDeclaredColumnCount() != request.columnCount();
    String reason = declarationChanged ? trimToNull(adjustmentReason) : PARTIAL_SAVE_REASON;
    if (declarationChanged && reason == null) {
      throw new BusinessRuleViolationException(
          "Debe explicar el ajuste de la cantidad declarada de yemas o cargadores.");
    }
    if (!hasChanges(matrix, request)) {
      matrix.touch(actorId);
      return toResponse(matrix, template);
    }
    ensureSafeReduction(matrix, request);
    update(matrix, request, reason, actorId, actorRole, true);
    return toResponse(matrix, template);
  }

  public ResultDtos.MatrixResponse getMatrix(
      UUID resultEntryId, TechnicalCatalogDtos.TemplateResponse template) {
    return matrixRepository
        .findByResultEntryId(resultEntryId)
        .map(matrix -> toResponse(matrix, template))
        .orElse(null);
  }

  public List<ResultDtos.MatrixCorrectionResponse> getCorrections(UUID resultEntryId) {
    ResultMatrix matrix = matrixRepository.findByResultEntryId(resultEntryId).orElse(null);
    if (matrix == null) {
      return List.of();
    }
    List<ResultMatrixChangeLog> changes =
        changeRepository.findByResultMatrixIdOrderByChangedAtDesc(matrix.getId());
    Map<UUID, ApplicationUser> users =
        userRepository
            .findAllById(changes.stream().map(ResultMatrixChangeLog::getChangedBy).toList())
            .stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));
    return changes.stream()
        .map(
            change ->
                new ResultDtos.MatrixCorrectionResponse(
                    change.getId(),
                    change.getTargetType(),
                    change.getRowIndex(),
                    change.getColumnIndex(),
                    change.getFieldName(),
                    change.getOldValue(),
                    change.getNewValue(),
                    change.getReason(),
                    change.getChangedBy(),
                    users.containsKey(change.getChangedBy())
                        ? users.get(change.getChangedBy()).getFullName()
                        : "Usuario no disponible",
                    change.getChangedByRole(),
                    change.getChangedAt()))
        .toList();
  }

  private ResultDtos.MatrixResponse toResponse(
      ResultMatrix matrix, TechnicalCatalogDtos.TemplateResponse template) {
    List<ResultMatrixCell> storedCells =
        cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId());
    return new ResultDtos.MatrixResponse(
        matrix.getId(),
        matrix.getRowCount(),
        matrix.getColumnCount(),
        matrix.getDeclaredRowCount(),
        matrix.getDeclaredColumnCount(),
        matrix.getExecutionStatus(),
        completionPercentage(storedCells, matrix),
        matrix.getStartedAt(),
        matrix.getCompletedAt(),
        matrix.getLastModifiedAt(),
        matrix.getLastModifiedBy(),
        matrix.getLastModifiedBy() == null
            ? "Usuario no disponible"
            : userRepository
                .findById(matrix.getLastModifiedBy())
                .map(ApplicationUser::getFullName)
                .orElse("Usuario no disponible"),
        columnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()).stream()
            .map(
                column ->
                    new ResultDtos.MatrixColumnResponse(
                        column.getId(),
                        column.getColumnIndex(),
                        column.getCarrierLabel(),
                        column.getDistance(),
                        column.getDiameter(),
                        column.getShape()))
            .toList(),
        storedCells.stream()
            .map(
                cell ->
                    new ResultDtos.MatrixCellResponse(
                        cell.getId(),
                        cell.getRowIndex(),
                        cell.getColumnIndex(),
                        cell.getValueText(),
                        cell.getValueNumber(),
                        cell.getCategory()))
            .toList(),
        categorySummary(storedCells, matrix, template));
  }

  private void validate(
      ResultDtos.MatrixRequest request,
      TechnicalCatalogDtos.TemplateResponse template,
      boolean requireComplete) {
    validateDimensions(request.rowCount(), request.columnCount(), template);
    if (request.columns().size() != request.columnCount()
        || request.cells().size() != request.rowCount() * request.columnCount()) {
      throw new BusinessRuleViolationException(
          "La matriz debe contener todos sus cargadores y celdas.");
    }
    Set<Integer> columns = new HashSet<>();
    request.columns().forEach(column -> columns.add(column.columnIndex()));
    Set<String> cells = new HashSet<>();
    request.cells().forEach(cell -> cells.add(cell.rowIndex() + ":" + cell.columnIndex()));
    if (columns.size() != request.columnCount()
        || cells.size() != request.rowCount() * request.columnCount()) {
      throw new BusinessRuleViolationException(
          "Los índices de filas y columnas de la matriz deben ser únicos.");
    }
    boolean invalidColumnIndexes =
        request.columns().stream()
            .anyMatch(
                column ->
                    column.columnIndex() < 0 || column.columnIndex() >= request.columnCount());
    boolean invalidCellIndexes =
        request.cells().stream()
            .anyMatch(
                cell ->
                    cell.rowIndex() < 0
                        || cell.rowIndex() >= request.rowCount()
                        || cell.columnIndex() < 0
                        || cell.columnIndex() >= request.columnCount());
    if (invalidColumnIndexes || invalidCellIndexes) {
      throw new BusinessRuleViolationException(
          "Los índices de la matriz deben estar dentro de sus dimensiones.");
    }
    if (request.columns().stream()
        .anyMatch(
            column ->
                (column.distance() != null && column.distance().signum() < 0)
                    || (column.diameter() != null && column.diameter().signum() < 0))) {
      throw new BusinessRuleViolationException(
          "La distancia y el diámetro de los cargadores no pueden ser negativos.");
    }
    if (requireComplete
        && request.columns().stream()
            .anyMatch(
                column ->
                    trimToNull(column.carrierLabel()) == null
                        || column.distance() == null
                        || column.diameter() == null
                        || trimToNull(column.shape()) == null)) {
      throw new BusinessRuleViolationException(
          "Cada cargador requiere nombre, distancia, diámetro y forma válidos.");
    }
    Set<String> categories =
        template.categories().stream()
            .map(TechnicalCatalogDtos.TemplateCategoryResponse::code)
            .map(this::normalize)
            .collect(Collectors.toSet());
    if (!categories.isEmpty()
        && request.cells().stream()
            .map(ResultDtos.MatrixCellRequest::categoryCode)
            .map(this::normalize)
            .filter(Objects::nonNull)
            .anyMatch(category -> !categories.contains(category))) {
      throw new BusinessRuleViolationException(
          "La matriz contiene una categoría no permitida por la plantilla.");
    }
    if (requireComplete
        && !categories.isEmpty()
        && request.cells().stream().anyMatch(cell -> normalize(cell.categoryCode()) == null)) {
      throw new BusinessRuleViolationException("Todas las celdas de la matriz son obligatorias.");
    }
    if (requireComplete
        && categories.isEmpty()
        && request.cells().stream()
            .anyMatch(cell -> trimToNull(cell.valueText()) == null && cell.valueNumber() == null)) {
      throw new BusinessRuleViolationException("Todas las celdas de la matriz son obligatorias.");
    }
  }

  private ResultMatrix create(
      UUID resultEntryId, ResultDtos.MatrixRequest request, UUID actorId, boolean completed) {
    ResultMatrix matrix =
        matrixRepository.save(
            new ResultMatrix(
                resultEntryId, request.rowCount(), request.columnCount(), actorId, completed));
    request
        .columns()
        .forEach(
            column ->
                columnRepository.save(
                    new ResultMatrixColumn(
                        matrix.getId(),
                        column.columnIndex(),
                        trimToNull(column.carrierLabel()),
                        column.distance(),
                        column.diameter(),
                        trimToNull(column.shape()))));
    request
        .cells()
        .forEach(
            cell ->
                cellRepository.save(
                    new ResultMatrixCell(
                        matrix.getId(),
                        cell.rowIndex(),
                        cell.columnIndex(),
                        trimToNull(cell.valueText()),
                        cell.valueNumber(),
                        normalize(cell.categoryCode()))));
    return matrix;
  }

  private ResultMatrix createDraft(
      UUID resultEntryId, int rowCount, int columnCount, UUID actorId) {
    ResultMatrix matrix =
        matrixRepository.save(
            new ResultMatrix(resultEntryId, rowCount, columnCount, actorId, false));
    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
      columnRepository.save(
          new ResultMatrixColumn(
              matrix.getId(), columnIndex, "Cargador " + (columnIndex + 1), null, null, null));
    }
    for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
      for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
        cellRepository.save(
            new ResultMatrixCell(matrix.getId(), rowIndex, columnIndex, null, null, null));
      }
    }
    return matrix;
  }

  private void update(
      ResultMatrix matrix,
      ResultDtos.MatrixRequest request,
      String reason,
      UUID actorId,
      String actorRole,
      boolean allowReduction) {
    int previousRows = matrix.getRowCount();
    int previousColumns = matrix.getColumnCount();
    audit(
        matrix.getId(),
        "MATRIX",
        null,
        null,
        "dimensions",
        matrix.getRowCount() + "x" + matrix.getColumnCount(),
        request.rowCount() + "x" + request.columnCount(),
        reason,
        actorId,
        actorRole);
    if (allowReduction) {
      deleteOutsideDimensions(matrix.getId(), request.rowCount(), request.columnCount());
    }
    if (previousRows != request.rowCount() || previousColumns != request.columnCount()) {
      matrix.adjustDeclaration(request.rowCount(), request.columnCount(), actorId);
    } else {
      matrix.resize(request.rowCount(), request.columnCount());
    }

    Map<Integer, ResultMatrixColumn> columns =
        columnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()).stream()
            .collect(Collectors.toMap(ResultMatrixColumn::getColumnIndex, Function.identity()));
    for (ResultDtos.MatrixColumnRequest input : request.columns()) {
      ResultMatrixColumn column = columns.get(input.columnIndex());
      if (column == null) {
        audit(
            matrix.getId(),
            "COLUMN",
            null,
            input.columnIndex(),
            "metadata",
            null,
            columnValue(input),
            reason,
            actorId,
            actorRole);
        columnRepository.save(
            new ResultMatrixColumn(
                matrix.getId(),
                input.columnIndex(),
                trimToNull(input.carrierLabel()),
                input.distance(),
                input.diameter(),
                trimToNull(input.shape())));
        continue;
      }
      audit(
          matrix.getId(),
          "COLUMN",
          null,
          input.columnIndex(),
          "metadata",
          columnValue(column),
          columnValue(input),
          reason,
          actorId,
          actorRole);
      column.replace(
          trimToNull(input.carrierLabel()),
          input.distance(),
          input.diameter(),
          trimToNull(input.shape()));
    }

    Map<String, ResultMatrixCell> cells =
        cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId()).stream()
            .collect(
                Collectors.toMap(
                    cell -> cell.getRowIndex() + ":" + cell.getColumnIndex(), Function.identity()));
    for (ResultDtos.MatrixCellRequest input : request.cells()) {
      String key = input.rowIndex() + ":" + input.columnIndex();
      ResultMatrixCell cell = cells.get(key);
      if (cell == null) {
        audit(
            matrix.getId(),
            "CELL",
            input.rowIndex(),
            input.columnIndex(),
            "value",
            null,
            cellValue(input),
            reason,
            actorId,
            actorRole);
        cellRepository.save(
            new ResultMatrixCell(
                matrix.getId(),
                input.rowIndex(),
                input.columnIndex(),
                trimToNull(input.valueText()),
                input.valueNumber(),
                normalize(input.categoryCode())));
        continue;
      }
      audit(
          matrix.getId(),
          "CELL",
          input.rowIndex(),
          input.columnIndex(),
          "value",
          cellValue(cell),
          cellValue(input),
          reason,
          actorId,
          actorRole);
      cell.replace(
          trimToNull(input.valueText()), input.valueNumber(), normalize(input.categoryCode()));
    }
    matrix.touch(actorId);
  }

  private boolean hasChanges(ResultMatrix matrix, ResultDtos.MatrixRequest request) {
    if (matrix.getRowCount() != request.rowCount()
        || matrix.getColumnCount() != request.columnCount()) {
      return true;
    }
    Map<Integer, ResultMatrixColumn> columns =
        columnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()).stream()
            .collect(Collectors.toMap(ResultMatrixColumn::getColumnIndex, Function.identity()));
    boolean changedColumns =
        request.columns().stream()
            .anyMatch(
                input -> {
                  ResultMatrixColumn current = columns.get(input.columnIndex());
                  return current == null
                      || !Objects.equals(columnValue(current), columnValue(input));
                });
    if (changedColumns) {
      return true;
    }
    Map<String, ResultMatrixCell> cells =
        cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId()).stream()
            .collect(
                Collectors.toMap(
                    cell -> cell.getRowIndex() + ":" + cell.getColumnIndex(), Function.identity()));
    return request.cells().stream()
        .anyMatch(
            input -> {
              ResultMatrixCell current = cells.get(input.rowIndex() + ":" + input.columnIndex());
              return current == null || !Objects.equals(cellValue(current), cellValue(input));
            });
  }

  private void audit(
      UUID matrixId,
      String targetType,
      Integer rowIndex,
      Integer columnIndex,
      String fieldName,
      String oldValue,
      String newValue,
      String reason,
      UUID actorId,
      String actorRole) {
    if (!Objects.equals(oldValue, newValue)) {
      changeRepository.save(
          new ResultMatrixChangeLog(
              matrixId,
              targetType,
              rowIndex,
              columnIndex,
              fieldName,
              oldValue,
              newValue,
              reason,
              actorId,
              actorRole));
    }
  }

  private void validateDimensions(
      int rowCount, int columnCount, TechnicalCatalogDtos.TemplateResponse template) {
    if (rowCount < 1 || columnCount < 1) {
      throw new BusinessRuleViolationException(
          "La evaluación requiere al menos una yema y un cargador.");
    }
    if (rowCount > valueOrMaximum(template.maximumRows())
        || columnCount > valueOrMaximum(template.maximumColumns())) {
      throw new BusinessRuleViolationException(
          "Las dimensiones exceden los límites configurados en la plantilla.");
    }
  }

  private void ensureSafeReduction(ResultMatrix matrix, ResultDtos.MatrixRequest request) {
    if (request.rowCount() >= matrix.getRowCount()
        && request.columnCount() >= matrix.getColumnCount()) {
      return;
    }
    boolean removesRecordedCells =
        cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId()).stream()
            .filter(
                cell ->
                    cell.getRowIndex() >= request.rowCount()
                        || cell.getColumnIndex() >= request.columnCount())
            .anyMatch(this::isCompleted);
    if (removesRecordedCells) {
      throw new BusinessRuleViolationException(
          "No se puede reducir la matriz porque se eliminarían celdas con datos registrados.");
    }
  }

  private void deleteOutsideDimensions(UUID matrixId, int rowCount, int columnCount) {
    cellRepository.deleteByResultMatrixIdAndRowIndexGreaterThanEqual(matrixId, rowCount);
    cellRepository.deleteByResultMatrixIdAndColumnIndexGreaterThanEqual(matrixId, columnCount);
    columnRepository.deleteByResultMatrixIdAndColumnIndexGreaterThanEqual(matrixId, columnCount);
  }

  private BigDecimal completionPercentage(List<ResultMatrixCell> cells, ResultMatrix matrix) {
    long total = (long) matrix.getDeclaredRowCount() * matrix.getDeclaredColumnCount();
    if (total == 0) {
      return BigDecimal.ZERO;
    }
    long completed = cells.stream().filter(this::isCompleted).count();
    return BigDecimal.valueOf(completed * 100)
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }

  private List<ResultDtos.MatrixCategorySummaryResponse> categorySummary(
      List<ResultMatrixCell> cells,
      ResultMatrix matrix,
      TechnicalCatalogDtos.TemplateResponse template) {
    if (template == null || template.categories().isEmpty()) {
      return List.of();
    }
    Map<String, Long> counts =
        cells.stream()
            .map(ResultMatrixCell::getCategory)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BigDecimal total =
        BigDecimal.valueOf((long) matrix.getDeclaredRowCount() * matrix.getDeclaredColumnCount());
    return template.categories().stream()
        .sorted(
            Comparator.comparingInt(TechnicalCatalogDtos.TemplateCategoryResponse::displayOrder))
        .map(
            category -> {
              long count = counts.getOrDefault(normalize(category.code()), 0L);
              BigDecimal percentage =
                  total.signum() == 0
                      ? BigDecimal.ZERO
                      : BigDecimal.valueOf(count * 100).divide(total, 2, RoundingMode.HALF_UP);
              return new ResultDtos.MatrixCategorySummaryResponse(
                  category.code(), category.label(), count, percentage);
            })
        .toList();
  }

  private boolean isCompleted(ResultMatrixCell cell) {
    return trimToNull(cell.getValueText()) != null
        || cell.getValueNumber() != null
        || normalize(cell.getCategory()) != null;
  }

  private String columnValue(ResultMatrixColumn column) {
    return String.join(
        " | ",
        Objects.toString(column.getCarrierLabel(), ""),
        decimalValue(column.getDistance()),
        decimalValue(column.getDiameter()),
        Objects.toString(column.getShape(), ""));
  }

  private String columnValue(ResultDtos.MatrixColumnRequest column) {
    return String.join(
        " | ",
        Objects.toString(trimToNull(column.carrierLabel()), ""),
        decimalValue(column.distance()),
        decimalValue(column.diameter()),
        Objects.toString(trimToNull(column.shape()), ""));
  }

  private String cellValue(ResultMatrixCell cell) {
    return String.join(
        " | ",
        Objects.toString(cell.getValueText(), ""),
        decimalValue(cell.getValueNumber()),
        Objects.toString(cell.getCategory(), ""));
  }

  private String cellValue(ResultDtos.MatrixCellRequest cell) {
    return String.join(
        " | ",
        Objects.toString(trimToNull(cell.valueText()), ""),
        decimalValue(cell.valueNumber()),
        Objects.toString(normalize(cell.categoryCode()), ""));
  }

  private String decimalValue(java.math.BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  private int valueOrMaximum(Integer maximum) {
    return maximum == null ? Integer.MAX_VALUE : maximum;
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase();
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
