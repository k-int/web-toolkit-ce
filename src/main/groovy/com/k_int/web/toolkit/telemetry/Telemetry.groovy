package com.k_int.web.toolkit.telemetry;


import org.springframework.stereotype.Component

import grails.core.GrailsApplication
import grails.util.Holders
import groovy.transform.CompileStatic

@Component
@CompileStatic
public class Telemetry {
	
	private static Telemetry _telemetry
	private final TelemetryPublisher publisher;
	
	private static Telemetry getTelemetry() {
		if (_telemetry == null) _telemetry = Holders.getGrailsApplication().mainContext.getBean(Telemetry.class)
		
		_telemetry
	}
	
	public Telemetry ( GrailsApplication grailsApplication, Optional<TelemetryPublisher> publisher ) {
		this.publisher = publisher.orElseGet({ new DefaultTelemetryPublisher(grailsApplication) })
	}
	
	private void publishData(TelemetryData data) {
		publisher.publish(data);
	}
	
	public static TelemetryData raiseEvent (TelemetryData data) {
		getTelemetry()
			.publishData(data)
	}
	
	public static TelemetryData raiseEvent (
			@DelegatesTo(value=TelemetryData.class, strategy=Closure.DELEGATE_FIRST) Closure<?> closure) {
			
		getTelemetry()
			.publishData(new TelemetryData().tap(closure))
	}
}
