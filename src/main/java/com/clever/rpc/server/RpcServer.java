package com.clever.rpc.server;

import com.clever.rpc.pojo.RpcResponse;
import com.clever.rpc.coder.RpcDecoder;
import com.clever.rpc.coder.RpcEncoder;
import com.clever.rpc.pojo.RpcRequest;
import com.clever.rpc.register.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RPC Server
 *
 * @author sunbin
 */
@Component
public class RpcServer implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    /**
     * 服务地址
     */
    @Value("${rpc.server.port}")
    private int port;

    @Value("${zk.address}")
    private String registerAddress;

    @Value("${rpc.topic}")
    private String topic;

    /**
     * 注册的接口  暂时是tcp直连 没用到zookeeper
     */
    private Map<String, Object> handlerMap = new HashMap<>();
    private static ThreadPoolExecutor threadPoolExecutor;

    /**
     * 接收连接
     */
    private EventLoopGroup bossGroup = null;

    /**
     * 处理接收的链接
     */
    private EventLoopGroup workerGroup = null;

    /**
     * 启动时注册包含@Service的服务
     *
     * @throws BeansException
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            final ApplicationContext ctx = event.getApplicationContext();
            if (null == ctx.getParent()) {
                Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(Service.class);
                if (serviceBeanMap != null && !serviceBeanMap.isEmpty()) {
                    for (Object serviceBean : serviceBeanMap.values()) {
                        String interfaceName = serviceBean.getClass().getInterfaces()[0].getName();
                        handlerMap.put(interfaceName, serviceBean);
                    }
                }
            }

            //启动
            start();
        } catch (InterruptedException | UnknownHostException e) {
            logger.error("RpcServer failed", e);
        }
    }


    /**
     * @param task
     */
    public static void submit(Runnable task) {
        if (threadPoolExecutor == null) {
            synchronized (RpcServer.class) {
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = new ThreadPoolExecutor(16, 16, 600L,
                            TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));
                }
            }
        }
        threadPoolExecutor.submit(task);
    }

    /**
     * 启动Rpc
     *
     * @throws Exception
     */
    public void start() throws InterruptedException, UnknownHostException {
        if (bossGroup == null && workerGroup == null) {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) {
                            channel.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0))
                                    .addLast(new RpcDecoder(RpcRequest.class))
                                    .addLast(new RpcEncoder(RpcResponse.class))
                                    .addLast(new RpcHandler(handlerMap));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            //获取本机ip
            InetAddress inetAddress= InetAddress.getLocalHost();
            String host= inetAddress.getHostAddress();

            ChannelFuture future = bootstrap.bind(host, port).sync();
            logger.info("Server started on port :{}", port);

            //注册
            new ServiceRegistry(registerAddress,port).register(handlerMap,topic,host);
            future.channel().closeFuture().sync();
        }
    }

    @PreDestroy
    public void destory() {
        //TODO 容器销毁 删除zookeeper注册信息

    }
}
