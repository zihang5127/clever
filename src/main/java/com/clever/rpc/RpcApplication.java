package com.clever.rpc;

import com.clever.rpc.server.RpcServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author sunbin
 */
@SpringBootApplication
public class RpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(RpcApplication.class, args).addApplicationListener(new RpcServer());
    }
}
