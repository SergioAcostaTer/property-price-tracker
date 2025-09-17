package dev.propprice.co.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class Hashing {
  public static String md5(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(32);
      for (byte b : d)
        sb.append(String.format(Locale.ROOT, "%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
