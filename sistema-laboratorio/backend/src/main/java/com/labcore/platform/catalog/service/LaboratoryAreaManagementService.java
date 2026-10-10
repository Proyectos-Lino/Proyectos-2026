package com.labcore.platform.catalog;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LaboratoryAreaManagementService {

  private final LaboratoryAreaRepository areaRepository;

  public LaboratoryAreaManagementService(LaboratoryAreaRepository areaRepository) {
    this.areaRepository = areaRepository;
  }

  public LaboratoryAreaDtos.Response create(LaboratoryAreaDtos.CreateRequest request) {
    String code = normalizeCode(request.code());
    if (areaRepository.existsByCodeIgnoreCase(code)) {
      throw new BusinessRuleViolationException("Ya existe un área con ese código.");
    }

    return toResponse(areaRepository.save(new LaboratoryArea(code, request.name().trim())));
  }

  @Transactional(readOnly = true)
  public Page<LaboratoryAreaDtos.Response> findPage(
      String search, Boolean active, Pageable pageable) {
    String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
    Specification<LaboratoryArea> specification = (root, query, builder) -> builder.conjunction();

    if (normalizedSearch != null) {
      String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.or(
                      builder.like(builder.lower(root.get("code").as(String.class)), pattern),
                      builder.like(builder.lower(root.get("name").as(String.class)), pattern)));
    }
    if (active != null) {
      specification =
          specification.and((root, query, builder) -> builder.equal(root.get("active"), active));
    }

    return areaRepository.findAll(specification, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public LaboratoryArea getActiveArea(UUID id) {
    LaboratoryArea area = getArea(id);
    if (!area.isActive()) {
      throw new BusinessRuleViolationException("El área seleccionada se encuentra inactiva.");
    }
    return area;
  }

  @Transactional(readOnly = true)
  public java.util.List<LaboratoryAreaDtos.Option> listActiveOptions() {
    return areaRepository.findByActiveTrueOrderByNameAsc().stream()
        .map(area -> new LaboratoryAreaDtos.Option(area.getId(), area.getCode(), area.getName()))
        .toList();
  }

  public LaboratoryAreaDtos.Response update(UUID id, LaboratoryAreaDtos.UpdateRequest request) {
    LaboratoryArea area = getArea(id);
    String code = normalizeCode(request.code());
    if (areaRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw new BusinessRuleViolationException("Ya existe un área con ese código.");
    }
    area.update(code, request.name().trim());
    return toResponse(area);
  }

  public LaboratoryAreaDtos.Response updateStatus(UUID id, boolean active) {
    LaboratoryArea area = getArea(id);
    if (active) {
      area.activate();
    } else {
      area.deactivate();
    }
    return toResponse(area);
  }

  @Transactional(readOnly = true)
  public LaboratoryArea getArea(UUID id) {
    return areaRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el área de laboratorio", id));
  }

  private LaboratoryAreaDtos.Response toResponse(LaboratoryArea area) {
    return new LaboratoryAreaDtos.Response(
        area.getId(), area.getCode(), area.getName(), area.isActive(), area.getCreatedAt());
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
