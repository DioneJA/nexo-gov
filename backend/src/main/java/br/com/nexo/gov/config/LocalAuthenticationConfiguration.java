package br.com.nexo.gov.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@Profile("local")
public class LocalAuthenticationConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService localUsers(
            PasswordEncoder encoder,
            @Value("${NEXOGOV_DEMO_USERNAME:servidor}") String username,
            @Value("${NEXOGOV_DEMO_PASSWORD}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(username).password(encoder.encode(password)).roles("SERVIDOR").build());
    }
}
