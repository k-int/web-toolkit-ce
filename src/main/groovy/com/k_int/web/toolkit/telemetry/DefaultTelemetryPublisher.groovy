package com.k_int.web.toolkit.telemetry;

import org.springframework.stereotype.Component
import grails.config.Config
import grails.core.GrailsApplication
import grails.events.annotation.Subscriber
import grails.util.Environment
import groovy.json.JsonOutput
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

@Slf4j
@PackageScope
class DefaultTelemetryPublisher implements TelemetryPublisher {
	private static final String DEFAULT_TARGET_URL = 'https://console-api.hosting.k-int.com/telemetry'
	
	private static final String CONFIG_PREFIX = 'telemetry.'
	private static final String CONFIG_TELEMETRY_ACTIVE = "${CONFIG_PREFIX}active"
	private static final String CONFIG_TELEMETRY_TARGET = "${CONFIG_PREFIX}target"
	private static final String CONFIG_TELEMETRY_ORG = "${CONFIG_PREFIX}hosting.org"
	private static final String CONFIG_TELEMETRY_ENV = "${CONFIG_PREFIX}hosting.env"
	
	final boolean active
	final String target
	final String hostingOrganization
	final String hostingEnvironment
	
	DefaultTelemetryPublisher(GrailsApplication grailsApplication) {
		final Config config = grailsApplication.getConfig()
		
		final String activeVal = config.getProperty(CONFIG_TELEMETRY_ACTIVE, String.class)
		
		active = "TRUE".equalsIgnoreCase( activeVal ) || (Environment.PRODUCTION == Environment.getCurrent());
	  target = config.getProperty(CONFIG_TELEMETRY_TARGET, String.class, DEFAULT_TARGET_URL)
	  hostingOrganization = config.getProperty(CONFIG_TELEMETRY_ORG, String.class)
	  hostingEnvironment = config.getProperty(CONFIG_TELEMETRY_ENV, String.class)
	}
	
	private void NOOP( TelemetryData telemetryData ) {
		log('TelemetryData [{}]', telemetryData)
	}

	private void handleThrowable(Throwable t) {
		if ( log.isTraceEnabled() ) t.printStackTrace()
	}
	
	private void handleResponseData ( responseCode, responseText ) {
		if ( !log.isTraceEnabled() ) return
		
		if ( responseText == null ) {
			log.trace('Response [status: [{}]]', responseCode)
		}
		
		
		log.trace('Response [status: [{}], content: [{}]]', responseCode, responseText)
	}
	
	private void log(String text, Object... vals) {
		if ( !log.isTraceEnabled() ) return
		
		log.trace(text, vals)
	}
	
	private Map<String, Object> toMap (TelemetryData telemetryData) {
		
		final Map<String, Object> defaults = [
			'env' :  hostingEnvironment,
			'org' :  hostingOrganization
		].collectEntries { key, val -> val != null ? [(key): val] : Collections.emptyMap() }
		
		
		return defaults + telemetryData.toDataMap()
	}
	
	@Override
  public void publish( TelemetryData telemetryData ) {
		try {
			
			if ( !active ) {
				NOOP(telemetryData)
				return
			}

      // Convert the payload to JSON format
      def jsonData = JsonOutput.toJson( toMap( telemetryData ))

      // Create a URL object
      URL url = new URL(target);
			
			log("Posting [{}] to [{}]", jsonData, target)

      // Open a connection to the URL		
      HttpURLConnection connection = (HttpURLConnection) url.openConnection()

      // Configure the connection
      connection.setRequestMethod('POST')
      connection.setDoOutput(true) // Enable output for sending data
      connection.setRequestProperty('Content-Type', 'application/json')
      connection.setRequestProperty('Accept', 'application/json')

      // Write the JSON data to the connection's output stream
      connection.outputStream.withWriter('UTF-8') { writer ->
        writer.write(jsonData)
      }

      // Get the response code
      int responseCode = connection.responseCode
			String responseText = null

      // Read the response for 2xx or error otherwise
			try {
	      responseText = connection.inputStream.withReader('UTF-8') { reader ->
	        reader.text
	      }
			} catch ( Exception ex ) {
				try {
					responseText = connection.errorStream?.withReader('UTF-8') { reader ->
						reader.text
					}
				} catch ( Exception errEx ) { /* Suppress */ }
			}
			
			handleResponseData ( responseCode, responseText )
		} catch (Throwable t) {
			try {
				handleThrowable(t)
			} catch ( Throwable t2 ) { /* SUPPRESS */ }
		}
  }

}
