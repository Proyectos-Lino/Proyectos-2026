package com.labcore.platform.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.client.Client;
import com.labcore.platform.client.ClientService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.organization.OrganizationProfileService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOrderDocumentService {

  private final ServiceOrderRepository orderRepository;
  private final ServiceOrderSampleRepository sampleRepository;
  private final ServiceOrderSampleTestRepository requestedTestRepository;
  private final ServiceOrderDocumentRepository documentRepository;
  private final ClientService clientService;
  private final CatalogService catalogService;
  private final OrganizationProfileService organizationService;
  private final ObjectMapper objectMapper;

  public ServiceOrderDocumentService(
      ServiceOrderRepository orderRepository,
      ServiceOrderSampleRepository sampleRepository,
      ServiceOrderSampleTestRepository requestedTestRepository,
      ServiceOrderDocumentRepository documentRepository,
      ClientService clientService,
      CatalogService catalogService,
      OrganizationProfileService organizationService,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.sampleRepository = sampleRepository;
    this.requestedTestRepository = requestedTestRepository;
    this.documentRepository = documentRepository;
    this.clientService = clientService;
    this.catalogService = catalogService;
    this.organizationService = organizationService;
    this.objectMapper = objectMapper;
  }

  public ServiceOrderDtos.ServiceOrderDocumentResponse generateForConfirmation(
      UUID orderId, String generatedBy) {
    ServiceOrder order = getConfirmedOrder(orderId);
    return documentRepository
        .findFirstByServiceOrderIdOrderByVersionDesc(orderId)
        .map(this::readDocument)
        .orElseGet(() -> createDocument(order, generatedBy));
  }

  public ServiceOrderDtos.ServiceOrderDocumentResponse getDocument(
      UUID orderId, String requestedBy) {
    ServiceOrder order = getConfirmedOrder(orderId);
    return documentRepository
        .findFirstByServiceOrderIdOrderByVersionDesc(orderId)
        .map(this::readDocument)
        .orElseGet(() -> createDocument(order, requestedBy));
  }

  private ServiceOrder getConfirmedOrder(UUID orderId) {
    ServiceOrder order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("la orden de servicio", orderId));
    if (order.getConfirmedAt() == null) {
      throw new BusinessRuleViolationException(
          "El documento de la orden solo está disponible después de enviarla al laboratorio.");
    }
    return order;
  }

  private ServiceOrderDtos.ServiceOrderDocumentResponse createDocument(
      ServiceOrder order, String generatedBy) {
    ServiceOrderDtos.ServiceOrderDocumentResponse snapshot = buildSnapshot(order);
    ServiceOrderDocument document =
        documentRepository.save(
            new ServiceOrderDocument(
                order.getId(), nextVersion(order.getId()), serialize(snapshot), generatedBy));
    return withDocumentMetadata(snapshot, document);
  }

  public ServiceOrderDtos.ServiceOrderDocumentResponse regenerateAfterCorrection(
      UUID orderId, String generatedBy) {
    return createDocument(getConfirmedOrder(orderId), generatedBy);
  }

  private int nextVersion(UUID orderId) {
    return documentRepository
        .findFirstByServiceOrderIdOrderByVersionDesc(orderId)
        .map(document -> document.getVersion() + 1)
        .orElse(1);
  }

  private ServiceOrderDtos.ServiceOrderDocumentResponse buildSnapshot(ServiceOrder order) {
    Client client = clientService.getRequired(order.getClientId());
    List<ServiceOrderSample> samples =
        sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(
            order.getId());
    Map<UUID, List<ServiceOrderSampleTest>> testsBySample =
        requestedTestRepository
            .findByServiceOrderSampleIdIn(samples.stream().map(ServiceOrderSample::getId).toList())
            .stream()
            .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
            .collect(Collectors.groupingBy(ServiceOrderSampleTest::getServiceOrderSampleId));
    List<ServiceOrderDtos.DocumentSample> documentSamples =
        samples.stream()
            .map(
                sample ->
                    toDocumentSample(sample, testsBySample.getOrDefault(sample.getId(), List.of())))
            .toList();
    return new ServiceOrderDtos.ServiceOrderDocumentResponse(
        null,
        0,
        Instant.now(),
        organizationService.documentIdentity(),
        order.getCode(),
        client.getLegalName(),
        client.getDocumentType() + " " + client.getDocumentNumber(),
        catalogService.getArea(order.getLaboratoryAreaId()).getName(),
        order.getReportName(),
        order.getReportEmail(),
        order.getReportAddress(),
        order.getDeliveryMethod(),
        order.getPickupPerson(),
        order.getPlannedDeliveryAt(),
        order.getProformaNumber(),
        order.getTechnicalProposalNumber(),
        order.getDevelopmentDays(),
        order.isOutsourced(),
        order.getPostAnalysisCustody(),
        order.getGeneralObservations(),
        documentSamples);
  }

  private ServiceOrderDtos.DocumentSample toDocumentSample(
      ServiceOrderSample sample, List<ServiceOrderSampleTest> requestedTests) {
    return new ServiceOrderDtos.DocumentSample(
        sample.getCommercialSequence(),
        sample.getClientIdentification(),
        producerName(sample.getProducerId()),
        producerDocument(sample.getProducerId()),
        sample.getProductName(),
        sample.getVarietyName(),
        catalogService.getSampleType(sample.getSampleTypeId()).getName(),
        sample.getPhysicalDescription(),
        sample.getSamplingLocation(),
        sample.getOrigin(),
        sample.getSamplerName(),
        sample.getSampledAt(),
        sample.getQuantity(),
        sample.getMeasurementUnitId() == null
            ? null
            : catalogService.getUnit(sample.getMeasurementUnitId()).getName(),
        sample.getContainerDescription(),
        sample.getBatchNumber(),
        sample.getProductionYear(),
        sample.getBrand(),
        sample.getOriginCertificate(),
        sample.getSanitaryRegistration(),
        sample.getCropAge(),
        sample.getCarrierCount(),
        sample.getBudCount(),
        sample.isCounterSampleInCustody(),
        sample.getObservations(),
        requestedTests.stream()
            .map(
                requestedTest ->
                    new ServiceOrderDtos.DocumentTest(
                        catalogService.getService(requestedTest.getLaboratoryServiceId()).getName(),
                        catalogService.getTest(requestedTest.getTestDefinitionId()).getName(),
                        catalogService.getMethod(requestedTest.getTestMethodId()).getName()))
            .toList());
  }

  private String serialize(ServiceOrderDtos.ServiceOrderDocumentResponse snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible almacenar el documento de la orden.", exception);
    }
  }

  private ServiceOrderDtos.ServiceOrderDocumentResponse readDocument(
      ServiceOrderDocument document) {
    try {
      ServiceOrderDtos.ServiceOrderDocumentResponse snapshot =
          objectMapper.readValue(
              document.getSnapshotContent(), ServiceOrderDtos.ServiceOrderDocumentResponse.class);
      return withDocumentMetadata(snapshot, document);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible leer el documento almacenado de la orden.", exception);
    }
  }

  private ServiceOrderDtos.ServiceOrderDocumentResponse withDocumentMetadata(
      ServiceOrderDtos.ServiceOrderDocumentResponse snapshot, ServiceOrderDocument document) {
    return new ServiceOrderDtos.ServiceOrderDocumentResponse(
        document.getId(),
        document.getVersion(),
        document.getGeneratedAt(),
        snapshot.organization() == null
            ? organizationService.documentIdentity()
            : snapshot.organization(),
        snapshot.code(),
        snapshot.clientName(),
        snapshot.clientDocument(),
        snapshot.laboratoryAreaName(),
        snapshot.reportName(),
        snapshot.reportEmail(),
        snapshot.reportAddress(),
        snapshot.deliveryMethod(),
        snapshot.pickupPerson(),
        snapshot.plannedDeliveryAt(),
        snapshot.proformaNumber(),
        snapshot.technicalProposalNumber(),
        snapshot.developmentDays(),
        snapshot.outsourced(),
        snapshot.postAnalysisCustody(),
        snapshot.generalObservations(),
        snapshot.samples());
  }

  private String producerName(UUID producerId) {
    return producerId == null ? null : clientService.getRequired(producerId).getLegalName();
  }

  private String producerDocument(UUID producerId) {
    if (producerId == null) {
      return null;
    }
    Client producer = clientService.getRequired(producerId);
    return producer.getDocumentType() + " " + producer.getDocumentNumber();
  }
}
