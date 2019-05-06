package com.clever.rpc.client;

/**
 * @author sunbin
 */
public interface RpcCallback {

    /**
     * 成功
     *
     * @param result
     */
    void success(Object result);

    /**
     * 失败
     *
     * @param e
     */
    void fail(Exception e);

}
