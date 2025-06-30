package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.error.handledException
import com.k_int.web.toolkit.error.ErrorHandle

class CustomPropertyException extends Exception implements handledException {
  public static final Long GENERIC_ERROR = 0L
  public static final Long INVALID_TYPE = 1L

  final Long code
  final String contextString

  public CustomPropertyException(String errorMessage, Long code, String contextString) {
    super(errorMessage)
    this.code = code
    this.contextString = contextString
  }

  public CustomPropertyException(String errorMessage, Long code) {
    CustomPropertyException(errorMessage, code, null)
  }

  public CustomPropertyException(String errorMessage) {
    CustomPropertyException(errorMessage, GENERIC_ERROR)
  }

  public ErrorHandle handleException() {
    ErrorHandle handle;
    switch(code) {
      case INVALID_TYPE:
        handle = new ErrorHandle("Type: ${contextString} is invalid", 422)
        break
      case GENERIC_ERROR:
      default:
        if (contextString) {
          handle = new ErrorHandle("CustomPropertyException. Context: ${contextString}")
        } else {
          handle = new ErrorHandle("CustomPropertyException")
        }
        break
    }

    return handle
  }
}
