package com.clever.rpc.client;

import com.clever.rpc.pojo.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;


/**
 * @author sunbin
 */
public class ServiceProxy implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RpcFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture.get();
    }

}
