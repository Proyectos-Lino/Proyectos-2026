package com.odontocare.finance.repository;

import com.odontocare.finance.model.FinancialContent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinancialContentRepository
    extends JpaRepository<FinancialContent, UUID>, JpaSpecificationExecutor<FinancialContent> {}
