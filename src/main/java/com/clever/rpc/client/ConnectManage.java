package com.clever.rpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author sunbin
 */
public class ConnectManage {
    private static final Logger logger = LoggerFactory.getLogger(ConnectManage.class);
    private volatile static ConnectManage connectManage;
    private long connectTimeoutMillis = 6000;
    private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16,
            600L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));
    private volatile boolean isRuning = true;
    private static final int INDEX_MIN_VALUE = 1;
    private Map<String, CopyOnWriteArrayList<RpcClientHandler>> connectedHandlersMap = new HashMap<>();

    /**
     * <p>
     * CopyOnWrite容器即写时复制的容器。通俗的理解是当我们往一个容器添加元素的时候，不直接往当前容器添加，而是
     * 先将当前容器进行Copy，复制出一个新的容器，然后新的容器里添加元素，添加完元素之后，再将原容器的引用指向
     * 新的容器。这样做的好处是我们可以对CopyOnWrite容器进行并发的读，而不需要加锁，因为当前容器不会添加任何
     * 元素。所以CopyOnWrite容器也是一种读写分离的思想，读和写不同的容器
     * </p>
     */
    private CopyOnWriteArrayList<RpcClientHandler> connectedHandlers;
    private Map<String, RpcClientHandler> connectedServerNodes = new ConcurrentHashMap<>();
    private ReentrantLock lock = new ReentrantLock();
    private Condition connected = lock.newCondition();
    private AtomicInteger roundRobin = new AtomicInteger(0);

    private ConnectManage() {
    }

    public static ConnectManage getInstance() {
        if (connectManage == null) {
            synchronized (ConnectManage.class) {
                if (connectManage == null) {
                    connectManage = new ConnectManage();
                }
            }
        }
        return connectManage;
    }

    /**
     * Rpc直连
     *
     * @param serverAddress
     */
    public void initConnection(String serverAddress) {
        String[] array = serverAddress.split(":");
        String host = array[0];
        int port = Integer.parseInt(array[1]);

        threadPoolExecutor.submit(() -> {
            Bootstrap b = new Bootstrap();
            b.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new RpcClientInitializer());
            ChannelFuture channelFuture = b.connect(new InetSocketAddress(host, port));
            channelFuture.addListener((ChannelFutureListener) channelFuture1 -> {
                if (channelFuture1.isSuccess()) {
                    logger.debug("Connect to remote server");
                    RpcClientHandler handler = channelFuture1.channel().pipeline().get(RpcClientHandler.class);
                    addHandler(handler);
                }
            });
        });
    }

    /**
     * 更新Rpc链接
     *
     * @param serverAddressMap
     */
    public void updateConnectedServer(Map<String, Set<String>> serverAddressMap) {
        if (serverAddressMap != null && !serverAddressMap.isEmpty()) {
            Map<String, Set<InetSocketAddress>> newServiceNodesMap = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : serverAddressMap.entrySet()) {
                Set<String> serverAddress = entry.getValue();
                Set<InetSocketAddress> newServiceNode = new HashSet<>();
                for (String address : serverAddress) {
                    String[] array = address.split(":");
                    String host = array[0];
                    int port = Integer.parseInt(array[1]);
                    final InetSocketAddress remotePeer = new InetSocketAddress(host, port);
                    newServiceNode.add(remotePeer);
                }
                newServiceNodesMap.put(entry.getKey(), newServiceNode);
            }

            for (Map.Entry<String, Set<InetSocketAddress>> entry : newServiceNodesMap.entrySet()) {
                connectedHandlers = new CopyOnWriteArrayList<>();
                for (final InetSocketAddress socketAddress : entry.getValue()) {
                    if (!connectedServerNodes.keySet().contains(entry.getKey()) || !entry.getValue().contains(connectedServerNodes.get(entry.getKey()).getRemoteAddress())) {
                        connectServerNode(entry.getKey(), socketAddress);
                    }
                }
            }

            //删除失效的链接
            for (Map.Entry<String, CopyOnWriteArrayList<RpcClientHandler>> oldConnectedHandlers : connectedHandlersMap.entrySet()) {
                for (RpcClientHandler oldConnectedServerHandler : oldConnectedHandlers.getValue()) {
                    SocketAddress oldRemoteNode = oldConnectedServerHandler.getRemoteAddress();
                    String oldServiceName = oldConnectedServerHandler.getServiceName();
                    for (Map.Entry<String, Set<InetSocketAddress>> newInetSocketAddress : newServiceNodesMap.entrySet()) {
                        if (oldServiceName.equals(newInetSocketAddress.getKey()) && !newInetSocketAddress.getValue().contains(oldRemoteNode)) {
                            logger.info("Remove server invalid  node :{}" + oldRemoteNode);
                            RpcClientHandler handler = connectedServerNodes.get(oldServiceName);
                            if (handler != null) {
                                handler.close();
                            }
                            connectedServerNodes.remove(oldServiceName);
                            oldConnectedHandlers.getValue().remove(oldConnectedServerHandler);
                        }
                    }
                }
            }
        } else {
            //所有服务都失效
            logger.error("No available server node. Close all handler");
            for (Map.Entry<String, CopyOnWriteArrayList<RpcClientHandler>> entry : connectedHandlersMap.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    entry.getValue().forEach(connectedServerHandler -> {
                        RpcClientHandler handler = connectedServerNodes.get(connectedServerHandler.getServiceName());
                        handler.close();
                        connectedServerNodes.remove(connectedServerHandler.getServiceName());
                    });
                }
                entry.getValue().clear();
            }
        }
    }

    /**
     * 连接到节点
     *
     * @param socketAddress
     */
    private void connectServerNode(String serviceName, InetSocketAddress socketAddress) {
        threadPoolExecutor.submit(() -> {
            Bootstrap b = new Bootstrap();
            b.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new RpcClientInitializer());

            ChannelFuture channelFuture = b.connect(socketAddress);
            channelFuture.addListener((ChannelFutureListener) channelFuture1 -> {
                if (channelFuture1.isSuccess()) {
                    logger.debug("Successfully connect to remote server , host :{}", socketAddress);
                    RpcClientHandler handler = channelFuture1.channel().pipeline().get(RpcClientHandler.class);
                    handler.setServiceName(serviceName);
                    addHandler(handler);
                }
            });
        });
    }

    private void addHandler(RpcClientHandler handler) {
        String serviceName = handler.getServiceName();
        connectedHandlers.add(handler);
        connectedHandlersMap.put(serviceName, connectedHandlers);
        connectedServerNodes.put(serviceName, handler);
        signalAvailableHandler();
    }

    /**
     * 唤醒在condition的线程
     */
    private void signalAvailableHandler() {
        lock.lock();
        try {
            //唤醒所有在Condition阻塞队列中的线程，让其继续执行
            connected.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 等待，直到被signal/signalAll唤醒
     *
     * @return
     * @throws InterruptedException
     */
    private boolean waitingForHandler() throws InterruptedException {
        lock.lock();
        try {
            //当前线程添加到Condition阻塞队，线程被阻塞不会继续执行
            return connected.await(this.connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }


    /**
     * 轮询获取服务节点索引
     *
     * @param size
     * @return
     */
    private AtomicInteger getIndex(int size) {
        if (this.roundRobin.getAndAdd(1) > size) {
            this.roundRobin.set(INDEX_MIN_VALUE);
        }
        return this.roundRobin;
    }

    public RpcClientHandler chooseHandler(String serviceName) {
        int size = 0;

        if (connectedHandlersMap.get(serviceName) != null) {
            size = connectedHandlersMap.get(serviceName).size();
        }

        while (isRuning && size <= 0) {
            try {
                boolean available = waitingForHandler();
                if (available) {
                    size = connectedHandlersMap.get(serviceName).size();
                }
            } catch (InterruptedException e) {
                logger.error("Get available node Failed", e);
                throw new RuntimeException("Can't connect any servers!", e);
            }
        }
        int index = (getIndex(size).get() + size) % size;
        return connectedHandlersMap.get(serviceName).get(index);
    }

    /**
     * 关闭连接
     */
    public void stop() {
        isRuning = false;
        if (connectedHandlersMap != null && connectedHandlersMap.size() > 0) {
            connectedHandlersMap.forEach((k, v) -> v.forEach(RpcClientHandler::close));
        }
        signalAvailableHandler();
        threadPoolExecutor.shutdown();
        eventLoopGroup.shutdownGracefully();
    }


}
