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
            @Value("${NEXOGOV_DEMO_PASSWORD}") String password,
            @Value("${NEXOGOV_DEMO_MEMBER_USERNAME:servidor.teste}") String memberUsername,
            @Value("${NEXOGOV_DEMO_MEMBER_PASSWORD:${NEXOGOV_DEMO_PASSWORD}}") String memberPassword) {
        if (username.equals(memberUsername)) {
            throw new IllegalArgumentException("As contas de demonstração devem ter nomes diferentes.");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("SERVIDOR").build(),
                User.withUsername(memberUsername).password(encoder.encode(memberPassword)).roles("SERVIDOR").build());
    }
}
