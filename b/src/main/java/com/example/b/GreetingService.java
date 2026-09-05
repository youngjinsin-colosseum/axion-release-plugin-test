package com.example.b;

import com.example.a.Greeter;

/** 모듈 b: 모듈 a 에 의존한다. */
public class GreetingService {

    private final Greeter greeter = new Greeter();

    public String greetLoudly(String name) {
        return greeter.greet(name).toUpperCase();
    }
}
