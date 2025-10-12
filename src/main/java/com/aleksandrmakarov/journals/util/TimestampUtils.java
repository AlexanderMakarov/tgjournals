package com.aleksandrmakarov.journals.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import lombok.experimental.UtilityClass;

/** Service for handling timestamp conversions between database and Java objects. */
@UtilityClass
public class TimestampUtils {

  /**
   * Converts a DATETIME timestamp from the database to LocalDateTime.
   *
   * @param timestamp The timestamp from the database
   * @return LocalDateTime representation of the timestamp
   */
  public LocalDateTime fromTimestamp(Timestamp timestamp) {
    return timestamp != null ? timestamp.toLocalDateTime() : null;
  }

  /**
   * Converts a LocalDateTime to DATETIME timestamp for database storage.
   *
   * @param dateTime The LocalDateTime to convert
   * @return The timestamp for database storage
   */
  public Timestamp toTimestamp(LocalDateTime dateTime) {
    return dateTime != null ? Timestamp.valueOf(dateTime) : null;
  }
}
