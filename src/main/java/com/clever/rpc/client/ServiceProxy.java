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
    private static final Logger logger = LoggerFactory.getLogger(ServiceProxy.class);

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);

        logger.debug(method.getDeclaringClass().getName());
        logger.debug(method.getName());

        for (Class<?> type : method.getParameterTypes()) {
            logger.debug(type.getName());
        }
        for (Object obj : args) {
            logger.debug(obj.toString());
        }

        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RpcFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture.get();
    }

}
