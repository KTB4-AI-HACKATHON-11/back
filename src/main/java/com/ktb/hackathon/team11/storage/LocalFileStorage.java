package com.ktb.hackathon.team11.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "storage.local-enabled", havingValue = "true", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
  private final ConcurrentMap<String, byte[]> files = new ConcurrentHashMap<>();

  public StoredFile store(String key, byte[] bytes, String mime) {
    files.put(key, bytes.clone());
    return new StoredFile(key, createReadUrl(key, Duration.ofMinutes(5)));
  }

  public String createReadUrl(String key, Duration d) {
    return "https://local-storage.invalid/" + URLEncoder.encode(key, StandardCharsets.UTF_8);
  }

  public void delete(String key) {
    files.remove(key);
  }
}
