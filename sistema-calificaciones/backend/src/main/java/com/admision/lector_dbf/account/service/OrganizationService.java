package com.admision.lector_dbf.account.service;

import com.admision.lector_dbf.account.model.AccountModels.Organization;
import com.admision.lector_dbf.account.repository.AccountRepository;
import com.admision.lector_dbf.account.repository.OrganizationProfileRepository;
import com.admision.lector_dbf.account.model.OrganizationProfileInput;
import com.admision.lector_dbf.account.model.OrganizationProfile;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
    private final AccountRepository repository;
    private final OrganizationAccess access;
    private final OrganizationProfileRepository profiles;

    public OrganizationService(AccountRepository repository, OrganizationAccess access,OrganizationProfileRepository profiles) {
        this.repository = repository;
        this.access = access;
        this.profiles=profiles;
    }

    @Transactional
    public Organization create(long currentOrganizationId, String code, String name,OrganizationProfileInput profile, Authentication auth) {
        long userId = access.require(currentOrganizationId, auth, "ADMINISTRADOR").id();
        long id = repository.createOrganization(code.trim(), name.trim());
        repository.createMembership(id, userId, "ADMINISTRADOR");
        profiles.save(id,profile);
        return repository.organization(id, userId).orElseThrow();
    }

    public Organization get(long id, Authentication auth) {
        long userId = access.require(id, auth).id();
        return repository.organization(id, userId).orElseThrow(() -> ApiException.notFound("Organización"));
    }

    @Transactional
    public Organization update(long id, String name,OrganizationProfileInput profile, Authentication auth) {
        long userId = access.require(id, auth, "ADMINISTRADOR").id();
        if (repository.updateOrganization(id, name.trim()) == 0) throw ApiException.notFound("Organización");
        profiles.save(id,profile);
        return repository.organization(id, userId).orElseThrow();
    }

    public OrganizationProfile profile(long id,Authentication auth) {
        access.require(id,auth);
        return profiles.get(id);
    }
}
