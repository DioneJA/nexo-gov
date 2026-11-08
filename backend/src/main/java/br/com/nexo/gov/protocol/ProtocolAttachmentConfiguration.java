package br.com.nexo.gov.protocol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Locale;

@Configuration
public class ProtocolAttachmentConfiguration {
    @Bean
    ProtocolAttachmentStore protocolAttachmentStore(
            @Value("${nexogov.attachments.endpoint-uri:}") String endpointValue,
            @Value("${nexogov.attachments.region:us-east-1}") String region,
            @Value("${nexogov.attachments.bucket}") String bucket,
            @Value("${nexogov.attachments.access-key:}") String accessKey,
            @Value("${nexogov.attachments.secret-key:}") String secretKey,
            org.springframework.core.env.Environment environment) {
        if (bucket == null || bucket.isBlank()) throw new IllegalStateException("NEXOGOV_STORAGE_BUCKET precisa ser configurado.");
        boolean hasAccessKey = accessKey != null && !accessKey.isBlank();
        boolean hasSecretKey = secretKey != null && !secretKey.isBlank();
        if (hasAccessKey != hasSecretKey) throw new IllegalStateException("Configure em conjunto NEXOGOV_STORAGE_ACCESS_KEY e NEXOGOV_STORAGE_SECRET_KEY.");
        URI endpoint = endpointValue == null || endpointValue.isBlank() ? null : URI.create(endpointValue);
        if (endpoint != null && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            boolean localProfile = environment.matchesProfiles("local", "test");
            boolean loopback = endpoint.getHost() != null && (endpoint.getHost().equalsIgnoreCase("localhost")
                    || endpoint.getHost().equals("127.0.0.1") || endpoint.getHost().equals("::1"));
            if (!(localProfile && loopback)) {
                throw new IllegalStateException("NEXOGOV_STORAGE_ENDPOINT_URI precisa usar HTTPS fora do loopback local/teste.");
            }
        }
        return new ProtocolAttachmentStore(ProtocolAttachmentStore.createClient(endpoint, region, accessKey, secretKey), bucket);
    }
}
