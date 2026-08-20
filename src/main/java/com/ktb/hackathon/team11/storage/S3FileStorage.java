package com.ktb.hackathon.team11.storage;

import com.ktb.hackathon.team11.global.exception.*;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnProperty(name = "storage.local-enabled", havingValue = "false")
public class S3FileStorage implements FileStorage {
  private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;

  public S3FileStorage(S3Client c, S3Presigner p, @Value("${storage.bucket}") String b) {
    client = c;
    presigner = p;
    bucket = b;
  }

  public StoredFile store(String key, byte[] bytes, String mime) {
    try {
      client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime).build(),
          RequestBody.fromBytes(bytes));
      return new StoredFile(key, createReadUrl(key, Duration.ofMinutes(5)));
    } catch (S3Exception e) {
      log.error(
          "S3 upload failed bucket={} key={} status={} code={} requestId={}",
          bucket,
          key,
          e.statusCode(),
          e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode(),
          e.requestId());
      throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
    } catch (RuntimeException e) {
      log.error(
          "S3 upload failed before receiving an AWS response bucket={} key={} type={} message={}",
          bucket,
          key,
          e.getClass().getSimpleName(),
          e.getMessage());
      throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
    }
  }

  public String createReadUrl(String key, Duration valid) {
    try {
      long cacheSeconds = Math.max(0, Math.min(valid.toSeconds(), 300));
      return presigner
          .presignGetObject(
              GetObjectPresignRequest.builder()
                  .signatureDuration(valid)
                  .getObjectRequest(
                      b ->
                          b.bucket(bucket)
                              .key(key)
                              .responseCacheControl("private, max-age=" + cacheSeconds))
                  .build())
          .url()
          .toString();
    } catch (RuntimeException e) {
      throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
    }
  }

  public void delete(String key) {
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (RuntimeException ignored) {
    }
  }
}
