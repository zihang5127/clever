# clever
### 如何使用?
**1、 配置**
```properties
zk.address=127.0.0.1:2181
rpc.server.port=8088
rpc.topic=rpc
```

**1、定义接口**
```java
public interface HelloService {
    String hello(String name); 
}
```
**3、 实现接口并且加@Service注解**
```java
@Service
public class HelloServiceImpl implements HelloService {
    
    @Override
    public String hello(String name) {
        return "Hello! " + name;
    }
}
```
**4、 运行 zookeeper**
```
For example: zookeeper is running on 127.0.0.1:2181
```
**5、 启动服务端:**
```java
@SpringBootApplication
public class RpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(RpcApplication.class, args).addApplicationListener(new RpcServer());
    }
}

```
**6、 启动客户端:**

```java
public class RPCTest {
    public static void main(String[] args) {
        List<Class<?>> classes = new ArrayList<>();
        classes.add(HelloService.class);

        try {
            ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181", "rpc", classes);
            RpcClient rpcClient = new RpcClient(serviceDiscovery);
            HelloService syncClient = rpcClient.create(HelloService.class,6000l);
            String result = syncClient.hello("tom");
            System.out.println(result);
        } finally {
            rpcClient.stop();
        }
    }
}
```