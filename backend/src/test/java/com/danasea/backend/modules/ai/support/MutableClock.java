package com.danasea.backend.modules.ai.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

public final class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = Objects.requireNonNull(initialInstant, "initialInstant must not be null");
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneOffset.UTC);
    }

    public static MutableClock epoch() {
        return new MutableClock(Instant.EPOCH, ZoneOffset.UTC);
    }

    public static MutableClock of(Instant instant) {
        return new MutableClock(instant, ZoneOffset.UTC);
    }

    public synchronized void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        this.currentInstant = this.currentInstant.plus(duration);
    }

    public synchronized void setInstant(Instant newInstant) {
        this.currentInstant = Objects.requireNonNull(newInstant, "newInstant must not be null");
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zone)) {
            return this;
        }
        return new MutableClock(instant(), zone);
    }

    @Override
    public synchronized Instant instant() {
        return currentInstant;
    }
}
