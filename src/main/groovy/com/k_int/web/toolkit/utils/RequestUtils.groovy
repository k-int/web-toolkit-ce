package com.k_int.web.toolkit.utils

import javax.servlet.http.HttpServletRequest

class RequestUtils {
  public static final String HEADER_FORWARDED = 'forwarded'
  public static final String HEADER_FORWARDED_HOST = 'X-Forwarded-Host'
  public static final String HEADER_FORWARDED_PROTO = 'X-Forwarded-Proto'
  public static final String HEADER_HOST = 'host'
  
  public static final String getOriginalServerURL(HttpServletRequest request) {
    
    String derrivedServer = null
    Map<String,String> details = [:]
    
    // Check for the Forwarded header.
    String headerInfo = request.getHeader(HEADER_FORWARDED)
    if (headerInfo) {
      
      details = headerInfo.split(/\s*;\s*/).inject(details) { Map<String, String> map, String token ->
        token.split(/\s*=\s*/).with { map[it[0]] = it[1] }
        map
      }
      
    } else {
      headerInfo = request.getHeader(HEADER_HOST) ?: request.getHeader(HEADER_FORWARDED_HOST)
      if (headerInfo) {
        details['host'] = headerInfo
      }
      headerInfo = request.getHeader(HEADER_FORWARDED_PROTO) ?: request.scheme
      if (headerInfo) {
        details['proto'] = headerInfo
      }
    }
    
    if (headerInfo) {
      String[] hostAndPort = details['host'].split( ':' )
      
      String host = hostAndPort[0]
      String port = hostAndPort.length > 1 ? hostAndPort[1] : request.localPort
      String proto = details['proto']
      
      if ((proto.toLowerCase() == 'https' && port == '443') || (proto.toLowerCase() == 'http' && port == '80')) {
        // Blank the port.
        port = null
      }
      
      derrivedServer = "${proto}://${host}${ port ? ':' + port : '' }"
    }
    
    derrivedServer
  }
}
