package uk.gov.hmcts.reform.tecpoc.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

    public static final ZoneId UK_ZONE_ID = ZoneId.of("Europe/London");

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
