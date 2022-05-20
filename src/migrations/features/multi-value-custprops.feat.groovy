
databaseChangeLog = {

  changeSet(author: "sosguthorpe", id: "1652870877903-1") {
    createTable(tableName: "custom_property_multi_blob") {
      column(autoIncrement: "true", name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_blobPK")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "public_note", type: "CLOB")

      column(name: "definition_id", type: "VARCHAR(36)")

      column(name: "note", type: "CLOB")

      column(name: "internal", type: "BOOLEAN") {
        constraints(nullable: "false")
      }

      column(name: "parent_id", type: "BIGINT")
    }

    createTable(tableName: "custom_property_multi_blob_value") {
      column(name: "custom_property_multi_blob_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_byte;", type: "BLOB")
    }


    addForeignKeyConstraint(baseColumnNames: "parent_id", baseTableName: "custom_property_multi_blob", constraintName: "FKb46ah4k2on658e4gpd227fpx9", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_container", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_blob_id", baseTableName: "custom_property_multi_blob_value", constraintName: "FKed37wtwoq679mwy1s18bl6exr", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_blob", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "definition_id", baseTableName: "custom_property_multi_blob", constraintName: "FKh8phlvwn1fb0l8yl9tg4scx", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pd_id", referencedTableName: "custom_property_definition", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1652870877903-2") {
    createTable(tableName: "custom_property_multi_decimal") {
      column(autoIncrement: "true", name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_decimalPK")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "public_note", type: "CLOB")

      column(name: "definition_id", type: "VARCHAR(36)")

      column(name: "note", type: "CLOB")

      column(name: "internal", type: "BOOLEAN") {
        constraints(nullable: "false")
      }

      column(name: "parent_id", type: "BIGINT")
    }

    createTable(tableName: "custom_property_multi_decimal_value") {
      column(name: "custom_property_multi_decimal_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_big_decimal", type: "NUMBER(19, 2)")
    }

    addForeignKeyConstraint(baseColumnNames: "parent_id", baseTableName: "custom_property_multi_decimal", constraintName: "FK3s899p851g9qyk1yc76nsyd2c", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_container", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "definition_id", baseTableName: "custom_property_multi_decimal", constraintName: "FK7pvd9f4p9v1dflqcypi5ljsi4", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pd_id", referencedTableName: "custom_property_definition", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_decimal_id", baseTableName: "custom_property_multi_decimal_value", constraintName: "FKi57yhini8g473uvxv8fcy9p2f", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_decimal", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1652870877903-3") {
    createTable(tableName: "custom_property_multi_integer") {
      column(autoIncrement: "true", name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_integerPK")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "public_note", type: "CLOB")

      column(name: "definition_id", type: "VARCHAR(36)")

      column(name: "note", type: "CLOB")

      column(name: "internal", type: "BOOLEAN") {
        constraints(nullable: "false")
      }

      column(name: "parent_id", type: "BIGINT")
    }

    createTable(tableName: "custom_property_multi_integer_value") {
      column(name: "custom_property_multi_integer_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_big_integer", type: "NUMBER(19, 2)")
    }

    addForeignKeyConstraint(baseColumnNames: "parent_id", baseTableName: "custom_property_multi_integer", constraintName: "FKd2uuflu0rj1y7uy4x1tadq1ik", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_container", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_integer_id", baseTableName: "custom_property_multi_integer_value", constraintName: "FK2j4jyo04f9rplaimjf9131j1m", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_integer", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "definition_id", baseTableName: "custom_property_multi_integer", constraintName: "FKhbmf9b0rpjmh796528yiib3e5", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pd_id", referencedTableName: "custom_property_definition", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1652870877903-4") {
    createTable(tableName: "custom_property_multi_local_date") {
      column(autoIncrement: "true", name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_local_datePK")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "public_note", type: "CLOB")

      column(name: "definition_id", type: "VARCHAR(36)")

      column(name: "note", type: "CLOB")

      column(name: "internal", type: "BOOLEAN") {
        constraints(nullable: "false")
      }

      column(name: "parent_id", type: "BIGINT")
    }
    createTable(tableName: "custom_property_multi_local_date_value") {
      column(name: "custom_property_multi_local_date_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_local_date", type: "date")
    }

    addForeignKeyConstraint(baseColumnNames: "parent_id", baseTableName: "custom_property_multi_local_date", constraintName: "FK3a97bq8whmu5bryuxsnocuwda", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_container", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_local_date_id", baseTableName: "custom_property_multi_local_date_value", constraintName: "FKlq0njvkrtu3kr3beevehf03j7", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_local_date", validate: "true")


    addForeignKeyConstraint(baseColumnNames: "definition_id", baseTableName: "custom_property_local_date", constraintName: "FK7897vj3shs837v623bh235ys6", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pd_id", referencedTableName: "custom_property_definition", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1652870877903-5") {
    createTable(tableName: "custom_property_multi_refdata") {
      column(autoIncrement: "true", name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_refdataPK")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "public_note", type: "CLOB")

      column(name: "definition_id", type: "VARCHAR(36)")

      column(name: "note", type: "CLOB")

      column(name: "internal", type: "BOOLEAN") {
        constraints(nullable: "false")
      }

      column(name: "parent_id", type: "BIGINT")
    }

    createTable(tableName: "custom_property_multi_refdata_refdata_value") {
      column(name: "custom_property_multi_refdata_value_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "refdata_value_id", type: "VARCHAR(36)")
    }

    addForeignKeyConstraint(baseColumnNames: "parent_id", baseTableName: "custom_property_multi_refdata", constraintName: "FK4f10c1ntj0i8xin98cpwk586r", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_container", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_refdata_value_id", baseTableName: "custom_property_multi_refdata_refdata_value", constraintName: "FKch1uraa9ojuygaksh67bmxxve", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_refdata", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "definition_id", baseTableName: "custom_property_multi_refdata", constraintName: "FKjfr8sede3xm818vb60kef0pty", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pd_id", referencedTableName: "custom_property_definition", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "refdata_value_id", baseTableName: "custom_property_multi_refdata_refdata_value", constraintName: "FKjqol9rr523a3gl1cfi9p3sdu5", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value", validate: "true")
  }

  changeSet(author: "sosguthorpe", id: "1652870877903-6") {
    createTable(tableName: "custom_property_multi_text") {
      column(autoIncrement: "true", name: "id", type: "BIGINT") {
        constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_property_multi_textPK")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "public_note", type: "CLOB")

      column(name: "definition_id", type: "VARCHAR(36)")

      column(name: "note", type: "CLOB")

      column(name: "internal", type: "BOOLEAN") {
        constraints(nullable: "false")
      }

      column(name: "parent_id", type: "BIGINT")
    }


    createTable(tableName: "custom_property_multi_text_value") {
      column(name: "custom_property_multi_text_id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value_string", type: "CLOB")
    }

    addForeignKeyConstraint(baseColumnNames: "custom_property_multi_text_id", baseTableName: "custom_property_multi_text_value", constraintName: "FKbklek969hy5ykdxsp2nf3t5fl", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_multi_text", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "parent_id", baseTableName: "custom_property_multi_text", constraintName: "FKp8kldvlxr1dbvi3u2ldbowlen", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_property_container", validate: "true")

    addForeignKeyConstraint(baseColumnNames: "definition_id", baseTableName: "custom_property_multi_text", constraintName: "FKstgaru5a7nvl9n0o3f4vwi959", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "pd_id", referencedTableName: "custom_property_definition", validate: "true")
  }
}
