package com.labcore.platform.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.security.ApplicationUserRepository;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OperationVersionService {
  private final OperationVersionRepository repository;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbc;
  private final ApplicationUserRepository users;

  public OperationVersionService(
      OperationVersionRepository repository,
      ObjectMapper mapper,
      JdbcTemplate jdbc,
      ApplicationUserRepository users) {
    this.repository = repository;
    this.mapper = mapper;
    this.jdbc = jdbc;
    this.users = users;
  }

  public void capture(
      OperationType type, UUID id, String action, String reason, Object snapshot, String actor) {
    // Serialize the numbering of concurrent snapshots for this operation only.
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        (org.springframework.jdbc.core.RowCallbackHandler) row -> {},
        type.name() + ":" + id);
    String roles =
        users
            .findByEmailIgnoreCase(actor)
            .map(
                user ->
                    user.getRoles().stream()
                        .map(role -> role.getCode().name())
                        .sorted()
                        .collect(Collectors.joining(",")))
            .orElse("SYSTEM");
    try {
      repository.save(
          new OperationVersion(
              type,
              id,
              repository.lastRevision(type, id) + 1,
              action,
              reason,
              actor,
              roles,
              mapper.writeValueAsString(snapshot)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No se pudo conservar la versión de la operación.", exception);
    }
  }

  public void capture(
      OperationType type,
      UUID id,
      String action,
      String reason,
      java.util.function.Supplier<Object> snapshot,
      String actor) {
    capture(type, id, action, reason, snapshot.get(), actor);
  }

  @Transactional(readOnly = true)
  public Page<OperationVersionDtos.Version> find(OperationType type, UUID id, Pageable pageable) {
    return repository
        .findByOperationTypeAndOperationIdOrderByRevisionDesc(type, id, pageable)
        .map(
            version -> {
              try {
                return new OperationVersionDtos.Version(
                    version.getId(),
                    version.getRevision(),
                    version.getAction(),
                    version.getReason(),
                    version.getPerformedBy(),
                    version.getPerformedByRole(),
                    version.getCapturedAt(),
                    mapper.readTree(version.getSnapshotContent()));
              } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                    "No se pudo leer la versión conservada.", exception);
              }
            });
  }
}
