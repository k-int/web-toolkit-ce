package com.k_int.web.toolkit.databinding

import grails.databinding.DataBindingSource
import grails.web.mime.MimeType

import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest

import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator

class ExtendedJsonDataBindingSourceCreator extends JsonDataBindingSourceCreator {

  @Override
  DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) {
    
    // If the match was done because of the mime types then we should inspect the request body.
    if (mimeType in getMimeTypes()) {
      if (bindingSource instanceof ServletRequest) {
        bindingSource = inspectRequest ((HttpServletRequest)bindingSource)
      }
    }

    super.createDataBindingSource(mimeType, bindingTargetType, bindingSource)
  }

  /**
   * Inspect the request and if it contains JSON then we should use that as the source instead.
   * @param req
   * @return
   */
  protected Object inspectRequest (Object req) {
    return req.JSON ?: req
  }
}
