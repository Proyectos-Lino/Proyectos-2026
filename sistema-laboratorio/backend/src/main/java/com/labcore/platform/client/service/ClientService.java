package com.labcore.platform.client;

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
public class ClientService {

  private final ClientRepository clientRepository;

  public ClientService(ClientRepository clientRepository) {
    this.clientRepository = clientRepository;
  }

  public ClientDtos.ClientResponse create(ClientDtos.ClientRequest request) {
    String documentNumber = normalizeDocument(request.documentNumber());
    if (clientRepository.existsByDocumentTypeAndDocumentNumber(
        request.documentType(), documentNumber)) {
      throw new BusinessRuleViolationException("Ya existe un participante con ese documento.");
    }
    Client client =
        clientRepository.save(
            new Client(
                request.participantType(),
                request.documentType(),
                documentNumber,
                request.legalName().trim(),
                nullableTrim(request.reportName()),
                nullableTrim(request.email()),
                nullableTrim(request.address()),
                nullableTrim(request.contactName()),
                nullableTrim(request.phone())));
    return ClientDtos.ClientResponse.from(client);
  }

  @Transactional(readOnly = true)
  public Page<ClientDtos.ClientResponse> search(
      String search,
      DocumentType documentType,
      ParticipantType participantType,
      Boolean active,
      Pageable pageable) {
    Specification<Client> specification = searchSpecification(search);
    if (documentType != null) {
      specification =
          specification.and(
              (root, query, builder) -> builder.equal(root.get("documentType"), documentType));
    }
    if (participantType != null) {
      specification = specification.and(participantTypeSpecification(participantType));
    }
    if (active != null) {
      specification =
          specification.and((root, query, builder) -> builder.equal(root.get("active"), active));
    }
    return clientRepository.findAll(specification, pageable).map(ClientDtos.ClientResponse::from);
  }

  public ClientDtos.ClientResponse update(UUID id, ClientDtos.ClientRequest request) {
    Client client = getRequired(id);
    String documentNumber = normalizeDocument(request.documentNumber());
    if (clientRepository.existsByDocumentTypeAndDocumentNumberAndIdNot(
        request.documentType(), documentNumber, id)) {
      throw new BusinessRuleViolationException("Ya existe un participante con ese documento.");
    }
    client.update(
        request.participantType(),
        request.documentType(),
        documentNumber,
        request.legalName().trim(),
        nullableTrim(request.reportName()),
        nullableTrim(request.email()),
        nullableTrim(request.address()),
        nullableTrim(request.contactName()),
        nullableTrim(request.phone()));
    return ClientDtos.ClientResponse.from(client);
  }

  public ClientDtos.ClientResponse updateStatus(UUID id, ClientDtos.StatusRequest request) {
    Client client = getRequired(id);
    if (request.active()) {
      client.activate();
    } else {
      client.deactivate();
    }
    return ClientDtos.ClientResponse.from(client);
  }

  @Transactional(readOnly = true)
  public Client getRequired(UUID id) {
    return clientRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el cliente", id));
  }

  public Client getActiveRequired(UUID id) {
    Client client = getRequired(id);
    if (!client.isActive()) {
      throw new BusinessRuleViolationException("El cliente seleccionado se encuentra inactivo.");
    }
    return client;
  }

  public Client getActiveClientRequired(UUID id) {
    Client client = getActiveRequired(id);
    if (!client.getParticipantType().canActAsClient()) {
      throw new BusinessRuleViolationException(
          "El participante seleccionado no está clasificado como cliente.");
    }
    return client;
  }

  public Client getActiveProducerRequired(UUID id) {
    Client client = getActiveRequired(id);
    if (!client.getParticipantType().canActAsProducer()) {
      throw new BusinessRuleViolationException(
          "El participante seleccionado no está clasificado como productor.");
    }
    return client;
  }

  private Specification<Client> participantTypeSpecification(ParticipantType participantType) {
    return switch (participantType) {
      case CLIENT ->
          (root, query, builder) ->
              root.get("participantType").in(ParticipantType.CLIENT, ParticipantType.BOTH);
      case PRODUCER ->
          (root, query, builder) ->
              root.get("participantType").in(ParticipantType.PRODUCER, ParticipantType.BOTH);
      case BOTH ->
          (root, query, builder) ->
              builder.equal(root.get("participantType"), ParticipantType.BOTH);
    };
  }

  private Specification<Client> searchSpecification(String search) {
    if (search == null || search.isBlank()) {
      return (root, query, builder) -> builder.conjunction();
    }
    String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, builder) ->
        builder.or(
            builder.like(builder.lower(root.get("legalName").as(String.class)), pattern),
            builder.like(builder.lower(root.get("documentNumber").as(String.class)), pattern),
            builder.like(builder.lower(root.get("email").as(String.class)), pattern),
            builder.like(builder.lower(root.get("contactName").as(String.class)), pattern));
  }

  private String normalizeDocument(String documentNumber) {
    return documentNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  private String nullableTrim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
