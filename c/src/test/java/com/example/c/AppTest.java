package com.example.c;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void runsWithoutArguments() {
        assertDoesNotThrow(() -> App.main(new String[0]));
    }
}
