package com.arcx.ctfplatform.competitions.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record LinkTasksRequest(
        @NotNull List<UUID> taskIds
) {
}
