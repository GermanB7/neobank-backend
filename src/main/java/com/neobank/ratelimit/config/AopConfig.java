package com.neobank.ratelimit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP configuration for aspect-oriented programming.
 *
 * Enables @Aspect components like RateLimitAspect to work properly.
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
