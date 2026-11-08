package com.samdev.appointments.branchmanagement.web.cache;

import java.util.List;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import com.samdev.appointments.branchmanagement.web.dto.response.PublicBranchDetail;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicServiceCategoryResponse;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicServiceResponse;

/**
 * The caches of {@link PublicCatalog}. Each stores one response type as plain JSON (no type information in the
 * values), with the time to live and key prefix of {@code spring.cache.redis} as a safety net for a missed
 * eviction.
 */
@Configuration(proxyBeanMethods = false)
class PublicCatalogCaches {

	static final String BRANCH = "public-branch";
	static final String SERVICES = "public-services";
	static final String CATEGORIES = "public-service-categories";

	static final List<String> ALL = List.of(BRANCH, SERVICES, CATEGORIES);

	@Bean
	RedisCacheManagerBuilderCustomizer publicCatalogCacheConfigurations(JsonMapper json) {

		var types = json.getTypeFactory();

		return builder -> {
			var defaults = builder.cacheDefaults();
			builder.withCacheConfiguration(BRANCH, as(defaults, json, types.constructType(PublicBranchDetail.class)))
					.withCacheConfiguration(SERVICES, as(defaults, json,
							types.constructCollectionType(List.class, PublicServiceResponse.class)))
					.withCacheConfiguration(CATEGORIES, as(defaults, json,
							types.constructCollectionType(List.class, PublicServiceCategoryResponse.class)));
		};
	}

	private static RedisCacheConfiguration as(RedisCacheConfiguration defaults, JsonMapper json, JavaType type) {
		return defaults.serializeValuesWith(
				SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, type)));
	}
}
