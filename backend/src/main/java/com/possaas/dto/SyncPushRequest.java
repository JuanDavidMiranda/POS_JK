package com.possaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncPushRequest(
        @Valid @NotEmpty List<SyncEventDto> events
) {
}
