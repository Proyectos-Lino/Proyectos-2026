package com.odontocare.users.repository;

import com.odontocare.users.model.UserAccount;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository
    extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<UserAccount> findByUsername(String username);

  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  @Query("select u from UserAccount u where u.id = :id")
  Optional<UserAccount> findWithAccessById(@Param("id") UUID id);

  boolean existsByUsernameAndIdNot(String username, UUID id);

  boolean existsByUsername(String username);

  @Query(
      "select count(u) from UserAccount u join u.roles r where u.active = true and r.code ="
          + " 'ADMIN'")
  long countActiveAdministrators();
}
