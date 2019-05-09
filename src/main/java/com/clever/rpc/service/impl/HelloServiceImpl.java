package com.clever.rpc.service.impl;

import com.clever.rpc.server.Service;
import com.clever.rpc.service.HelloService;

@Service
public class HelloServiceImpl implements HelloService {

    @Override
    public String hello() {
        return "hello";
    }
}
