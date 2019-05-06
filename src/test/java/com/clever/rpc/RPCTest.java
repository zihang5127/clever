package com.clever.rpc;

import com.clever.rpc.client.RpcClient;
import com.clever.rpc.service.UserService;

public class RPCTest {
    public static void main(String[] args) {
        RpcClient rpcClient = new RpcClient("127.0.0.1:8011");

        try {
            UserService syncClient = rpcClient.create(UserService.class);
            String result = syncClient.findById(1l);
            System.out.println(result);
        } catch (Exception e) {
            System.out.println(e);
        }finally {
            //关闭连接
            rpcClient.stop();
        }
    }
}
