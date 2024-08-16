package com.k_int.web.toolkit.telemetry;

import grails.util.Environment

public class DefaultTelemetry implements Telemetry {

  public void telementryEvent(String event, String tenant, Map<String,Object> params) {

    Environment current = Environment.getCurrent();
    if ( Environment.PRODUCTION == current) {
      try {
  		  String TELEMETRY_TARGET = system.getEnv("TELEMETRY_TARGET") ?: 'https://console-api.hosting.k-int.com/telemetry';
        String hosting_org = System.getEnv("HOSTING_ORG") ?: 'UNKNOWN';
        String hosting_env = System.getEnv("HOSTING_ENV") ?: 'UNKNOWN';
  
        def jsonPayload = [
          event: event,
          org: hosting_org,
          env: hosting_env,
          tenant: tenant,
          params: params
        ]
  
        // Convert the payload to JSON format
        def jsonData = JsonOutput.toJson(jsonPayload)
  
        // Create a URL object
        URL url = new URL(TELEMETRY_TARGET);
  
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
        println "Response Code: ${responseCode}"
  
        // Read the response
        if (responseCode == HttpURLConnection.HTTP_OK) { // Success
          connection.inputStream.withReader('UTF-8') { reader ->
            // println "Response: ${reader.text}"
            result = true;
          }
        } else {
          //println "POST request failed"
        }
  
      } catch (Exception e) {
          // e.printStackTrace()
      }
    }
  }

}
