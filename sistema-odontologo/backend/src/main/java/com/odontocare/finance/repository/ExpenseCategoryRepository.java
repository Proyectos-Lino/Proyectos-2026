package com.odontocare.finance.repository;

import com.odontocare.finance.model.ExpenseCategory;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ExpenseCategoryRepository
    extends JpaRepository<ExpenseCategory, UUID>, JpaSpecificationExecutor<ExpenseCategory> {}
