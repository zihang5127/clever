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

    /**
     * 生产者服务节点
     */
    private Set<String> serverHandler = new HashSet<>();

    public ServiceDiscovery(String registerAddress, String topic, List<Class<?>> services) {
        this.topic = topic;
        this.services = services;
        zkClient = new ZkClient(registerAddress);

        //注册消费者
        register();

        //服务发现
        discovery();

        //链接节点
        ConnectManage.getInstance().initConnection(serverHandler);
    }

    /**
     * 注册消费者
     */
    public void register() {
        String rootPath = Costs.ZK_ROOT;
        String customerPath = Costs.ZK_SERVICE_CUSTOMER;
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
                boolean serviceExist = zkClient.exists(rootPath + "/" + topic + "/" + serviceName + "/" + customerPath);
                if (!serviceExist) {
                    zkClient.createPersistent(rootPath + "/" + topic + "/" + serviceName + "/" + customerPath);
                }
                //注册
                String path = rootPath + "/" + topic + "/" + serviceName + "/" + customerPath + "/" + host;
                if(!zkClient.exists(path)){
                    zkClient.createEphemeral(path);
                }
                logger.info("Service customer registry Success service :{}", serviceName);
            } catch (UnknownHostException e) {
                logger.error("Service customer registry failed service :{}", serviceName, e);
            }
        }
    }

    /**
     * 服务发现
     */
    public void discovery() {
        for (String serviceName : zkClient.getChildren(Costs.ZK_ROOT + "/" + topic)) {
            List<String> ips = zkClient.getChildren(Costs.ZK_ROOT + "/" + topic + "/" + serviceName + "/" + Costs.ZK_SERVICE_PROVIDER);
            for (String ip : ips) {
                serverHandler.add(ip);
            }
        }
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
