package com.clever.rpc.client;

import com.clever.rpc.register.ServiceDiscovery;

import java.lang.reflect.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RPC Client
 *
 * @author sunbin
 */
public class RpcClient {
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16,
            600L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));
    public ServiceDiscovery serviceDiscovery;

    /**
     * Rpc直连
     *
     * @param serverAddress
     */
    public RpcClient(String serverAddress) {
        ConnectManage.getInstance().initConnection(serverAddress);
    }

    /**
     * 连接注册中心
     *
     * @param serviceDiscovery
     */
    public RpcClient(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    /**
     * 创建连接
     *
     * @param interfaceClass
     * @param <T>
     * @return
     */
    public <T> T create(Class<T> interfaceClass,long timeout) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new ServiceProxy(timeout,interfaceClass)
        );
    }

    /**
     * 关闭链接
     */
    public void stop() {
        threadPoolExecutor.shutdown();
        serviceDiscovery.stop();
        ConnectManage.getInstance().stop();
    }
}

