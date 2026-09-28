package com.keyloop.scheduler.booking.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("scheduler.booking")
record BookingProperties(@DefaultValue("5") int maxAttempts) {
}
