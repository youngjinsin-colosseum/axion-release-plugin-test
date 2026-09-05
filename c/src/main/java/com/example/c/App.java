package com.example.c;

import com.example.b.GreetingService;

/** 모듈 c: 모듈 b 에 의존하는 실행 진입점. */
public class App {

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "world";
        System.out.println(new GreetingService().greetLoudly(name));
    }
}
