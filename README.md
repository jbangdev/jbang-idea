jbang-intellij-plugin
======================

<!-- Plugin description -->
**JBang plugin** is a plugin for IntelliJ IDEA to integrate [JBang](https://www.jbang.dev/).

The following features are available:

* JSON Schema for jbang-catalog.json
* JDKs sync with JBang: sync JDKs from JBang to IntelliJ IDEA
* JBang script creation from file templates: New -> JBang Script
* Run Configuration support: run JBang script by right click
    * file name end with '.java', '.kt' or '.jsh'
    * file code should contain `///usr/bin/env jbang` or `//DEPS`

<!-- Plugin description end -->

## Install

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jbang"</kbd> > <kbd>Install Plugin</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>
 
## Build

```
$ # JDK 11 required
$ ./gradlew -x test patchPluginXml buildPlugin
```

<kbd>Preferences</kbd> > <kbd>Plugins</kbd> >  <kbd>Gear Icon Right Click</kbd> > <kbd>Install Plugin from Disk</kbd> > <kbd>Choose $PROJECT_DIR/build/distributions/jbang-intellij-plugin-0.x.0.zip</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>
