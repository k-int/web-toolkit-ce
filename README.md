# Web Toolkit
Toolkit and value-add module for grails 3.2.x and beyond

# Installation
Currently the only way to run the module is to include it as part of a multi-project Gradle build.

This guide assumes that a grails project has already been created in the normal way (i.e. using `grails create-app`)

1. To create a multi-project build you must first remove the `settings.gradle` file from the root of your grails project.

2. You now need to create a settings.gradle file 1 directory above your grails project root with the following configuration:
``` Groovy
include 'grails-project-folder', 'web-toolkit'

/** /
// If the web-toolkit module does not exist at the same level as your main grails project then uncomment
// this block to add a custom location to the web toolkit.
project(':web-toolkit').projectDir = new File('/path/to/web-toolkit/directory')
/*/
```

3. Then, in your base grails project root you should include the following stanza at the root of your build.gradle file.
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
4. Delete the folder `.gradle` from your grails project root directory if it exists. If you haven't built your project yet, this folder may not exists.

## File change summary
If we assume your grails project is called `grails-project` then, once you have completed the steps above, the structure of file changes should look like the following:
``` Tree
root
├── grails-project
│   ├── settings.gradle     <--- Remove this file #1
│   ├── .gradle             <--- Remove this directory (if present) #4
│   └── build.gradle        <--- Add the grails plugin stanza from #3.
│
└── settings.gradle         <--- Create this file and add the content as detailed in #2

```

# Development

## Publish
edit ~/.gradle/gradle.properties and add

    kintMavenUser=
    kintMavenPassword=

then run
    grails publish-plugin

