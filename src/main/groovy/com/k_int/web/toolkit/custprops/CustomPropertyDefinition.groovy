package com.k_int.web.toolkit.custprops

import javax.persistence.Transient
import javax.validation.UnexpectedTypeException

import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant
import groovy.util.logging.Log4j

@Log4j
class CustomPropertyDefinition implements MultiTenant<CustomPropertyDefinition> {

    String id

    @Transient
    final static String[] AVAILABLE_DESCR =[LIC_PROP,ORG_CONF,SYS_CONF]
    @Transient
    final static String LIC_PROP='Licence Property'
    @Transient
    final static String ORG_CONF='Organisation Config'
    @Transient
    final static String SYS_CONF='System Config'
    
    String name
    String descr
    String type
    String refdataCategory

    //Map keys can change and they wont affect any of the functionality
    @Transient
    static def validTypes = ["Number":Integer.toString(), 
                             "Text":String.toString(), 
                             "Refdata":RefdataValue.toString(), 
                             "Decimal":BigDecimal.toString()]

    static constraints = {
        name(nullable: false, blank: false, unique:true)
        descr(nullable: true, blank: false)
        type(nullable: false, blank: false)
        refdataCategory(nullable:true)
    }

    static mapping = {
                      id column: 'pd_id', generator: 'uuid', length:36
                   descr column: 'pd_description'
                    name column: 'pd_name', index: 'td_name_idx'
                    type column: 'pd_type', index: 'td_type_idx'
         refdataCategory column: 'pd_rdc', index: 'td_type_idx'
                      sort name: 'desc'
    }

    private static def typeIsValid(value) {
        if (validTypes.containsValue(value)) {
            return true;
        } else {
            log.error("Provided custom prop type ${value.getClass()} is not valid. Allowed types are ${validTypes}")
            throw new UnexpectedTypeException()
        }
    }

    static def lookupOrCreateProp(id, owner){
        if(id instanceof String){
            id = id.toLong()
        }
        def type = get(id)
        createPropertyValue(owner, type)
    }
    /**
    * @param owner: The class that will hold the property, e.g Licence
    **/
    private static CustomProperty createPropertyValue(owner, CustomPropertyDefinition type) {
        String classString = owner.getClass().toString()
        def ownerClassName = classString.substring(classString.lastIndexOf(".")+1)
        ownerClassName="com.k_int.kbplus."+ownerClassName+"CustomProperty"
        def newProp = Class.forName(ownerClassName).newInstance(type: type,owner: owner)
        newProp.setNote("")
        owner.customProperties.add(newProp)
        newProp.save(flush:true)
        newProp
    }

    static def lookupOrCreateType(name, typeClass, descr) {
        typeIsValid(typeClass)
        def type = findByNameAndType(name, typeClass);
        if (!type) {
            log.debug("No PropertyDefinition type match found for ${typeClass}. Creating new.")
            type = new CustomPropertyDefinition(name: name, type: typeClass, descr: descr)
            type.save()
        }
        type
    }

    static def refdataFind(params) {

        def result = []
        def ql = null

        // Copy params and default in sort order
        def find_params = [
          desc: params.desc, q:params.q, sort:(params.sort?:'name'), order:(params.order?:'asc'), max:params.max, offset:params.offset
        ]

        if(!find_params.desc || find_params.desc == "*"){
            if(!find_params.desc)
              log.error("Search PropertyDefinition without Description ${params}");

            ql = findAllByNameIlike("${find_params.q}%",find_params)
        }else{
            ql = findAllByNameIlikeAndDescr("${find_params.q}%",find_params.desc, find_params)
        }

        if ( ql ) {
            ql.each { prop ->
                result.add([id:"${prop.id}",text:"${prop.name}"])
            }
        }

        result
    }

  @Transient
  def getOccurrencesOwner(String[] cls){
    def all_owners = []
    cls.each{
        all_owners.add(getOccurrencesOwner(it)) 
    }
    return all_owners
  }

  @Transient
  def getOccurrencesOwner(String cls){
    def qparams = [this]
    def qry = 'select c.owner from '+cls+" as c where c.type = ?"
    return PropertyDefinition.executeQuery(qry,qparams); 
  }

  @Transient
  def countOccurrences(String cls) {
    def qparams = [this]
    def qry = 'select count(c) from '+cls+" as c where c.type = ?"
    return (PropertyDefinition.executeQuery(qry,qparams))[0]; 
  }
  @Transient
  def countOccurrences(String[] cls){
    def total_count = 0
    cls.each{
        total_count += countOccurrences(it)
    }
    return total_count
  }
  @Transient
  def removeProperty() {
    log.debug("Remove");
    PropertyDefinition.executeUpdate('delete from com.k_int.kbplus.LicenseCustomProperty c where c.type = ?',[this])
    PropertyDefinition.executeUpdate('delete from com.k_int.kbplus.SubscriptionCustomProperty c where c.type = ?',[this])
    this.delete();
  }
}

