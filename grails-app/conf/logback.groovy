import grails.util.BuildSettings
import grails.util.Environment
import org.springframework.boot.logging.logback.ColorConverter
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter

import java.nio.charset.Charset

conversionRule 'clr', ColorConverter
conversionRule 'wex', WhitespaceThrowableProxyConverter

// See http://logback.qos.ch/manual/groovy.html for details on configuration
appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        charset = Charset.forName('UTF-8')

        pattern =
                '%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} ' + // Date
                        '%clr(%5p) ' + // Log level
                        '%clr(---){faint} %clr([%15.15t]){faint} ' + // Thread
                        '%clr(%-40.40logger{39}){cyan} %clr(:){faint} ' + // Logger
                        '%m%n%wex' // Message
    }
}


boolean devEnv = Environment.isDevelopmentMode() || Environment.currentEnvironment.name == 'vagrant-db'

if (devEnv || Environment.currentEnvironment == Environment.TEST) {

  // Change default verbosity to INFO for dev/test
  root(INFO, ['STDOUT'])

  // Increase specific levels to debug
  logger 'grails.app.init', DEBUG
  logger 'grails.app.controllers', DEBUG
  logger 'grails.app.domains', DEBUG
  logger 'grails.app.jobs', DEBUG
  logger 'grails.app.services', DEBUG
  logger 'com.zaxxer.hikari.pool.HikariPool', WARN

  logger 'com.k_int', DEBUG
  logger 'com.k_int.web.toolkit', DEBUG
  logger 'org.olf', DEBUG


  if (Environment.currentEnvironment == Environment.TEST) {
    // Test only.
    root(DEBUG, ['STDOUT'])
  }
}


def targetDir = BuildSettings.TARGET_DIR
if (Environment.isDevelopmentMode() && targetDir != null) {
    appender("FULL_STACKTRACE", FileAppender) {
        file = "${targetDir}/stacktrace.log"
        append = true
        encoder(PatternLayoutEncoder) {
            pattern = "%level %logger - %msg%n"
        }
    }
    logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
}
root(ERROR, ['STDOUT'])
