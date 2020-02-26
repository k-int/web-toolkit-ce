package com.k_int.web.toolkit.databinding

import javax.annotation.PostConstruct

import org.grails.databinding.converters.web.LocaleAwareNumberConverter

import grails.core.GrailsApplication
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class FixedLocaleNumberConverter extends LocaleAwareNumberConverter {
  
  @Autowired(required=false)
  GrailsApplication grailsApplication
  
  Locale fixedLocale  
  
  @PostConstruct
  void init() {
    final String langTag = grailsApplication.config.getProperty('webtoolkit.converters.numbers.fixedLocale',String, null)
    if (langTag?.toBoolean() && langTag.toLowerCase() != 'true') {
      // Set the locale
      fixedLocale = Locale.forLanguageTag(langTag)
    } else {
      fixedLocale = Locale.default
    }
    
    log.info("Parsing Numbers using fixed Locale of ${fixedLocale}")
  }
  
  @Override
  protected Locale getLocale() {    
    fixedLocale
  }
}
