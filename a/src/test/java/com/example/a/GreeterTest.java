package com.example.a;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreeterTest {

    @Test
    void greetsByName() {
        assertEquals("Hello, axion!", new Greeter().greet("axion"));
    }

    @Test
    void bidsFarewellByName() {
        assertEquals("Goodbye, axion!", new Greeter().farewell("axion"));
    }
}
