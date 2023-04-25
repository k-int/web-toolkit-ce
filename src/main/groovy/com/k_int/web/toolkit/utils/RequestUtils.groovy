package com.k_int.web.toolkit.utils

import groovy.util.logging.Slf4j
import javax.servlet.http.HttpServletRequest

@Slf4j
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
        token.split(/\s*=\s*/).with { 
          log.debug "Trying to split ${it}"
          
          if (it) {
            if (it.length == 2) {
              map[it[0]] = it[1]
            } else {
              
              log.debug "Header does not fit the pattern x=y:  ${it[0]}"
            }
          } else {
            log.debug "Ignoring ${it} and treating as invalid"
          }
        }
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
