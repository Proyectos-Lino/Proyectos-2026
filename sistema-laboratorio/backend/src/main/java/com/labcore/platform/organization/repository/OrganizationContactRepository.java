package com.labcore.platform.organization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationContactRepository extends JpaRepository<OrganizationContact, UUID> {
  List<OrganizationContact> findByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from OrganizationContact contact where contact.organizationId = :organizationId")
  void deleteByOrganizationId(@Param("organizationId") UUID organizationId);
}
