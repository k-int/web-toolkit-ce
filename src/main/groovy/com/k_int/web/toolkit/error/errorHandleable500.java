package com.k_int.web.toolkit.error;

// A simple interface allowing 500 error message handling per exception
/*
 * Any exception which is not caught in a request context will result in an "Uncaught Internal server error"
 * message on the response (for security reasons). If it is relatively common to see a certain exception
 * in a certain service call, but not worth wrapping each expected usage of that service in a try/catch at the
 * request level, instead an exception implementing this can be thrown, and `handle500Message` will be used to
 * provide context in the response.
 */
public interface errorHandleable500 {
    String handle500Message();
}
