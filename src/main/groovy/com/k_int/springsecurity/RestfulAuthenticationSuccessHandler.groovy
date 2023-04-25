package com.k_int.springsecurity

import java.io.IOException

import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler

/**
 * Spring-Security Authentication success handler that does not redirect on SUccessfull authentication. 
 */
class RestfulAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    // We do not need to do anything extra on REST authentication success, because there is no page to redirect to.
    // NOOP.
  }

}
