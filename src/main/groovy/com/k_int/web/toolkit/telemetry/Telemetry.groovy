package com.k_int.web.toolkit.telemetry;

public interface Telemetry {

  public void telemetryEvent(String event, String tenant, Map<String,Object> params);

  // Deprecated
  public void telementryEvent(String event, String tenant, Map<String,Object> params);

}
