package com.admision.lector_dbf.evaluation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Preparation(String status, boolean approved, boolean editable, List<Check> setup, List<Check> publication) {
    public record Check(String code, String label, boolean completed, long pending) {}

    @JsonProperty
    public boolean readyToApprove() {
        return !approved && "CERRADA".equals(status) && publication.stream().allMatch(Check::completed);
    }

    @JsonProperty
    public boolean readyToPublish() {
        return approved && "CERRADA".equals(status) && publication.stream()
                .allMatch(Check::completed);
    }
}
