package com.clever.rpc.client;

import com.clever.rpc.pojo.RpcRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * @author sunbin
 */
public class ServiceProxy implements InvocationHandler {

    /**
     * 请求超时时间
     */
    private long timeout;

    public ServiceProxy(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws InterruptedException {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RpcFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture.get(timeout,TimeUnit.MILLISECONDS);
    }

}
