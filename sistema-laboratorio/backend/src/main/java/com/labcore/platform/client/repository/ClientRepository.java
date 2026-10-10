package com.labcore.platform.client;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClientRepository
    extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {
  boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

  boolean existsByDocumentTypeAndDocumentNumberAndIdNot(
      DocumentType documentType, String documentNumber, UUID id);

  Optional<Client> findByDocumentTypeAndDocumentNumber(
      DocumentType documentType, String documentNumber);

  long countByActiveTrue();

  long countByActiveTrueAndParticipantTypeIn(Collection<ParticipantType> participantTypes);
}
