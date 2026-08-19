package com.ktb.hackathon.team11.storage;

import com.ktb.hackathon.team11.global.exception.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PhotoInspector {
  private static final int MAX = 10 * 1024 * 1024;

  public InspectedPhoto inspect(MultipartFile file) {
    try {
      if (file.isEmpty() || file.getSize() > MAX)
        throw new BusinessException(ErrorCode.INVALID_PHOTO);
      byte[] b = file.getBytes();
      String mime;
      String ext;
      if (b.length >= 3 && (b[0] & 255) == 0xff && (b[1] & 255) == 0xd8 && (b[2] & 255) == 0xff) {
        mime = "image/jpeg";
        ext = "jpg";
      } else if (b.length >= 8
          && (b[0] & 255) == 0x89
          && b[1] == 0x50
          && b[2] == 0x4e
          && b[3] == 0x47) {
        mime = "image/png";
        ext = "png";
      } else if (b.length >= 12
          && new String(b, 0, 4).equals("RIFF")
          && new String(b, 8, 4).equals("WEBP")) {
        mime = "image/webp";
        ext = "webp";
      } else throw new BusinessException(ErrorCode.INVALID_PHOTO);
      String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
      return new InspectedPhoto(b, mime, ext, b.length, sha);
    } catch (BusinessException e) {
      throw e;
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new BusinessException(ErrorCode.INVALID_PHOTO);
    }
  }

  public record InspectedPhoto(
      byte[] bytes, String mimeType, String extension, long sizeBytes, String sha256) {}
}
