# Changelog

## Version 6.3.0-rc.1

### Additions
* [Custom Properties]
	* Multi valued custom properties
* [General]
	* Add multivalue property migrations as opt in feature

### Changes
* [Chore]
	* Change folder for migrations to namespace as wtk

### Fixes
* [General]
	* Fix binding for Generic Collections.
	* Cast to prevent groovy compiler not seeing name property

## Version 6.2.0

### Additions
* [Custom   Properties]
	* LocalDate type and Retired flag

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 6.2.0-rc.4

### Changes
* [Chore]
	* Extend integration tests and logging around cloning

### Fixes
* [General]
	* unproxy objects before attempting to clone them

## Version 6.2.0-rc.3

### Fixes
* [General]
	* virtual clone method on FileObject

## Version 6.2.0-rc.2

### Changes
* [Chore]
	* add an integrationTest for FileUPload clone operation

## Version 6.1.0-rc.3

### Changes
* [Chore]
	* Logging on RestfulController::update to report version
* [Feature]
	* add ctx to custom property definition

## Version 6.1.0-rc.2

### Additions
* [General]
	* add ctx to custom property definition

## Version 6.0.3-rc.1

### Changes
* [Refactor]
	* Turned off DomainUtils caching (#5)

## Version 6.0.2

### Changes
* [Chore]
	* bump commit for moving LOBFileObject and S3FileObject to domain directory
	* fileContents column name mapping was wrong. just remove it and let the default apply - its correct
	* more tidying of annotations around domain classes
	* be more explicit about some casts
	* Tidy annotations on FileObject subclasses

## Version 6.0.1

### Changes
* [Chore]
	* Build - Change to test resources only

## Version 6.0.0

### Additions
* [S 3   Storage]
	* **BREAKING** -  Breaking change commit
* [General]
	* add get input stream method to file service
	* add method to retrieve inputstream for S3 object
	* Add s3-object-prefix app-setting which allows a module to specify a prefix for objects uploaded to a bucket
	* Add S3FileObject, FileUploadService now understands an engine parameter that defaults to LOB
	* add LOBFileObject, make FileObject abstract

### Changes
* [Chore]
	* Added migration info to readme
	* update readme
* [WIP]
	* integration test

### Fixes
* [General]
	* Improve stream reading

## Version 5.3.0

### Additions
* [General]
	* Chunked streaming version of doTheLookup added to base controller
	* New util helper methods for grails transactions.
	* Chunking iterator

### Changes
* [Chore]
	* Stray space.

### Fixes
* [General]
	* Compiler warning.
	* Skip static compilation on before.

## Version 5.2.0

### Additions
* [General]
	* New WithPromises static and better PromiseFactory impl

## Version 5.1.1

### Fixes
* [General]
	* Revert compile statements to provided. And ensure gorm consistency

## Version 5.1.0

### Additions
* [General]
	* New TrackingMdcWrapper

## Version 5.0.0

### Changes
* [Feature]
	* Overide default properties when none supplied to clone method
	* Validate custom property container values explicitly

### Fixes
* [General]
	* Update wrapper

### References
* [Provides]
	* Issue #ERM-742

## Version 5.0.0-rc.4

### Changes
* [Simple Lookup Service]
	* **BREAKING** -  Allow none-phrase searching.
* [Build]
	* Update the Gradle plugin.

## Version 5.0.0-rc.3

### Changes
* [Refactor]
	* Rename Gorm API methods.

### Fixes
* [General]
	* Don't flush the session.

## Version 5.0.0-rc.2

### Fixes
* [General]
	* Missing import.
	* Deproxy class name.
	* Set correct package name for templates.

## Version 5.0.0-rc.1

### Changes
* [General]
	* **BREAKING** -  Grails 4