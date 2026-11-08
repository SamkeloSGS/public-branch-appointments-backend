package com.samdev.appointments.branchmanagement.web.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.web.cache.PublicCatalog;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicBranchDetail;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicServiceCategoryResponse;
import com.samdev.appointments.branchmanagement.web.dto.response.PublicServiceResponse;

/**
 * Customer-facing endpoints. Only active offerings are ever returned. Answers come from the Redis cache
 * ({@link PublicCatalog}).
 */
@Tag(name = "Branch (public)", description = "Customer endpoints. Only active offerings are returned.")
@RestController
@RequestMapping("/api/v1")
class PublicBranchController {

	private final PublicCatalog catalog;

	PublicBranchController(PublicCatalog catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/branch")
	@Operation(summary = "Get the branch with its hours and offered services")
	PublicBranchDetail branch() {
		return catalog.branch();
	}

	@GetMapping("/services")
	@Operation(summary = "List active services, optionally of one category or customer segment, ordered by name")
	List<PublicServiceResponse> services(@RequestParam @Nullable UUID categoryId,
			@RequestParam @Nullable CustomerSegment segment) {
		return catalog.services(categoryId, segment);
	}

	@GetMapping("/service-categories")
	@Operation(summary = "List active service categories, optionally of one customer segment, with their active "
			+ "services")
	List<PublicServiceCategoryResponse> categories(@RequestParam @Nullable CustomerSegment segment) {
		return catalog.categories(segment);
	}
}
