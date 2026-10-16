package com.odontocare.documents.repository;

import com.odontocare.documents.model.DocumentConsent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DocumentConsentRepository
    extends JpaRepository<DocumentConsent, UUID>, JpaSpecificationExecutor<DocumentConsent> {}
