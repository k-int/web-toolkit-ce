package com.k_int.web.toolkit.grammar

import com.k_int.web.toolkit.error.handledException
import com.k_int.web.toolkit.error.ErrorHandle


class SimpleLookupServiceException extends Exception implements handledException {
  public static final Long GENERIC_ERROR = 0L
  public static final Long INVALID_PROPERTY = 1L

  final Long code
  final String contextString

  public SimpleLookupServiceException(String errorMessage, Long code, String contextString) {
    super(errorMessage)
    this.code = code
    this.contextString = contextString
  }

  public SimpleLookupServiceException(String errorMessage, Long code) {
    SimpleLookupServiceException(errorMessage, code, null)
  }

  public SimpleLookupServiceException(String errorMessage) {
    SimpleLookupServiceException(errorMessage, GENERIC_ERROR)
  }

  public ErrorHandle handleException() {
    ErrorHandle handle;
    switch(code) {
      case INVALID_PROPERTY:
        handle = new ErrorHandle("Failure in SimpleLookupService. Invalid property: ${contextString}", 400)
        break
      case GENERIC_ERROR:
      default:
        if (contextString) {
          handle = new ErrorHandle("Failure in SimpleLookupService. Context: ${contextString}")
        } else {
          handle = new ErrorHandle("Failure in SimpleLookupService")
        }
        break
    }

    return handle
  }
}
