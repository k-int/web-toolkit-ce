package com.k_int.web.toolkit.settings


import grails.gorm.MultiTenant

public class AppSetting implements MultiTenant<AppSetting> {

  String id
  String section
  String key
  String settingType
  String vocab
  String defValue
  String value

  /** If this value is set to true then we do not want it to be found by the filter functionality */
  Boolean hidden;

  static mapping = {
             id column: 'st_id', generator: 'uuid', length:36
        version column: 'st_version'
        section column: 'st_section'
            key column: 'st_key'
    settingType column: 'st_setting_type'
          vocab column: 'st_vocab'
       defValue column: 'st_default_value'
          value column: 'st_value'
         hidden column: 'st_hidden'
  }

  static constraints = {
        section (nullable: true,  blank: false)
            key (nullable: false, blank: false)
    settingType (nullable: true,  blank: false)
          vocab (nullable: true,  blank: false)
       defValue (nullable: true,  blank: false)
          value (nullable: true,  blank: true)
         hidden (nullable: true)
  }

  public static String getSettingValue(String section, String key) {
    String result = AppSetting.executeQuery('select coalesce(a.value, a.defValue) from AppSetting as a where a.section=:s and a.key=:k',
                                            [s:section, k:key])?[0]; // Swapped out .get(0) for ?[0] in order to empty-safe the process
    return result;
  }

}
