package com.tss.tssmanager_backend.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateUtils {
    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    public static LocalDateTime nowInMexico() {
        return LocalDateTime.now(MEXICO_ZONE);
    }

    public static Instant localDateToInstantStartOfDay(LocalDate date) {
        return date.atStartOfDay(MEXICO_ZONE).toInstant();
    }

    public static Instant localDateToInstantEndOfDay(LocalDate date) {
        return date.atStartOfDay(MEXICO_ZONE).plusDays(1).toInstant();
    }

    public static LocalDate instantToLocalDateInMexico(Instant instant) {
        return instant.atZone(MEXICO_ZONE).toLocalDate();
    }

    public static LocalDateTime instantToLocalDateTimeInMexico(Instant instant) {
        return instant.atZone(MEXICO_ZONE).toLocalDateTime();
    }
}