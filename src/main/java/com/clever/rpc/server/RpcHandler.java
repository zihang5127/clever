package com.clever.rpc.server;

import com.clever.rpc.pojo.RpcRequest;
import com.clever.rpc.pojo.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.reflect.FastClass;

import java.util.Map;

/**
 * @author sunbin
 */
public class RpcHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RpcHandler.class);
    private final Map<String, Object> handlerMap;
    public RpcHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    /**
     * 收到请求
     *
     * @param ctx
     * @param request
     */
    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final RpcRequest request) {
        RpcServer.submit(() -> {
            logger.debug("Receive request :{}", request.getRequestId());
            RpcResponse response = new RpcResponse();
            response.setRequestId(request.getRequestId());
            try {
                Object result = handle(request);
                response.setResult(result);
            } catch (Throwable t) {
                response.setError(t.toString());
                logger.error("RPC Server handle request error", t);
            }

            //写入并且冲刷缓冲区
            ctx.writeAndFlush(response);
        });
    }

    /**
     * 反射调用服务
     *
     * @param request
     * @return
     * @throws Throwable
     */
    private Object handle(RpcRequest request) throws Throwable {
        String className = request.getClassName();
        Object serviceBean = handlerMap.get(className);
        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();
        FastClass serviceFastClass = FastClass.create(serviceClass);
        int methodIndex = serviceFastClass.getIndex(methodName, parameterTypes);
        return serviceFastClass.invoke(methodIndex, serviceBean, parameters);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("server caught exception", cause);
        ctx.close();
    }
}
