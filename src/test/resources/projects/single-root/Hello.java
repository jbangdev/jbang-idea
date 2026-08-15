///usr/bin/env jbang
//DEPS org.apache.commons:commons-lang3:3.17.0
//SOURCES src/Message.java
//FILES config/app.properties

import org.apache.commons.lang3.StringUtils;

class Hello {
    public static void main(String[] args) {
        System.out.println(StringUtils.capitalize(Message.text()));
    }
}
