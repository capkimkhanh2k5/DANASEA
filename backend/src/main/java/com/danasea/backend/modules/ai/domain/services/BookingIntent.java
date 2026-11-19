package com.danasea.backend.modules.ai.domain.services;

import java.util.regex.Pattern;

public final class BookingIntent {
    private static final String BOOK = "(?:dat(?: cho| ve| tour| dich vu)?|giu cho|book(?:ing)?|reserv(?:e|ation))";
    private static final Pattern EXPLICIT = Pattern.compile("\\b(?:(?:muon|hay|can|giup toi|dong y|xac nhan) "
            + BOOK + "|(?:i want to|i would like to|please|confirm(?: my)?) " + BOOK
            + "|book (?:this|that|it|the)\\b|xac nhan (?:phuong an|luc nay))");
    private static final Pattern NEGATED = Pattern.compile("\\b(?:(?:chua|khong)(?: (?:muon|can|dong y))? "
            + BOOK + "|(?:don't|do not|not ready to|not trying to)(?: want to)? " + BOOK + ")\\b");
    private static final Pattern SEARCH_ONLY = Pattern.compile("\\b(?:chi (?:muon )?(?:tim|xem|tham khao)|just (?:looking|searching|browsing)|only (?:search|browse))\\b");

    private BookingIntent() {}

    public static boolean permitsConfirmation(String message) {
        String normalized = TravelQueryParser.normalize(message);
        return !isSearchOnly(normalized) && EXPLICIT.matcher(normalized).find();
    }

    public static boolean isSearchOnly(String message) {
        String normalized = TravelQueryParser.normalize(message);
        return NEGATED.matcher(normalized).find() || SEARCH_ONLY.matcher(normalized).find();
    }
}
