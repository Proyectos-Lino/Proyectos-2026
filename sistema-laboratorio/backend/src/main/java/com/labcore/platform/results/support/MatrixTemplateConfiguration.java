package com.labcore.platform.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import java.util.List;

public final class MatrixTemplateConfiguration {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MatrixTemplateConfiguration() {}

  public record Parameters(
      int defaultRows,
      Integer maximumRows,
      int defaultColumns,
      Integer maximumColumns,
      List<TechnicalCatalogDtos.TemplateCategoryResponse> categories,
      List<String> carrierShapes) {}

  public static String serialize(TechnicalCatalogDtos.TemplateResponse template) {
    try {
      return MAPPER.writeValueAsString(
          new Parameters(
              template.defaultRows(),
              template.maximumRows(),
              template.defaultColumns(),
              template.maximumColumns(),
              template.categories(),
              template.carrierShapes()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible conservar la configuración de la matriz.", exception);
    }
  }

  public static TechnicalCatalogDtos.TemplateResponse restore(
      TechnicalCatalogDtos.TemplateResponse template, String snapshot) {
    if (snapshot == null) return template;
    try {
      Parameters parameters = MAPPER.readValue(snapshot, Parameters.class);
      return new TechnicalCatalogDtos.TemplateResponse(
          template.id(),
          template.testDefinitionId(),
          template.testDefinitionName(),
          template.sampleTypeId(),
          template.sampleTypeName(),
          template.name(),
          template.description(),
          template.resultMode(),
          template.minimumRepetitions(),
          template.maximumRepetitions(),
          parameters.defaultRows(),
          parameters.maximumRows(),
          parameters.defaultColumns(),
          parameters.maximumColumns(),
          template.active(),
          template.createdAt(),
          template.fields(),
          parameters.categories(),
          parameters.carrierShapes());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible recuperar la configuración histórica de la matriz.", exception);
    }
  }
}
