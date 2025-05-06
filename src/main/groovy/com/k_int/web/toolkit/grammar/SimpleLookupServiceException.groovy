package com.k_int.web.toolkit.grammar

import com.k_int.web.toolkit.error.errorHandleable500

class SimpleLookupServiceException extends Exception implements errorHandleable500 {
  public static final Long GENERIC_ERROR = 0L
  public static final Long INVALID_PROPERTY = 1L

  final Long code

  final String contextString

  SimpleLookupServiceException(String errorMessage, Long code, String contextString) {
    super(errorMessage)
    this.code = code
    this.contextString = contextString
  }

  SimpleLookupServiceException(String errorMessage, Long code) {
    SimpleLookupServiceException(errorMessage, code, null)
  }

  SimpleLookupServiceException(String errorMessage) {
    SimpleLookupServiceException(errorMessage, GENERIC_ERROR)
  }

  String handle500Message() {
    String msg500
    switch(code) {
      case INVALID_PROPERTY:
        msg500 = "Failure in SimpleLookupService. Invalid property: ${contextString}"
        break
      case GENERIC_ERROR:
      default:
        if (contextString) {
          msg500 = "Failure in SimpleLookupService. Context: ${contextString}"
        } else {
          msg500 = "Failure in SimpleLookupService"
        }
        break
    }

    return msg500
  }
}
