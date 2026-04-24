package com.arcx.ctfplatform.common.time;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TimeProvider {

    private final Clock clock;

    public Instant now() {
        return Instant.now(clock);
    }
}
