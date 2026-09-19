# Changelog

## Version fsl/v11.2.1

### Fixes

- \[Release\]
  - use plugin-owned FSL tags and one changelog

## Version fsl/v11.2.0

### Fixes

- \[General\]
  - support multipart LOB rebinding without buffering

- \[Storage\]
  - reject missing tenant migration safeguards

### General

- \[General\]
  - run migration qualification without publishing credentials
  - finish owned S3 cleanup before tenant purge
  - retain tenant file ownership through deletion and rollback
  - retain submission context through promise execution
  - close Grails 7.2 migration checklist
  - record final shared release evidence

## Version v11.1.0

### Fixes

- \[General\]
  - expose the toolkit query backend setting

### General

- \[General\]
  - align web toolkit with Grails 7.2

## Version v11.0.0-rc.4

### Fixes

- \[Databinding\]
  - qualify tag lookup for Groovy 4

## Version v11.0.0-rc.1

### Breaking changes

- \[General\]
  - Drops Grails 6 support and requires JDK 21.

### General

- \[General\]
  - upgrade to Grails 7

## Version v10.5.0

### General

- \[General\]
  - Adding the telemetry data and publisher

## Version v10.4.0

### Fixes

- \[General\]
  - Wrap error controller logic in try/catch to prevent infinite looping (#22)

## Version v10.3.0

### General

- \[General\]
  - Custprop Types are handled by internal exception logic for better response messaging (#19)

## Version v10.2.0

### General

- \[General\]
  - default 500 handling (#17)

## Version v10.1.0

### General

- \[General\]
  - S3 secret environment variable (#16)

## Version v10.0.0

### Breaking changes

- \[General\]
  - Grails 6.2 upgrade - reposting using single quotes instead of escaping the bang

## Version v9.0.3

### Fixes

- \[General\]
  - contains for looking up contexts

## Version v9.0.1

### General

- \[General\]
  - Add validation bean to default context, also reference k-int repo for missing jar dependencies

## Version v9.0.0

### Breaking changes

- \[General\]
  - Grails 6

## Version v8.1.4

### Fixes

- \[General\]
  - Move none greedy whitespace captures to parser from lexer

## Version v8.1.3

### Fixes

- \[General\]
  - Trim determined subject in ambiguous filter

## Version v8.1.2

### Fixes

- \[General\]
  - Allow whitespace around subjects/values

## Version v8.1.1

### Fixes

- \[General\]
  - Special case for isNotSet
  - No session on results size.

## Version v8.1.0

### Fixes

- \[General\]
  - Pass association stack as root entry to filter walker

## Version v8.1.0-rc.4

### Fixes

- \[General\]
  - Reinstate static subquery resolution

## Version v8.1.0-rc.1

### Fixes

- \[General\]
  - \`@Before\` annotation in super not run anymore

### General

- \[General\]
  - Update the maven integration.
