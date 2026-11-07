package com.admision.lector_dbf.exam.model;

import com.admision.lector_dbf.importer.model.ImportModels.SectionScore;
import java.math.BigDecimal;
import java.util.List;

public record ResultVersionDetail(ResultVersion version, List<String> responses, List<String> key,
                                  BigDecimal correctPoints, BigDecimal incorrectPoints,
                                  BigDecimal blankPoints, List<SectionScore> sections) {}
