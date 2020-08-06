package com.exactpro.th2.schema.schemaeditorbe;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class GitController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/schema/{name}")
    @ResponseBody
    public Greeting sayHello(@PathVariable(name="name", required=false) String name) {
        return new Greeting(counter.incrementAndGet(), String.format(template, name));
    }

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> sayHello() throws Exception  {
        return Gitter.getBranches(Config.getInstance().getGit());
    }
}