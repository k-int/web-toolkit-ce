# web-toolkit
Toolkit and value-add module for grails 3.2.x and beyond

# installation
Currently the only way to run the module is to include it as part of a multi-project Gradle build.

This guide assumes that a grails project has already been created in the normal way (i.e. using `grails create-app`)
To create a multi-project build you must first remove the `settings.gradle` file from the root of your grails project and create a settings.gradle file 1 directory above your grails project root with the following configuration:

``` Groovy
include 'grails-project-folder', 'web-toolkit'

/** /
// If the web-toolkit module does not exist at the same level as your main grails project then uncomment
// this block to add a custom location to the web toolkit.
project(':web-toolkit').projectDir = new File('/path/to/web-toolkit/directory')
/*/
```
Then, in your base grails project root you should include the following stanza at the root of your build.gradle file.
``` Groovy
grails {
  plugins {
    // See if the Gradle project for the grails-tools plugin is declared.
    // If so, include locally, otherwise pull in distributed version from Maven
    println "Looking for the Web-Toolkit plugin..."
    def wtp = parent?.subprojects?.find {
      if (it.name == "web-toolkit") {
        compile it
        println "\tfound locally at ${it.projectDir}"
        return true
      }
      false
    }
  
    if (!wtp) {
      println "\tno local copy found downloading from Maven"
      // TODO: No published path as of yet. Update once published.
    }
  }
}
```
__Note:__ You may need to delete the folder `.gradle` in your grails project root directory if you have already built the project prior to setting up this multi-project build.
