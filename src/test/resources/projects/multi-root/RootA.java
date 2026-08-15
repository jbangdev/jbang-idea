///usr/bin/env jbang
//JAVA 22
//DEPS org.apache.commons:commons-lang3:3.14.0
//SOURCES src/AHelper.java

import org.apache.commons.lang3.StringUtils;

class RootA {
    public static void main(String[] args) {

        System.out.println(StringUtils.capitalize(AHelper.message()));

    }
}
