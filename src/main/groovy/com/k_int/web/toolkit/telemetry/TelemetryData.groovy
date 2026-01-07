package com.k_int.web.toolkit.telemetry

import org.apache.tools.ant.taskdefs.DefaultExcludes

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.ToString
import java.lang.reflect.Field

@CompileStatic
@ToString(ignoreNulls = true, includeNames=true)
public class TelemetryData {
	
	private static final List<String> DECLARED_FIELD_NAMES = TelemetryData.declaredFields.findResults({ Field f -> 
		if ( f.isSynthetic()) return null
		
		f.getName()
	}) as List

	private static final List<String> INCLUDED_SYNTHETIC_PROPERTIES = ['params']
	
	private static final List<String> INCLUDED_PROPERTY_NAMES = DECLARED_FIELD_NAMES + INCLUDED_SYNTHETIC_PROPERTIES
	
	String event
	String org
	String env
	String tenant
	
	private Map<String, Object> _additionalData = [:]
	
	private Map<String, Object> getParams() {
		
		// Tidies the additional data to not include the direct properties.
		DECLARED_FIELD_NAMES.forEach ( _additionalData.&remove )
		
		return _additionalData;
	}

	void propertyMissing( String propertyName ) {
		_additionalData.get(propertyName)
	}
	
	void propertyMissing( String propertyName, Object value ) {
		_additionalData.put(propertyName, value)
	}
	
	@PackageScope
	Map<String, Object> toDataMap() {
		
		// Tidies the additional data to not include the direct properties.
		Map<String, Object> data = getMetaPropertyValues()
			.collectEntries { prop ->
				final String name = prop.getName()
				final def val = prop.getValue()
				
				if (name == null || val == null) return Collections.emptyMap()
				
				if ( !INCLUDED_PROPERTY_NAMES.contains(name) ) return Collections.emptyMap()
				
				[(name) : val]
		}
		
		return data;
	}
}
