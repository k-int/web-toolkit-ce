
databaseChangeLog = {
    changeSet(author: "chas", id: "20220915120000-001") {

        addColumn(tableName: "app_setting") {
            column(name: "st_hidden", type: "boolean")
        }
    }
}
