package com.k_int.web.toolkit

import com.k_int.web.toolkit.error.handledException;
import com.k_int.web.toolkit.error.ErrorHandle;

import com.k_int.web.toolkit.grammar.SimpleLookupServiceException
import grails.core.GrailsApplication
import groovy.util.logging.Slf4j

import java.time.Instant

@Slf4j
class ErrorController {

  GrailsApplication grailsApplication // Inject GrailsApplication

  def handle500() {
    try {
      Throwable ex = request.getAttribute('javax.servlet.error.exception') ?: request.getAttribute('exception')
      if (ex?.cause) {
        ex = ex.cause
      }

      throw new Exception("Oops")

      String message = "Uncaught Internal server error";
      int code = 500;

      // Individual error handling. If exception implements errorHandleable500 then we can get a String message from it
      if (ex instanceof handledException) {
        ErrorHandle err = ex.handleException()
        message = err.message;
        code = err.code;
      }


      // Fetch config property from the application using the plugin
      Boolean includeStack = Boolean.valueOf(grailsApplication.config.getProperty('endpoints.include-stack-trace', String, 'false'))

      def model = [
        timestamp   : Instant.now().toString(),
        exception   : ex,
        includeStack: includeStack,
        message     : message,
        code        : code
      ]

      response.status = code;
      // Explicitly render the standard '/error' view name.
      // Grails will look for /grails-app/views/error.gson first in the app,
      // then fall back to the one in the plugin.
      render(view: '/error', model: model)
    } catch (Throwable t) {
      // try to get the original exception
      Throwable originalException = request.getAttribute('javax.servlet.error.exception')
      log.error("Exception occurred within the WebToolkit ErrorController. The original exception being handled was [{}].", originalException?.message, t)

      response.status = 500
      response.setContentType('application/json')
      response.writer << """
        {
          "timestamp": "${Instant.now().toString()}",
          "status": 500,
          "error": "Internal Server Error",
          "message": "An error occurred while attempting to handle another error. Please check the logs for details.",
          "path": "${request.forwardURI}"
        }
      """.stripIndent()
    }
  }
}