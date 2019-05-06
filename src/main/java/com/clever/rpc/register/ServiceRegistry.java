package com.clever.rpc.register;

import com.clever.rpc.common.Costs;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 服务注册
 *
 * @author sunbin
 */
public class ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    private String registerAddress;
    private int port;

    public ServiceRegistry(String registerAddress, int port) {
        this.registerAddress = registerAddress;
        this.port = port;
    }

    /**
     * 注册服务
     *
     * @param handlerMap 服务集合
     * @param host       服务ip
     */
    public void register(Map<String, Object> handlerMap, String topic, String host) {
        String rootPath = Costs.ZK_ROOT;
        String providerPath = Costs.ZK_SERVICE_PROVIDER;
        ZkClient zkClient = new ZkClient(registerAddress);
        boolean rootExists = zkClient.exists(rootPath);
        //创建根
        if (!rootExists) {
            zkClient.createPersistent(rootPath);
        }
        //创建服务节点
        for (Map.Entry<String, Object> entry : handlerMap.entrySet()) {
            String serviceName = entry.getKey();

            boolean topicExists = zkClient.exists(rootPath + "/" + topic);
            if (!topicExists) {
                zkClient.createPersistent(rootPath + "/" + topic);
            }

            boolean serviceProviderExist = zkClient.exists(rootPath + "/" + topic + "/" + serviceName);

            if (!serviceProviderExist) {
                zkClient.createPersistent(rootPath + "/" + topic + "/" + serviceName);
            }
            boolean serviceExist = zkClient.exists(rootPath + "/" + topic + "/" + serviceName + "/" + providerPath);

            if (!serviceExist) {
                zkClient.createPersistent(rootPath + "/" + topic + "/" + serviceName + "/" + providerPath);
            }

            //注册
            String path = rootPath + "/" + topic + "/" + serviceName + "/" + providerPath + "/" + host + ":" + port;
            if (!zkClient.exists(path)){
                zkClient.createEphemeral(rootPath + "/" + topic + "/" + serviceName + "/" + providerPath + "/" + host + ":" + port);
            }
            logger.info("Service provider registry Success service :{}", serviceName);
        }
    }
}
