package com.k_int.web.toolkit.mdc

import java.util.Map
import java.util.Set
import org.slf4j.MDC

public class TrackingMdcWrapper extends ThreadLocal<TrackingMDCWrapperImpl> {
  
  @Override
  protected TrackingMDCWrapperImpl initialValue() {
    new TrackingMDCWrapperImpl()
  }
  
  public void clear() {
    this.get().clear()
  }
  
  public void put(final String key, final String value) {
    this.get().put(key, value)
  }
  
  public String get(final String key) {
    this.get().get(key)
  }
  
  private void remove(final String key) {
    this.get().remove(key)
  }
  
  private void setContextMap(final Map<String, ?> vals) {
    this.get().setContextMap(vals)
  }
  
  private class TrackingMDCWrapperImpl {
    private final Set<String> MDC_ARGS = [] as Set
    
    private void clear() {
      // Only clear the values that were added by this very class.
      for (final String key : MDC_ARGS.collect() ) {
        this.remove(key)
      }
    }
    
    private void put(final String key, final String value) {
      MDC.put(key, value)
      
      // Keep a record of the MDC values
      MDC_ARGS.add(key)
    }
    
    private String get(final String key) {
      // Just return any value present. We only care about cleaning up values
      // we added, not restricting access.
      MDC.get(key)
    }
    
    private void remove(final String key) {
      MDC.remove(key)
      
      // Update our internal map.
      MDC_ARGS.remove(key)
    }
    
    private void setContextMap(Map<String, ?> vals) {
      for (Map.Entry<String, ?> entry : vals) {
        put(entry.key, entry.value)
      }
    }
  } 
}
