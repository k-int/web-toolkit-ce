# web-toolkit
Toolkit and value-add module for grails 3.2.x and beyond

# installation
Currently the only way to run the module is to include it as part of a multi-project Gradle build. To do that create a gradle.settings file 1 directory above your grails project with the following configuration:

``` Groovy
include 'grails-project-name', 'web-toolkit'
project(':web-toolkit').projectDir = new File('/path/to/web-toolkit/directory')
```
And in your base grails project you should include the following stanza at the bottom of your build.gradle file
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

Note: You may need to delete the folder .gradle in your grails project directory if you have already built the project prior to installing the module.
