package com.k_int.web.toolkit.rules


trait DomainRulePropertySource implements RulePropertySource {

  public Map<String, ?> getRuleProperties () {
    // Lazy map of properties.
    getProperties()
  }
}
