#!/usr/bin/env just --justfile

#build plugin - patch xml and build distribution
build-plugin:
   rm -rf build/distributions
   rm -rf build/classes
   rm -rf build/resources
   rm -rf build/instrumented
   rm -rf build/tmp
   ./gradlew -x test patchPluginXml buildPlugin

# clean project
clean:
   ./gradlew clean
