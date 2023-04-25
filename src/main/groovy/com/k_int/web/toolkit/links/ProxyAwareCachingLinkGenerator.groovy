package com.k_int.web.toolkit.links

import javax.annotation.PostConstruct

import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.servlet.mvc.GrailsWebRequest

import com.k_int.web.toolkit.utils.RequestUtils

import groovy.util.logging.Slf4j

@Slf4j
class ProxyAwareCachingLinkGenerator extends DefaultLinkGenerator {
  
  ProxyAwareCachingLinkGenerator(String serverBaseURL, String contextPath) {
    super(serverBaseURL, contextPath)
  }

  ProxyAwareCachingLinkGenerator(String serverBaseURL) {
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
