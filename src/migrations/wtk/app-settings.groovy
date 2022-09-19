databaseChangeLog = {

    changeSet(author: "ianibbo (manual)", id: "20191004-1451-001") {
        createTable(tableName: "app_setting") {
            column(name: "st_id", type: "VARCHAR(36)") {
                constraints(nullable: "false")
            }
            column(name: "st_version", type: "BIGINT") {
                constraints(nullable: "false")
            }
            column(name: 'st_section', type: "VARCHAR(255)")
            column(name: 'st_key', type: "VARCHAR(255)")
            column(name: 'st_setting_type', type: "VARCHAR(255)")
            column(name: 'st_vocab', type: "VARCHAR(255)")
            column(name: 'st_default_value', type: "VARCHAR(255)")
            column(name: 'st_value', type: "VARCHAR(255)")
        }
    }

}
