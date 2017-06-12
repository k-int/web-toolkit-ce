package com.k_int.web.toolkit.rules

import org.codehaus.groovy.control.CompilerConfiguration

import groovy.lang.MissingPropertyException;

trait Rule {

  def ruleDef = "true"
  Map<String, ?> bindingMap = [:]

  static final CompilerConfiguration conf = new CompilerConfiguration('scriptBaseClass': SafeScriptSource.class.name)

  def checkAgainst (Map<String, ?> obj) {

    bindingMap = obj

    def val
    switch (ruleDef) {
      case Closure :

        // We should be able to set the Delegate to the map here.
        Closure c = ruleDef as Closure
        c = c.rehydrate(this, this, bindingMap)

        // Pass in the vals  too as the "it" or first named param.
        val = c(bindingMap)
        break
      default :

        // Script....
        GroovyShell shell = new GroovyShell(getClass().getClassLoader(), new Binding(bindingMap), conf)

        // Evaluate as string.
        val = shell.evaluate ( ruleDef.toString() )
    }

    val
  }

  def propertyMissing (String name) {
    def val = null
    try {
      val = bindingMap.get(name)
    } catch (Exception e) {
      // Silence here. Just return null.
    }
    val
  }

  /**
   * Silence exception and return null on missing variable.
   */
  static abstract class SafeScriptSource extends Script {

    /**
     *  Overloading here to not drop through when a property doesn't exist.
     * Silence the exception and just return null.
     * @see groovy.lang.Script#getProperty(java.lang.String)
     */
    def getProperty(String name) {
      try {
        return super.getProperty(name);
      } catch (MissingPropertyException e) {
        return null
      }
    }
  }
}
