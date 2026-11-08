package br.com.nexo.gov.protocol;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.io.IOException;
import java.util.UUID;

/** Private object storage for protocol attachments. Keys never contain user filenames. */
public final class ProtocolAttachmentStore {
    private final S3Client client;
    private final String bucket;

    public ProtocolAttachmentStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public String objectKey(UUID organizationId, UUID protocolId, UUID attachmentId) {
        return organizationId + "/" + protocolId + "/" + attachmentId;
    }

    public void put(String key, byte[] content) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType("application/octet-stream").build(), RequestBody.fromBytes(content));
    }

    public StoredObject get(String key, long maximumBytes) {
        if (maximumBytes < 1 || maximumBytes >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Limite de leitura do objeto fora do intervalo suportado.");
        try (ResponseInputStream<GetObjectResponse> object = client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).build())) {
            long declaredSize = object.response().contentLength();
            if (declaredSize > maximumBytes) throw new IllegalStateException("O objeto armazenado excede o limite de leitura configurado.");
            byte[] content = object.readNBytes(Math.toIntExact(maximumBytes + 1));
            if (content.length > maximumBytes) throw new IllegalStateException("O objeto armazenado excede o limite de leitura configurado.");
            return new StoredObject(content, declaredSize);
        } catch (IOException failure) {
            throw new IllegalStateException("Não foi possível ler o objeto de anexo armazenado.", failure);
        }
    }

    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public record StoredObject(byte[] content, long sizeBytes) {}

    public static S3Client createClient(URI endpoint, String region, String accessKey, String secretKey) {
        var builder = S3Client.builder().region(Region.of(region))
                .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(endpoint != null).build());
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create());
        }
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        return builder.build();
    }
}
