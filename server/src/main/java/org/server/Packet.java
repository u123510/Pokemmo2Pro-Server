package org.server;

import org.server.bytes.ByteBufEx;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HexFormat;

public abstract class Packet {
  private static final HexFormat HEX = HexFormat.ofDelimiter(" ");

  public Packet() {}
  abstract public void encode(ByteBufEx buffer) throws Exception;
  abstract public void decode(ByteBufEx buffer);
  abstract public void handle(Session session) throws Exception;

  /**
   * Returns decoded packet fields for trace logging without requiring every
   * packet implementation to maintain a separate toString method.
   */
  public final String debugDescription() {
    StringBuilder description = new StringBuilder(getClass().getSimpleName()).append('{');
    boolean first = true;
    for (Class<?> type = getClass(); type != null && Packet.class.isAssignableFrom(type); type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || field.isSynthetic() || isInjected(field)) {
          continue;
        }

        if (!first) {
          description.append(", ");
        }
        first = false;
        description.append(field.getName()).append('=');
        try {
          if (!field.trySetAccessible()) {
            description.append("<inaccessible>");
          } else {
            Object value = field.get(this);
            description.append(isSensitiveField(field) ? formatRedactedValue(value) : formatDebugValue(value));
          }
        } catch (IllegalAccessException | RuntimeException e) {
          description.append("<error:").append(e.getClass().getSimpleName()).append('>');
        }
      }
    }
    return description.append('}').toString();
  }

  /**
   * Lets the session suppress raw payload logging for packets that contain
   * credentials, tokens, session keys, or hardware identifiers.
   */
  public final boolean containsSensitiveData() {
    return containsSensitiveFields(getClass());
  }

  public static boolean containsSensitiveFields(Class<? extends Packet> packetClass) {
    for (Class<?> type = packetClass; type != null && Packet.class.isAssignableFrom(type); type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()
            && !isInjected(field) && isSensitiveField(field)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isInjected(Field field) {
    for (Annotation annotation : field.getDeclaredAnnotations()) {
      String annotationName = annotation.annotationType().getName();
      if (annotationName.equals("com.google.inject.Inject") || annotationName.equals("javax.inject.Inject")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSensitiveField(Field field) {
    String fieldName = field.getName().toLowerCase();
    return fieldName.contains("password")
        || fieldName.contains("token")
        || fieldName.contains("credential")
        || fieldName.contains("sessionkey")
        || fieldName.contains("session_key")
        || fieldName.contains("privatekey")
        || fieldName.contains("private_key")
        || fieldName.contains("secret")
        || fieldName.contains("hwid")
        || fieldName.contains("hardware");
  }

  private static String formatRedactedValue(Object value) {
    if (value == null) {
      return "<redacted>";
    }
    if (value instanceof CharSequence sequence) {
      return "<redacted length=" + sequence.length() + '>';
    }
    if (value.getClass().isArray()) {
      return "<redacted length=" + Array.getLength(value) + '>';
    }
    return "<redacted type=" + value.getClass().getSimpleName() + '>';
  }

  private static String formatDebugValue(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String string) {
      return '"' + string.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
    if (value instanceof byte[] bytes) {
      return "byte[" + bytes.length + "]{" + HEX.formatHex(bytes) + '}';
    }
    if (value instanceof Byte byteValue) {
      return byteValue + String.format(" (0x%02X)", Byte.toUnsignedInt(byteValue));
    }
    if (value instanceof Short shortValue) {
      return shortValue + String.format(" (0x%04X)", Short.toUnsignedInt(shortValue));
    }
    if (value instanceof Integer intValue) {
      return intValue + String.format(" (0x%08X)", intValue);
    }
    if (value instanceof Long longValue) {
      return longValue + String.format(" (0x%016X)", longValue);
    }
    if (!value.getClass().isArray()) {
      return String.valueOf(value);
    }

    int length = Array.getLength(value);
    StringBuilder array = new StringBuilder("[");
    for (int index = 0; index < length; index++) {
      if (index > 0) {
        array.append(", ");
      }
      array.append(formatDebugValue(Array.get(value, index)));
    }
    return array.append(']').toString();
  }

  /**
   * Returns whether handling this packet may wait for a long-running action.
   * Long-running packets use a separate per-session queue so they do not delay
   * latency-sensitive packets such as player movement.
   */
  public boolean isLongRunning() {
    return false;
  }
}
