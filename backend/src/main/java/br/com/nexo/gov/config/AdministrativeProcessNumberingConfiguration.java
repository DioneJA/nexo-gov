package br.com.nexo.gov.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AdministrativeProcessNumberingConfiguration {
    @Bean
    Clock administrativeProcessNumberingClock(
            @Value("${nexogov.process.numbering-zone-id:America/Sao_Paulo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
