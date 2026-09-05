package com.example.b;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    @Test
    void greetsLoudly() {
        assertEquals("HELLO, AXION!", new GreetingService().greetLoudly("axion"));
    }
}
