package com.k_int.web.toolkit

import grails.config.Config
import spock.lang.Specification

import grails.testing.web.controllers.ControllerUnitTest
import grails.core.GrailsApplication

import com.k_int.web.toolkit.error.handledException;
import com.k_int.web.toolkit.error.ErrorHandle
import spock.lang.Unroll;

class ErrorControllerSpec extends Specification implements ControllerUnitTest<ErrorController> {
  class ErrorControllerSpecException extends Exception implements handledException {
    public ErrorControllerSpecException(String errorMessage) {
      super(errorMessage)
    }

    public ErrorHandle handleException() {
      ErrorHandle handle;

      handle = new ErrorHandle("ErrorControllerSpecException thrown: ${this.message}", 308)

      return handle
    }
  }

  @Unroll
  def "handle500 with #exceptionType.simpleName and config.includeStack=#includeStack should render expected values"() {
    given: 'An exception in request attributes'
      def ex = exceptionInstance
      controller.request.setAttribute('jakarta.servlet.error.exception', ex)

    and: 'grailsApplication config is mocked'
      def mockGrailsApp = Mock(GrailsApplication)
      mockGrailsApp.getConfig() >> Mock(Config) {
        getProperty('endpoints.include-stack-trace', String, 'false') >> includeStack
      }
      controller.grailsApplication = mockGrailsApp

    when: 'Calling handle500'
      controller.handle500()

    then: 'The expected values are rendered'
      view == '/error'
      model.message == expectedMessage
      model.code == expectedCode
      model.exception == ex
      model.includeStack.toString() == includeStack // convert to string to match exact config
      response.status == expectedCode

    where:
    // Combine all possibilities
    [exceptionType, exceptionInstance, expectedMessage, expectedCode, includeStack] << [
      [
        exceptionType    : ErrorControllerSpecException,
        exceptionInstance: new ErrorControllerSpecException("boom"),
        expectedMessage  : "ErrorControllerSpecException thrown: boom",
        expectedCode     : 308
      ],
      [
        exceptionType    : RuntimeException,
        exceptionInstance: new RuntimeException("oops"),
        expectedMessage  : "Uncaught Internal server error",
        expectedCode     : 500
      ]
    ].collectMany { base ->
      ['true', 'false'].collect { stack ->
        [base.exceptionType, base.exceptionInstance, base.expectedMessage, base.expectedCode, stack]
      }
    }
  }
}
