# clever
### How to use
1. Configuration application. The properties
```properties
zk.address=127.0.0.1:2181
rpc.server.port=8088
rpc.topic=rpc
```

2. Define an interface:
```java
public interface HelloService { 
    String hello(String name); 
}
```
3. Implement the interface with annotation @Service:
```java
@Service
public class HelloServiceImpl implements HelloService {
    
    @Override
    public String hello(String name) {
        return "Hello! " + name;
    }
}
```
4. Run zookeeper
5. Start server:
```java
@SpringBootApplication
public class RpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(RpcApplication.class, args).addApplicationListener(new RpcServer());
    }
}

```
6. Use the client:

```java
public class RPCTest {
    public static void main(String[] args) {
        List<Class<?>> classes = new ArrayList<>();
        classes.add(UserService.class);
        classes.add(HelloService.class);
        RpcClient rpcClient = null;

        try {
            ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1", "rpc", classes);
            rpcClient = new RpcClient(serviceDiscovery);
            HelloService syncClient = rpcClient.create(HelloService.class,6000l);
            String result = syncClient.hello("tom");
            System.out.println(result);
        } finally {
            rpcClient.stop();
        }
    }
}
```