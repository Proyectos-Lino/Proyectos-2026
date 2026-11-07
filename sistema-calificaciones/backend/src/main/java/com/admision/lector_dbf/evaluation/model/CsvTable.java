package com.admision.lector_dbf.evaluation.model;

import java.util.List;
import java.util.Map;

public record CsvTable(List<String> columns, List<Row> rows) {
    public record Row(int row, Map<String, String> values) {}
}
