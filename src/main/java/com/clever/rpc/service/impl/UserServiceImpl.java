package com.clever.rpc.service.impl;

import com.clever.rpc.server.Service;
import com.clever.rpc.service.UserService;

/**
 * @author sunbin
 */
@Service
public class UserServiceImpl implements UserService {
    @Override
    public String findById(Long id) {
        return "123123";
    }
}
