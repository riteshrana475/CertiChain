package com.certichain.wallet;

import com.certichain.crypto.CanonicalHasher;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class WalletObjectStorage {

    private final S3Client client;
    private final String bucket;

    public WalletObjectStorage(
            @Value("${certichain.storage.endpoint}") String endpoint,
            @Value("${certichain.storage.access-key}") String accessKey,
            @Value("${certichain.storage.secret-key}") String secretKey,
            @Value("${certichain.storage.bucket}") String bucket) {
        this.bucket = bucket;
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    public String put(byte[] bytes, String contentType) {
        ensureBucket();
        String key = "wallet/" + UUID.randomUUID();
        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
        return key;
    }

    public byte[] get(String key) {
        if (key == null || key.contains("..") || !key.startsWith("wallet/")) {
            throw new IllegalArgumentException("That file is not available.");
        }
        return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    public static String sha256(byte[] bytes) {
        return CanonicalHasher.sha256(bytes);
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
