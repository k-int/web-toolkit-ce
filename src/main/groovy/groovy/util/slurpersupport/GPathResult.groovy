package groovy.util.slurpersupport

/**
 * Compatibility bridge for tooling that still references the Groovy 2/3 package.
 * Groovy 4 relocated this type to groovy.xml.slurpersupport.
 */
abstract class GPathResult extends groovy.xml.slurpersupport.GPathResult {
  GPathResult(
    final groovy.xml.slurpersupport.GPathResult parent,
    final String name,
    final String namespacePrefix,
    final Map<String, String> namespaceTagHints
  ) {
    super(parent, name, namespacePrefix, namespaceTagHints)
  }
}
