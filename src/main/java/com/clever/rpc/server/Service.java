package com.clever.rpc.server;

import java.lang.annotation.*;

import org.springframework.stereotype.Component;

/**
 * @author sunbin
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Service {
    String value() default "";
}
