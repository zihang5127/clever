package com.clever.rpc;

import com.clever.rpc.client.RpcClient;
import com.clever.rpc.register.ServiceDiscovery;
import com.clever.rpc.service.HelloService;
import com.clever.rpc.service.UserService;

import java.util.ArrayList;
import java.util.List;

public class RPCTest {
    public static void main(String[] args) {

        //注册的消费者列表
        List<Class<?>> classes = new ArrayList<>();
        classes.add(UserService.class);
        classes.add(HelloService.class);
        RpcClient rpcClient = null;


        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1", "rpc", classes);

        try {
            rpcClient = new RpcClient(serviceDiscovery);
            UserService syncClient = rpcClient.create(UserService.class,6000l);
            String result = syncClient.findById(1l);
            System.out.println(result);
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            //关闭连接
            rpcClient.stop();
        }
    }
}
