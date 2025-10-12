package com.aleksandrmakarov.journals.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.experimental.UtilityClass;

/** Service for handling timestamp conversions between database and Java objects. */
@UtilityClass
public class TimestampUtils {

  /**
   * Converts a millisecond timestamp from the database to LocalDateTime. Uses the system default
   * timezone for conversion.
   *
   * @param timestampMillis The timestamp in milliseconds from epoch
   * @return LocalDateTime representation of the timestamp
   */
  public LocalDateTime fromMillis(long timestampMillis) {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault());
  }

  /**
   * Converts a LocalDateTime to millisecond timestamp for database storage. Uses the system default
   * timezone for conversion.
   *
   * @param dateTime The LocalDateTime to convert
   * @return The timestamp in milliseconds from epoch
   */
  public long toMillis(LocalDateTime dateTime) {
    return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}
