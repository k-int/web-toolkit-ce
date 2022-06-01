
databaseChangeLog = {
  changeSet(author: "sosguthorpe", id: "1654077379849-21") {

    createTable(tableName: "custom_property_multi_blob") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_blobPK")
      }
    }

    createTable(tableName: "custom_property_multi_blob_value") {
      column(name: "custom_property_multi_blob_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_byte;", type: "BYTEA")
    }
    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_blob_id", baseTableName: "custom_property_multi_blob_value", constraintName: "FKed37wtwoq679mwy1s18bl6exr", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_blob", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1654077379849-22") {
    createTable(tableName: "custom_property_multi_decimal") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_decimalPK")
      }
    }
    createTable(tableName: "custom_property_multi_decimal_value") {
      column(name: "custom_property_multi_decimal_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_big_decimal", type: "NUMBER(19, 2)")
    }
    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_decimal_id", baseTableName: "custom_property_multi_decimal_value", constraintName: "FKi57yhini8g473uvxv8fcy9p2f", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_decimal", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1654077379849-24") {
    createTable(tableName: "custom_property_multi_integer") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_integerPK")
      }
    }
    createTable(tableName: "custom_property_multi_integer_value") {
      column(name: "custom_property_multi_integer_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_big_integer", type: "NUMBER(19, 2)")
    }
    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_integer_id", baseTableName: "custom_property_multi_integer_value",
    constraintName: "FK2j4jyo04f9rplaimjf9131j1m", deferrable: "false", initiallyDeferred: "false",
    referencedColumnNames: "id", referencedTableName: "custom_property_multi_integer", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1654077379849-26") {
    createTable(tableName: "custom_property_multi_local_date") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_local_datePK")
      }
    }
    createTable(tableName: "custom_property_multi_local_date_value") {
      column(name: "custom_property_multi_local_date_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_local_date", type: "date")
    }

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_local_date_id", baseTableName: "custom_property_multi_local_date_value",
      constraintName: "FKlq0njvkrtu3kr3beevehf03j7", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id",
      referencedTableName: "custom_property_multi_local_date", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1654077379849-28") {
    createTable(tableName: "custom_property_multi_refdata") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_refdataPK")
      }
    }
    createTable(tableName: "custom_property_multi_refdata_refdata_value") {
      column(name: "custom_property_multi_refdata_value_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "refdata_value_id", type: "VARCHAR(36)")
    }
    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_refdata_value_id", baseTableName: "custom_property_multi_refdata_refdata_value",
      constraintName: "FKch1uraa9ojuygaksh67bmxxve", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id",
      referencedTableName: "custom_property_multi_refdata", validate: "true")
    
    addForeignKeyConstraint(baseColumnNames: "refdata_value_id", baseTableName: "custom_property_multi_refdata_refdata_value",
      constraintName: "FKjqol9rr523a3gl1cfi9p3sdu5", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id",
      referencedTableName: "refdata_value", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1654077379849-30") {
    createTable(tableName: "custom_property_multi_text") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_textPK")
      }
    }
    createTable(tableName: "custom_property_multi_text_value") {
      column(name: "custom_property_multi_text_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_string", type: "VARCHAR(255)")
    }
    
    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_text_id", baseTableName: "custom_property_multi_text_value",
      constraintName: "FKbklek969hy5ykdxsp2nf3t5fl", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id",
      referencedTableName: "custom_property_multi_text", validate: "true")
  }
}
