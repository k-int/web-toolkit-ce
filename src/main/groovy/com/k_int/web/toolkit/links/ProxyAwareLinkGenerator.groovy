package com.k_int.web.toolkit.links

import jakarta.annotation.PostConstruct

import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.servlet.mvc.GrailsWebRequest

import com.k_int.web.toolkit.utils.RequestUtils

import groovy.util.logging.Slf4j

@Slf4j
class ProxyAwareLinkGenerator extends DefaultLinkGenerator {
  
  ProxyAwareLinkGenerator(String serverBaseURL, String contextPath) {
    super(serverBaseURL, contextPath)
  }

  ProxyAwareLinkGenerator(String serverBaseURL) {
    super(serverBaseURL)
  }
  
  @PostConstruct
  void init() {
    log.debug ('Replacing the Default link generator with one that is Proxy aware.')
  }
  
  @Override
  String makeServerURL() {
    RequestUtils.getOriginalServerURL( GrailsWebRequest.lookup().currentRequest ) ?: super.makeServerURL()
  }
}
