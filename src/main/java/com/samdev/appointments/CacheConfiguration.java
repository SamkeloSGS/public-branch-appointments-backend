package com.samdev.appointments;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Caching in Redis ({@code spring.cache.redis} and {@code spring.data.redis} in {@code application.yml}), shared by
 * every instance. A cache is only a shortcut: when Redis fails, the error is logged and the request reads the
 * database instead of failing. Modules declare their own caches and empty them when the data changes.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfiguration implements CachingConfigurer {

	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}
}
