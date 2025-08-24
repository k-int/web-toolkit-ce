package com.k_int.web.toolkit.error;

/**
 * Represents a standardized error response, encapsulating an HTTP status code
 * and a descriptive message. This class is typically used in conjunction with
 * the {@link handledException} interface to provide rich, contextual error
 * information back to the client, especially for anticipated exceptions
 * that don't need a full stack trace or generic "internal server error" message.
 */
public class ErrorHandle {

  /**
   * The HTTP status code associated with the error.
   * For example, 400 for Bad Request, 404 for Not Found, or 500 for Internal Server Error.
   */
  public int code;

  /**
   * A human-readable message providing details about the error.
   * This message can be displayed to the user or logged for debugging.
   */
  public String message;

  /**
   * Constructs a new {@code ErrorHandle} with a specified message and HTTP status code.
   *
   * @param msg The descriptive error message.
   * @param code The HTTP status code.
   */
  public ErrorHandle(String msg, int code) {
    this.code = code;
    this.message = msg;
  }

  /**
   * Constructs a new {@code ErrorHandle} with a specified message and defaults
   * the HTTP status code to 500 (Internal Server Error). This constructor is
   * useful when a specific HTTP code isn't provided, implying a general server-side issue.
   *
   * @param msg The descriptive error message.
   */
  public ErrorHandle(String msg) {
    this(msg, 500);
  }
}