package com.k_int.web.toolkit.databinding

import java.text.NumberFormat
import java.text.ParsePosition

import jakarta.annotation.PostConstruct

import grails.core.GrailsApplication
import grails.databinding.converters.ValueConverter
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class FixedLocaleNumberConverter implements ValueConverter {

  Class<?> targetType
  Locale fixedLocale

  @Autowired(required=false)
  GrailsApplication grailsApplication

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

  protected Locale getLocale() {
    fixedLocale
  }

  @Override
  public boolean canConvert(Object value) {
    value instanceof String
  }

  @Override
  public Object convert(Object value) {
    final String trimmedValue = value.toString().trim()
    final ParsePosition parsePosition = new ParsePosition(0)
    def result = numberFormatter.parse((String)value, parsePosition).asType(getTargetType())
    if(parsePosition.index != trimmedValue.size()) {
      throw new NumberFormatException("Unable to parse number [${value}]")
    }
    result
  }

  protected NumberFormat getNumberFormatter() {
    NumberFormat.getInstance(getLocale())
  }
}
