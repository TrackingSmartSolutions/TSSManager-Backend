package com.tss.tssmanager_backend.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateUtils {
    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    public static LocalDateTime nowInMexico() {
        return LocalDateTime.now(MEXICO_ZONE);
    }
}