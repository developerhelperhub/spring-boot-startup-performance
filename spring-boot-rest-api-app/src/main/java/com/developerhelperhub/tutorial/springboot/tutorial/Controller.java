package com.developerhelperhub.tutorial.springboot.tutorial;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Value("${message.hello}")
    private  String message;

    @GetMapping("/hello")
    public String hello() {
        return "Hi welcome, " + message;
    }

}
