package com.example.a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GreeterTest {

    @Test
    void greetsByName() {
        assertEquals("Hello, axion!", new Greeter().greet("axion"));
    }

    @Test
    void greetsWithCustomGreeting() {
        assertEquals("안녕, axion!", new Greeter().greet("안녕", "axion"));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Greeter().greet(" "));
    }
}
