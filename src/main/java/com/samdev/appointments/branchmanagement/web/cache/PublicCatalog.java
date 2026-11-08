package com.samdev.appointments.branchmanagement.web.cache;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.domain.entity.Service;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicBranchDetail;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicServiceCategoryResponse;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicServiceResponse;

/**
 * The customer-facing reads, cached in Redis as the responses themselves ({@link PublicCatalogCaches}). Every
 * customer page starts with them, and they change only when the branch manager changes the hours or the catalog,
 * which empties the caches ({@link PublicCatalogEviction}).
 */
@Component
public class PublicCatalog {

	private final BranchQueries queries;

	PublicCatalog(BranchQueries queries) {
		this.queries = queries;
	}

	@Cacheable(cacheNames = PublicCatalogCaches.BRANCH, key = "'branch'")
	public PublicBranchDetail branch() {
		return PublicBranchDetail.of(queries.branch(), queries.openingHours(), queries.activeOfferings());
	}

	@Cacheable(cacheNames = PublicCatalogCaches.SERVICES, key = "#categoryId + ':' + #segment")
	public List<PublicServiceResponse> services(@Nullable UUID categoryId, @Nullable CustomerSegment segment) {
		return queries.activeServices(categoryId == null ? null : new ServiceCategoryId(categoryId), segment).stream()
				.map(PublicServiceResponse::of)
				.toList();
	}

	@Cacheable(cacheNames = PublicCatalogCaches.CATEGORIES, key = "'segment:' + #segment")
	public List<PublicServiceCategoryResponse> categories(@Nullable CustomerSegment segment) {

		var byCategory = queries.activeServices(null, segment).stream()
				.collect(Collectors.groupingBy(Service::getCategoryId,
						Collectors.mapping(PublicServiceResponse::of, Collectors.toList())));

		return queries.categories(segment, false).stream()
				.map(it -> PublicServiceCategoryResponse.of(it, byCategory.getOrDefault(it.getId(), List.of())))
				.toList();
	}
}
