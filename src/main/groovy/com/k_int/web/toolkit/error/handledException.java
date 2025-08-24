package com.k_int.web.toolkit.error;

/**
 * A simple interface allowing custom error message handling per exception.
 *
 * <p>Any exception not caught within a request context will typically result in a generic
 * "Uncaught Internal server error" message in the response for security reasons.
 * This interface provides a mechanism to offer more specific and user-friendly error messages
 * for anticipated exceptions without requiring a {@code try/catch} block for every expected usage
 * of a service at the request level.</p>
 *
 * <p>When an exception implementing this interface is thrown and uncaught,
 * its {@link #handleException()} method will be invoked. The {@link ErrorHandle}
 * returned by this method will then be used to provide contextual information,
 * including an HTTP status code and a message, in the service response.</p>
 */
public interface handledException {
    /**
     * Provides an {@link ErrorHandle} containing the desired HTTP status code and
     * a contextual message for the exception.
     *
     * @return An {@link ErrorHandle} instance detailing the HTTP code and message
     * to be returned in the service response.
     */
    ErrorHandle handleException();
}