package com.k_int.web.toolkit

import com.k_int.web.toolkit.error.errorHandleable500;

import com.k_int.web.toolkit.grammar.SimpleLookupServiceException
import grails.core.GrailsApplication
import java.time.Instant

class ErrorController {

  GrailsApplication grailsApplication // Inject GrailsApplication

  def handle500() {
    Throwable ex = request.getAttribute('javax.servlet.error.exception') ?: request.getAttribute('exception')
    if (ex?.cause) {
      ex = ex.cause
    }

    String message;

    // Individual error handling. If exception implements errorHandleable500 then we can get a String message from it
    if (ex instanceof errorHandleable500) {
      message = ex.handle500Message()
    }


    // Fetch config property from the application using the plugin
    Boolean includeStack = Boolean.valueOf(grailsApplication.config.getProperty('webtoolkit.endpoints.includeStackTraceFor500', String, 'false'))

    def model = [
      timestamp    : Instant.now().toString(),
      exception    : ex,
      includeStack : includeStack,
      message      : message
    ]

    response.status = 500
    // Explicitly render the standard '/error' view name.
    // Grails will look for /grails-app/views/error.gson first in the app,
    // then fall back to the one in the plugin.
    render(view: '/error', model: model)
  }
}