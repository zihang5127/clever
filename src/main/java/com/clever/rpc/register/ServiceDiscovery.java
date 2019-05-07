package com.clever.rpc.register;

import com.clever.rpc.client.ConnectManage;
import com.clever.rpc.common.Costs;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务发现
 *
 * @author sunbin
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    /**
     * 注册消费者服务
     */
    private List<Class<?>> services;

    /**
     * zk
     */
    private ZkClient zkClient;

    /**
     * zk topic
     */
    private String topic;


    public ServiceDiscovery(String registerAddress, String topic, List<Class<?>> services) {
        this.topic = topic;
        this.services = services;
        zkClient = new ZkClient(registerAddress);

        //注册消费者
        register();

        //服务发现
        discovery();
    }

    /**
     * 注册消费者
     */
    public void register() {
        String rootPath = Costs.ZK_ROOT;
        String consumerPath = Costs.ZK_SERVICE_CONSUMER;
        boolean rootExists = zkClient.exists(rootPath);
        //创建根
        if (!rootExists) {
            zkClient.createPersistent(rootPath);
        }
        for (Class<?> cls : services) {
            String serviceName = cls.getName();
            try {
                //获取本机ip
                InetAddress inetAddress = InetAddress.getLocalHost();
                String host = inetAddress.getHostAddress();

                boolean serviceProviderExist = zkClient.exists(rootPath + "/" + topic);
                if (!serviceProviderExist) {
                    zkClient.createPersistent(rootPath + "/" + topic);
                }

                boolean topicExists = zkClient.exists(rootPath + "/" + topic + "/" + serviceName);
                if (!topicExists) {
                    zkClient.createPersistent(rootPath + "/" + topic + "/" + serviceName);
                }
                boolean serviceExist = zkClient.exists(rootPath + "/" + topic + "/" + serviceName + "/" + consumerPath);
                if (!serviceExist) {
                    zkClient.createPersistent(rootPath + "/" + topic + "/" + serviceName + "/" + consumerPath);
                }
                //注册
                String path = rootPath + "/" + topic + "/" + serviceName + "/" + consumerPath + "/" + host;
                if (!zkClient.exists(path)) {
                    zkClient.createEphemeral(path);
                }
                logger.info("Service consumer registry Success service :{}", serviceName);
            } catch (UnknownHostException e) {
                logger.error("Service consumer registry failed service :{}", serviceName, e);
            }
        }
    }

    /**
     * 服务发现
     */
    public void discovery() {

        updateServerHandler();

        //监听子节点
        for (Class<?> cls : services) {
            String path = Costs.ZK_ROOT + "/" + topic + "/" + cls.getName() + "/" + Costs.ZK_SERVICE_PROVIDER;
            zkClient.subscribeChildChanges(path, (s, list) -> discovery());
        }
    }

    /**
     * 更新链接
     */
    private void updateServerHandler() {

        Set<String> socketAddress = new HashSet<>();
        for (String serviceName : zkClient.getChildren(Costs.ZK_ROOT + "/" + topic)) {
            List<String> ips = zkClient.getChildren(Costs.ZK_ROOT + "/" + topic + "/" + serviceName + "/" + Costs.ZK_SERVICE_PROVIDER);
            for (String remote : ips) {
                socketAddress.add(remote);
            }
        }

        //连接远程Node
        ConnectManage.getInstance().updateConnectedServer(socketAddress);
    }

    /**
     * 关闭zookeeper链接
     */
    public void stop() {
        if (zkClient != null) {
            zkClient.close();
        }
    }
}
