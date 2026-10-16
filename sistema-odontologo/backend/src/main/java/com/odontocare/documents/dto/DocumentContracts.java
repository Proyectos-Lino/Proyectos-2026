package com.odontocare.documents.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class DocumentContracts {
  private DocumentContracts() {}

  public record CategoryRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull Boolean active,
      @NotNull @PositiveOrZero Long version) {}

  public record CategoryResponse(UUID id, String name, Boolean active, long version) {}

  public record UploadRequest(
      @NotNull UUID patientId,
      UUID encounterId,
      UUID planId,
      Integer tooth,
      @NotNull UUID categoryId,
      @NotNull LocalDate recordedOn,
      @NotNull @Size(max = 1000) String description) {}

  public record DocumentResponse(
      UUID id,
      UUID patientId,
      UUID encounterId,
      UUID planId,
      Integer tooth,
      UUID categoryId,
      String categoryName,
      LocalDate recordedOn,
      String description,
      String fileName,
      String mediaType,
      long byteSize,
      String sha256,
      String actorName,
      Instant createdAt) {}

  public record ConsentRequest(
      @NotNull UUID patientId,
      @NotNull UUID documentId,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 160) String responsible,
      @NotBlank @Size(max = 80) String relationship,
      @NotNull LocalDate signedOn) {}

  public record ConsentResponse(
      UUID id,
      UUID patientId,
      UUID documentId,
      String name,
      String responsible,
      String relationship,
      LocalDate signedOn,
      String actorName,
      Instant createdAt) {}

  public record PolicyRequest(
      @NotNull @Min(1) @Max(60) Integer maxFileMiB, @NotNull @PositiveOrZero Long version) {}

  public record PolicyResponse(int maxFileMiB, long version, long storedBytes, long fileCount) {}

  public record Download(String fileName, String mediaType, byte[] bytes) {}
}
