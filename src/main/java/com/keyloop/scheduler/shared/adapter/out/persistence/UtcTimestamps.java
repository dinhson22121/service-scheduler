package com.keyloop.scheduler.shared.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class UtcTimestamps {

    private UtcTimestamps() {
    }

    public static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
