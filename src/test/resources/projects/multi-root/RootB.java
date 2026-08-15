///usr/bin/env jbang
//JAVA 24
//DEPS com.google.guava:guava:33.4.0-jre
//SOURCES src/BHelper.java

import com.google.common.collect.ImmutableList;

class RootB {
    public static void main(String[] args) {
        System.out.println(ImmutableList.of(BHelper.message()));
    }
}
