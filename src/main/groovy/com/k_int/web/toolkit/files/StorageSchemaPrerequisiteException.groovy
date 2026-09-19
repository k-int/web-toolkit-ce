package com.k_int.web.toolkit.files

/** Required tenant migrations are absent or their physical safeguards changed. */
class StorageSchemaPrerequisiteException extends IllegalStateException {
    final String schema
    final List<String> requirements

    StorageSchemaPrerequisiteException(String schema, List<String> requirements) {
        super("Toolkit storage prerequisites failed for schema '${schema}': ${requirements.join('; ')}. " +
            'Apply wtk/owned-file-lob.feat.groovy and wtk/stored-s3-object.feat.groovy through the tenant changelog; ' +
            'repair altered objects through an explicit migration, then retry tenant activation.')
        this.schema = schema
        this.requirements = Collections.unmodifiableList(new ArrayList<>(requirements))
    }
}
