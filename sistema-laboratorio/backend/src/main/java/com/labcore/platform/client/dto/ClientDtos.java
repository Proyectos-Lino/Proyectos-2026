package com.labcore.platform.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ClientDtos {

  private ClientDtos() {}

  public record ClientRequest(
      @NotNull ParticipantType participantType,
      @NotNull DocumentType documentType,
      @NotBlank @Size(max = 30) String documentNumber,
      @NotBlank @Size(max = 200) String legalName,
      @Size(max = 200) String reportName,
      @Email @Size(max = 180) String email,
      @Size(max = 300) String address,
      @Size(max = 160) String contactName,
      @Size(max = 40) String phone) {}

  public record StatusRequest(@NotNull Boolean active) {}

  public record ClientResponse(
      UUID id,
      ParticipantType participantType,
      DocumentType documentType,
      String documentNumber,
      String legalName,
      String reportName,
      String email,
      String address,
      String contactName,
      String phone,
      boolean active,
      Instant createdAt) {
    public static ClientResponse from(Client client) {
      return new ClientResponse(
          client.getId(),
          client.getParticipantType(),
          client.getDocumentType(),
          client.getDocumentNumber(),
          client.getLegalName(),
          client.getReportName(),
          client.getEmail(),
          client.getAddress(),
          client.getContactName(),
          client.getPhone(),
          client.isActive(),
          client.getCreatedAt());
    }
  }
}
