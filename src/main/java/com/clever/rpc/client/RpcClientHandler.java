package com.clever.rpc.client;

import com.clever.rpc.pojo.RpcRequest;
import com.clever.rpc.pojo.RpcResponse;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author sunbin
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    private ConcurrentHashMap<String, RpcFuture> pendingRPC = new ConcurrentHashMap<>();

    private volatile Channel channel;

    private SocketAddress remoteAddress;

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }


    public Channel getChannel() {
        return channel;
    }

    /**
     * <p>
     *     绑定ip和端口后触发
     * </p>
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.remoteAddress = this.channel.remoteAddress();
        super.channelActive(ctx);
    }

    /**
     * <p>
     *     在ServerSocketChannel对应的pipeline中触发（channelFuture.addListener触发）
     * </p>
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.channel = ctx.channel();
    }

    /**
     * 返回数据处理
     *
     * @param ctx
     * @param response
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        String requestId = response.getRequestId();
        RpcFuture rpcFuture = pendingRPC.get(requestId);
        if (rpcFuture != null) {
            //删除
            pendingRPC.remove(requestId);
            rpcFuture.done(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Client  exception", cause);
        ctx.close();
    }

    public RpcFuture sendRequest(RpcRequest request) {
        CompletableFuture<ChannelFuture> completableFuture = new CompletableFuture<>();
        RpcFuture rpcFuture = new RpcFuture(request);
        pendingRPC.put(request.getRequestId(), rpcFuture);
        channel.writeAndFlush(request).addListener((ChannelFutureListener) future -> {
            completableFuture.complete(future);
            //取代 CountDownLatch 的 await
            CompletableFuture.allOf(completableFuture);
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            logger.error(e.getMessage());
//        }
        });

        return rpcFuture;
    }

    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
}
