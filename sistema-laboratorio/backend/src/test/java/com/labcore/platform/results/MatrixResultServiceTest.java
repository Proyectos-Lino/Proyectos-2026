package com.labcore.platform.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.labcore.platform.catalog.ResultDataType;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.security.ApplicationUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatrixResultServiceTest {
  @Mock private ResultMatrixRepository matrixRepository;
  @Mock private ResultMatrixColumnRepository columnRepository;
  @Mock private ResultMatrixCellRepository cellRepository;
  @Mock private ResultMatrixChangeLogRepository changeRepository;
  @Mock private ApplicationUserRepository userRepository;

  @Test
  void startsAnIncompleteMatrixExecutionWithZeroProgress() {
    ResultEntry entry =
        new ResultEntry(
            UUID.randomUUID(), UUID.randomUUID(), ResultMode.MATRIX, null, UUID.randomUUID());
    entry.markDraft();
    UUID actorId = UUID.randomUUID();
    given(matrixRepository.findByResultEntryId(entry.getId())).willReturn(Optional.empty());
    given(matrixRepository.save(any(ResultMatrix.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    ResultDtos.MatrixResponse response =
        service().start(entry, 2, 3, template(), actorId, "ANALYST");

    assertThat(response.executionStatus()).isEqualTo(MatrixExecutionStatus.IN_PROGRESS);
    assertThat(response.declaredRowCount()).isEqualTo(2);
    assertThat(response.declaredColumnCount()).isEqualTo(3);
    assertThat(response.completionPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.completedAt()).isNull();
    verify(columnRepository, org.mockito.Mockito.times(3)).save(any(ResultMatrixColumn.class));
    verify(cellRepository, org.mockito.Mockito.times(6)).save(any(ResultMatrixCell.class));
  }

  @Test
  void acceptsAnIncompletePartialSaveWithoutSendingItToValidation() {
    ResultEntry entry =
        new ResultEntry(
            UUID.randomUUID(), UUID.randomUUID(), ResultMode.MATRIX, null, UUID.randomUUID());
    entry.markDraft();
    UUID actorId = UUID.randomUUID();
    ResultMatrix matrix = new ResultMatrix(entry.getId(), 1, 1, actorId, false);
    ResultMatrixColumn column =
        new ResultMatrixColumn(matrix.getId(), 0, "Cargador 1", null, null, null);
    ResultMatrixCell cell = new ResultMatrixCell(matrix.getId(), 0, 0, null, null, null);
    given(matrixRepository.findByResultEntryId(entry.getId())).willReturn(Optional.of(matrix));
    given(columnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()))
        .willReturn(List.of(column));
    given(cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId()))
        .willReturn(List.of(cell));
    ResultDtos.MatrixRequest partial =
        new ResultDtos.MatrixRequest(
            1,
            1,
            List.of(new ResultDtos.MatrixColumnRequest(0, "Cargador 1", null, null, null)),
            List.of(new ResultDtos.MatrixCellRequest(0, 0, null, null, null)));

    ResultDtos.MatrixResponse response =
        service().saveDraft(entry, partial, template(), null, actorId, "ANALYST");

    assertThat(response.executionStatus()).isEqualTo(MatrixExecutionStatus.IN_PROGRESS);
    assertThat(response.completionPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(entry.getStatus()).isEqualTo(ResultEntryStatus.DRAFT);
  }

  @Test
  void requiresAReasonWhenDeclaredDimensionsChangeAtClosure() {
    ResultEntry entry =
        new ResultEntry(
            UUID.randomUUID(), UUID.randomUUID(), ResultMode.MATRIX, null, UUID.randomUUID());
    ResultMatrix matrix = new ResultMatrix(entry.getId(), 1, 1, UUID.randomUUID(), false);
    given(matrixRepository.findByResultEntryId(entry.getId())).willReturn(Optional.of(matrix));

    assertThatThrownBy(
            () ->
                service()
                    .save(
                        entry,
                        completeRequest(2, 1),
                        template(),
                        null,
                        UUID.randomUUID(),
                        "ANALYST"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Debe explicar el ajuste de la cantidad declarada de yemas o cargadores.");

    verify(changeRepository, never()).save(any());
  }

  @Test
  void requiresReasonWhenChangingAMatrixCell() {
    ResultEntry entry =
        new ResultEntry(
            UUID.randomUUID(), UUID.randomUUID(), ResultMode.MATRIX, null, UUID.randomUUID());
    ResultMatrix matrix = new ResultMatrix(entry.getId(), 1, 1);
    ResultMatrixColumn column =
        new ResultMatrixColumn(
            matrix.getId(), 0, "Cargador 1", BigDecimal.ONE, BigDecimal.ONE, "Circular");
    ResultMatrixCell cell = new ResultMatrixCell(matrix.getId(), 0, 0, null, null, "A");
    given(matrixRepository.findByResultEntryId(entry.getId())).willReturn(Optional.of(matrix));
    given(columnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()))
        .willReturn(List.of(column));
    given(cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId()))
        .willReturn(List.of(cell));

    assertThatThrownBy(
            () ->
                service().save(entry, request("B"), template(), null, UUID.randomUUID(), "ANALYST"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Debe registrar el motivo al corregir datos de la matriz.");

    verify(changeRepository, never()).save(any());
  }

  @Test
  void rejectsCarrierWithoutRequiredMetadata() {
    ResultDtos.MatrixRequest invalidRequest =
        new ResultDtos.MatrixRequest(
            1,
            1,
            List.of(new ResultDtos.MatrixColumnRequest(0, "Cargador 1", null, null, null)),
            List.of(new ResultDtos.MatrixCellRequest(0, 0, null, null, "A")));

    assertThatThrownBy(
            () ->
                service()
                    .save(
                        new ResultEntry(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            ResultMode.MATRIX,
                            null,
                            UUID.randomUUID()),
                        invalidRequest,
                        template(),
                        null,
                        UUID.randomUUID(),
                        "ANALYST"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Cada cargador requiere nombre, distancia, diámetro y forma válidos.");
  }

  @Test
  void ignoresDecimalScaleWhenMatrixValuesAreEquivalent() {
    ResultEntry entry =
        new ResultEntry(
            UUID.randomUUID(), UUID.randomUUID(), ResultMode.MATRIX, null, UUID.randomUUID());
    ResultMatrix matrix = new ResultMatrix(entry.getId(), 1, 1);
    ResultMatrixColumn column =
        new ResultMatrixColumn(
            matrix.getId(),
            0,
            "Cargador 1",
            new BigDecimal("5.000000"),
            new BigDecimal("7.000000"),
            "Circular");
    ResultMatrixCell cell = new ResultMatrixCell(matrix.getId(), 0, 0, null, null, "A");
    given(matrixRepository.findByResultEntryId(entry.getId())).willReturn(Optional.of(matrix));
    given(columnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()))
        .willReturn(List.of(column));
    given(cellRepository.findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId()))
        .willReturn(List.of(cell));
    ResultDtos.MatrixRequest request =
        new ResultDtos.MatrixRequest(
            1,
            1,
            List.of(
                new ResultDtos.MatrixColumnRequest(
                    0, "Cargador 1", new BigDecimal("5"), new BigDecimal("7"), "Circular")),
            List.of(new ResultDtos.MatrixCellRequest(0, 0, null, null, "A")));

    assertThatCode(
            () -> service().save(entry, request, template(), null, UUID.randomUUID(), "ANALYST"))
        .doesNotThrowAnyException();

    verify(changeRepository, never()).save(any());
  }

  private ResultDtos.MatrixRequest request(String category) {
    return new ResultDtos.MatrixRequest(
        1,
        1,
        List.of(
            new ResultDtos.MatrixColumnRequest(
                0, "Cargador 1", BigDecimal.ONE, BigDecimal.ONE, "Circular")),
        List.of(new ResultDtos.MatrixCellRequest(0, 0, null, null, category)));
  }

  private ResultDtos.MatrixRequest completeRequest(int rows, int columns) {
    return new ResultDtos.MatrixRequest(
        rows,
        columns,
        java.util.stream.IntStream.range(0, columns)
            .mapToObj(
                index ->
                    new ResultDtos.MatrixColumnRequest(
                        index,
                        "Cargador " + (index + 1),
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        "Circular"))
            .toList(),
        java.util.stream.IntStream.range(0, rows * columns)
            .mapToObj(
                index ->
                    new ResultDtos.MatrixCellRequest(
                        index / columns, index % columns, null, null, "A"))
            .toList());
  }

  private TechnicalCatalogDtos.TemplateResponse template() {
    return new TechnicalCatalogDtos.TemplateResponse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Conteo de yemas",
        UUID.randomUUID(),
        "Fruta",
        "Matriz de yemas",
        null,
        ResultMode.MATRIX,
        1,
        null,
        1,
        10,
        1,
        10,
        true,
        Instant.now(),
        List.of(
            new TechnicalCatalogDtos.TemplateFieldResponse(
                UUID.randomUUID(),
                "clasificacion",
                "Clasificación",
                ResultDataType.QUALITATIVE,
                null,
                true,
                1,
                com.labcore.platform.catalog.ResultFieldKind.CAPTURED,
                false,
                null,
                com.labcore.platform.catalog.CalculationScope.PER_REPETITION,
                null,
                null,
                null)),
        List.of(
            new TechnicalCatalogDtos.TemplateCategoryResponse(
                UUID.randomUUID(), "A", "Categoría A", 1),
            new TechnicalCatalogDtos.TemplateCategoryResponse(
                UUID.randomUUID(), "B", "Categoría B", 2)));
  }

  private MatrixResultService service() {
    return new MatrixResultService(
        matrixRepository, columnRepository, cellRepository, changeRepository, userRepository);
  }
}
