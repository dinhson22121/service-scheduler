package com.keyloop.scheduler.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class TestDates {

    public static final ZoneId LONDON = ZoneId.of("Europe/London");
    public static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");

    private TestDates() {
    }

    public static LocalDate workingDay(int days) {
        LocalDate date = LocalDate.now(LONDON).plusDays(days);
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? date.plusDays(1) : date;
    }

    public static OffsetDateTime at(LocalDate date, String localTime, ZoneId zone) {
        return date.atTime(LocalTime.parse(localTime)).atZone(zone).toOffsetDateTime();
    }

    public static OffsetDateTime london(LocalDate date, String localTime) {
        return at(date, localTime, LONDON);
    }
}
