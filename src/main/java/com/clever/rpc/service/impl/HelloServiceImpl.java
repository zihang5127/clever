package com.clever.rpc.service.impl;

import com.clever.rpc.server.Service;
import com.clever.rpc.service.HelloService;

/**
 * @author sunbin
 */
@Service
public class HelloServiceImpl implements HelloService {

    @Override
    public String hello(String name) {
        return "hello! " + name;
    }
}
