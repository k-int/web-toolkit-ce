package com.k_int.web.toolkit

import grails.core.GrailsApplication
import java.time.Instant

class ErrorController {

  GrailsApplication grailsApplication // Inject GrailsApplication

  def handle500() {
    Throwable ex = request.getAttribute('javax.servlet.error.exception') ?: request.getAttribute('exception')
    if (ex?.cause) {
      ex = ex.cause
    }

    // Fetch config property from the application using the plugin
    Boolean includeStack = Boolean.valueOf(grailsApplication.config.getProperty('webtoolkit.endpoints.includeStackTraceFor500', String, 'false'))

    def model = [
        timestamp: Instant.now().toString(),
        exception         : ex,
        includeStack : includeStack,
    ]

    response.status = 500
    // Explicitly render the standard '/error' view name.
    // Grails will look for /grails-app/views/error.gson first in the app,
    // then fall back to the one in the plugin.
    render(view: '/error', model: model)
  }
}