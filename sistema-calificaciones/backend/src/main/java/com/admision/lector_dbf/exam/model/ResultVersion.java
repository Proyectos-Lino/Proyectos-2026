package com.admision.lector_dbf.exam.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ResultVersion(long id, int version, int keyVersion, int ruleVersion,
                            Integer sectionVersion, int correct, int incorrect, int blank,
                            BigDecimal score, String reason, OffsetDateTime calculatedAt) {}
