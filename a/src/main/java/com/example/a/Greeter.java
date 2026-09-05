package com.example.a;

/** 모듈 a: 가장 아래 계층의 라이브러리. */
public class Greeter {

    private static final String DEFAULT_GREETING = "Hello";

    public String greet(String name) {
        return greet(DEFAULT_GREETING, name);
    }

    /** 인사말을 직접 지정해서 인사한다. */
    public String greet(String greeting, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return greeting + ", " + name + "!";
    }

    public String farewell(String name) {
        return "Goodbye, " + name + "!";
    }
}
