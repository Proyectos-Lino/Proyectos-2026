package com.odontocare.installation.service;

import com.odontocare.installation.model.InstallationProfile;
import com.odontocare.installation.repository.InstallationProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ConfigurationLock {
  private final InstallationProfileRepository repository;

  public ConfigurationLock(InstallationProfileRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public InstallationProfile acquire() {
    return repository
        .lockInstallation()
        .orElseThrow(() -> new IllegalStateException("Installation missing"));
  }
}
