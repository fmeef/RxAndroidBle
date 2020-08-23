package com.polidea.rxandroidble2.internal.server
import spock.lang.Specification

public class MultiIndexTest extends Specification {
    MultiIndex<String, Integer, String> objectUnderTest

    def setup() {
        objectUnderTest = new MultiIndexImpl<>()
    }

    def "MultiIndex puts and retrieves from all indices"() {
        when:
        objectUnderTest.put(testKey, value)
        objectUnderTest.putMulti(testMulti, value)

        then:
        objectUnderTest.getMulti(testMulti) == value
        objectUnderTest.get(testKey) == value

        where:
        testKey | testMulti | value
        "test"  | 7         | "val"
        "no"    | 12        | "val2"
    }

    def "multindex fails with nonexistent multiKey"() {
        when:
        objectUnderTest.put(testKey, value)
        objectUnderTest.put(testKey2, value)
        objectUnderTest.putMulti(testMulti, value)

        then:
        objectUnderTest.getMulti(99) == null

        where:
        testKey   |  testKey2  |  testMulti  | value
        "test"    |  "test2"   |     1       |  "val"
        "test2"   |  "test3"   |     2       |  "val"
    }

    def "remove from multiindex works"() {
        when:
        objectUnderTest.put(testKey, value)

        then:
        objectUnderTest.remove(testKey) == value
        objectUnderTest.remove(testKey) == null


        where:
        testKey | value
        "test"  | "val"
        "no"    | "val2"
    }
}