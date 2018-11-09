package com.k_int.web.toolkit.refdata

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.text.Normalizer2
import com.k_int.web.toolkit.databinding.BindUsingWhenRef

import grails.gorm.MultiTenant
import grails.util.GrailsNameUtils

@BindUsingWhenRef({ obj, propName, source ->

  def data = source[propName]
  
  // If the data is asking for null binding then ensure we return here.
  if (data == null) {
    return null
  }
  
  // Create a map of id and value
  if (data instanceof String) {
    data = [
      'id': data,
      'value': data
    ]
  }
  
  // Default to the original
  RefdataValue val = obj[propName]
  if (data) {
    // Found by Id or lookup by value.
    val = RefdataValue.read(data['id']) ?: (obj."lookup${GrailsNameUtils.getClassName(propName)}"(data['value'])) ?: val
  }
  
  val
})
class RefdataValue implements MultiTenant<RefdataValue> {
  
  private static final Normalizer2 normalizer = Normalizer2.NFKDInstance

  String id
  String value
  String label

  static belongsTo = [
    owner:RefdataCategory
  ]

  static mapping = {
    id column: 'rdv_id', generator: 'uuid', length:36
    version column: 'rdv_version'
    owner column: 'rdv_owner', index:'rdv_entry_idx'
    value column: 'rdv_value', index:'rdv_entry_idx'
    label column: 'rdv_label'
  }

  static constraints = {
    label (nullable: false, blank: false)
    value (nullable: false, blank: false)
    owner (nullable: false, blank: false)
  }
  
  public static String normValue ( String string ) {
    // Remove all diacritics and substitute for compatibility
    normalizer.normalize( string.trim() ).replaceAll(/\p{M}/, '').replaceAll(/\s+/, '_').toLowerCase()
  }
  
  private static String tidyLabel ( String string ) {
    UCharacter.toTitleCase( string.trim(), null ).replaceAll(/\s{2,}/, ' ')
  }
  
  void setValue (String value) {
    this.value = normValue( value )
  }
  
  void setLabel (String label) {
    this.label = tidyLabel( label )
  }
  
  /**
   * Lookup or create a RefdataValue
   * @param category_name
   * @param value
   * @return
   */
  static <T extends RefdataValue> T lookupOrCreate(final String category_name, final String label, final String value=null, Class<T> clazz = this) {
    final RefdataCategory cat = RefdataCategory.findOrCreateByDesc(category_name).save(flush:true, failOnError:true)
    
    final String norm_value = normValue( value ?: label )
    
    T result = clazz.findByOwnerAndValue(cat, norm_value)
    
    if (!result) {
      result = clazz.newInstance()
      result.label = label
      result.value = norm_value
      result.owner = cat
      result.save(flush:true, failOnError:true)
    }
    result
  }

}
