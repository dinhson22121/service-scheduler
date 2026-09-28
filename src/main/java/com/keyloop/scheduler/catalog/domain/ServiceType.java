package com.keyloop.scheduler.catalog.domain;

import java.time.Duration;

public record ServiceType(long id, String code, String name, Duration duration, String requiredSkill,
                          String requiredBayType) {
}
