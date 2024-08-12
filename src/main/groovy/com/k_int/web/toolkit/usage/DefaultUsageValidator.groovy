package com.k_int.web.toolkit.usage;

import groovy.json.JsonOutput
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest;

public class DefaultUsageValidator implements UsageValidator {

  private static final String CONTEXT_REGISTRY = 'https://console-api.platform.k-int.com/toolkit/contextRegistry/validate';
  private static Set knownContexts = [
    'b2f3aac175664953c3bdbe1faa72de63',
    '237b0da9d28c8ec0bc6f7fb85a6038e5',
    '12cfb950528672643af2882e4c147c1b',
    '118af403cfe3322815d7202530f87310',
    'e435c3baa5f9b99ce2ec7175a95397e7',
    '730553da76b0f85cb37f79bfa93b6e40'
  ];

  public boolean validateUsageContext(String contextHash, String contextName) {
    if ( knownContexts.includes(contextHash) )
      return true;
    else
      return fullCheck(contextHash,contextName);
  }

  public String getModuleSignature(String key) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    byte[] digest = md.digest(key.bytes)
    String context = digest.collect { String.format("%02x", it) }.join()
    return signature;
  }


  private boolean fullCheck(String contextHash, String contextName) {
    
    boolean result = false;

    try {

      def jsonPayload = [
        contextHash: contextHash,
        contextName: contextName
      ]

      // Convert the payload to JSON format
      def jsonData = JsonOutput.toJson(jsonPayload)

      // Create a URL object
      URL url = new URL(CONTEXT_REGISTRY)
        
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

    return true;
  }
}
