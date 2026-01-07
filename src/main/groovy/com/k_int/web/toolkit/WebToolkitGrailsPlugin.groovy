package com.k_int.web.toolkit

import com.k_int.web.toolkit.databinding.ExtendedWebDataBinder
import com.k_int.web.toolkit.databinding.FixedLocaleBigDecimalConverter
import com.k_int.web.toolkit.databinding.FixedLocaleNumberConverter
import com.k_int.web.toolkit.links.ProxyAwareCachingLinkGenerator
import com.k_int.web.toolkit.links.ProxyAwareLinkGenerator
import com.k_int.web.toolkit.refdata.GrailsDomainRefdataHelpers

import grails.config.Settings
import grails.core.GrailsClass
import grails.plugins.*
import grails.util.Environment
import grails.web.databinding.DataBindingUtils
import groovy.util.logging.Slf4j

import com.k_int.web.toolkit.usage.*;
import com.k_int.web.toolkit.telemetry.*;
import grails.util.Holders;
import java.security.MessageDigest;


@Slf4j
class WebToolkitGrailsPlugin extends Plugin {
	
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "5.3.2 > *"

  // resources that are excluded from plugin packaging
  def pluginExcludes = [
    "grails-app/views/error.gsp"
  ]
  def loadAfter = [
    'hibernate',
    'controllers',
    'dataBinding',
    'dataBindingGrails'
  ]

  def title = "Web Toolkit" // Headline display name of the plugin
  def author = "Steve Osguthorpe"
  def authorEmail = "steve.osguthorpe@k-int.com"
  def description = '''\
      Provides general tooling for web application frontend development.
    '''

  // URL to the plugin's documentation
  //    def documentation = "http://grails.org/plugin/web-toolkit"

  // Extra (optional) plugin metadata

  // License: one of 'APACHE', 'GPL2', 'GPL3'
  //    def license = "APACHE"

  // Details of company behind the plugin (if there is one)
  //    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

  // Any additional developers beyond the author specified above.
  //    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

  // Location of the plugin's issue tracker.
  //    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

  // Online location of the plugin's browseable source code.
  //    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

  Closure doWithSpring() { {->

    // Replace the default link generator.
    boolean isReloadEnabled = Environment.isDevelopmentMode() || Environment.current.isReloadEnabled()
    boolean cacheUrls = config.getProperty(Settings.WEB_LINK_GENERATOR_USE_CACHE, Boolean, !isReloadEnabled)

    grailsLinkGenerator(cacheUrls ? ProxyAwareCachingLinkGenerator : ProxyAwareLinkGenerator, config.getProperty(Settings.SERVER_URL) ?: null)

    usageValidator(DefaultUsageValidator)
		
    telemetry(Telemetry)
    
    final String langTag = grailsApplication.config.getProperty('webtoolkit.converters.numbers.fixedLocale', String, null)
    if (langTag?.toBoolean()) {
      // Set the fixed locale converters.
      [Short,   Short.TYPE,
       Integer, Integer.TYPE,
       Float,   Float.TYPE,
       Long,    Long.TYPE,
       Double,  Double.TYPE].each { numberType ->
       
         "defaultGrails${numberType.simpleName}Converter"(FixedLocaleNumberConverter) {
             targetType = numberType
         }
       }
       [BigDecimal, BigInteger].each { numberType ->
         "defaultGrails${numberType.simpleName}Converter"(FixedLocaleBigDecimalConverter) {
             targetType = numberType
         }
       }
    }
			 
			 // Replace the default data binder with out custom version.
			 // println("Get existing data binder...");
			 // GrailsWebDataBinder existingDataBinder = application.mainContext.getBean(GrailsWebDataBinder.class)
			 // println("replace data binder...");
			 "${DataBindingUtils.DATA_BINDER_BEAN_NAME}"(ExtendedWebDataBinder)
  }}

  @Override
  void doWithDynamicMethods() {
    // Bind extra methods to the class.
    log.debug("Extending Domain classes.")
    (grailsApplication.getArtefacts("Domain")).each {GrailsClass gc ->
      GrailsDomainRefdataHelpers.addMethods(gc)
    }
  }

  void doWithApplicationContext() {
  }
}
