package com.labcore.platform.organization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationAuthorizedSignerRepository
    extends JpaRepository<OrganizationAuthorizedSigner, OrganizationAuthorizedSigner.Key> {
  List<OrganizationAuthorizedSigner> findByOrganizationIdOrderByDisplayOrderAsc(
      UUID organizationId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from OrganizationAuthorizedSigner signer where signer.organizationId = :organizationId")
  void deleteByOrganizationId(@Param("organizationId") UUID organizationId);
}
