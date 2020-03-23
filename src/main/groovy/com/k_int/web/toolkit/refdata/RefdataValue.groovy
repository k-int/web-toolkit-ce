package com.k_int.web.toolkit.refdata

import com.ibm.icu.text.Normalizer2
import com.k_int.web.toolkit.databinding.BindUsingWhenRef

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@BindUsingWhenRef({ obj, propName, source, boolean isCollection = false ->
  RefdataBinding.refDataBinding(obj, propName, source, isCollection)
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
    owner (nullable: false)
  }
  
  public static String normValue ( String string ) {
    // Remove all diacritics and substitute for compatibility
    normalizer.normalize( string.trim() ).replaceAll(/\p{M}/, '').replaceAll(/\s+/, '_').toLowerCase()
  }
  
  private static String tidyLabel ( String string ) {
    string.trim().replaceAll(/\s{2,}/, ' ')
  }
  
  void setValue (String value) {
    this.value = normValue( value )
  }
  
  void setLabel (String label) {
    this.label = tidyLabel( label )
    if (this.value == null) {
      this.setValue( label )
    }
  }
  
  /**
   * Lookup or create a RefdataValue
   * @param category_name
   * @param value
   * @return
   */
  static <T extends RefdataValue> T lookupOrCreate(final String category_name, final String label, final String value=null, final boolean defaultCatInternal = RefdataCategory.DEFAULT_INTERNAL, final Class<T> clazz = this) {
    RefdataCategory cat = RefdataCategory.findByDesc(category_name)
    if (!cat) {
      cat = new RefdataCategory()
      cat.desc = category_name
      cat.internal = defaultCatInternal
      cat.save(flush:true, failOnError:true)
    }
    
    lookupOrCreate (cat, label, value, clazz)
  }
  
  static <T extends RefdataValue> T lookupOrCreate(final RefdataCategory cat, final String label, final String value=null, final Class<T> clazz = this) {
    
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
