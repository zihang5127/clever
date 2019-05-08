package com.clever.rpc.client;

import com.clever.rpc.pojo.RpcRequest;
import com.clever.rpc.pojo.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author sunbin
 */
public class RpcFuture implements Future<Object> {
    private static final Logger logger = LoggerFactory.getLogger(RpcFuture.class);
    private Sync sync;
    private RpcRequest request;
    private RpcResponse response;
    private long startTime;
    private long responseTimeThreshold = 5000;

    public RpcFuture(RpcRequest request) {
        this.sync = new Sync();
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() {
        sync.acquire(-1);
        if (this.response != null) {
            return this.response.getResult();
        } else {
            return null;
        }
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException {

        //获取锁，超时时间timeout
        boolean success = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (success) {
            if (this.response != null) {
                return this.response.getResult();
            } else {
                return null;
            }
        } else {
            //请求超时
            throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                    + ". Request class name: " + this.request.getClassName()
                    + ". Request method: " + this.request.getMethodName());
        }
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    public void done(RpcResponse response) {
        this.response = response;

        //释放锁
        sync.release(1);

        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime > this.responseTimeThreshold) {
            logger.warn("Service response time is too slow. Request id :{}", response.getRequestId(), ". Response Time :{}", responseTime + "ms");
        }
    }

    /**
     * AQS
     */
    static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        /**
         * future status
         */
        private final int done = 1;
        private final int pending = 0;

        @Override
        protected boolean tryAcquire(int arg) {
            return getState() == done;
        }

        /**
         * 释放锁
         *
         * @param arg
         * @return
         */
        @Override
        protected boolean tryRelease(int arg) {
            if (getState() == pending) {
                //尝试释放锁  当状态为0的时候获取锁
                if (compareAndSetState(pending, done)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }

        public boolean isDone() {
            return getState() == done;
        }
    }
}
