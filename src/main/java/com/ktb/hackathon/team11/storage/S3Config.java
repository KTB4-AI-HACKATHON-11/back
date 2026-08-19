package com.ktb.hackathon.team11.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "storage.local-enabled", havingValue = "false")
public class S3Config {
  @Bean
  S3Client s3Client(@Value("${storage.region}") String region) {
    return S3Client.builder().region(Region.of(region)).build();
  }

  @Bean
  S3Presigner s3Presigner(@Value("${storage.region}") String region) {
    return S3Presigner.builder().region(Region.of(region)).build();
  }
}
