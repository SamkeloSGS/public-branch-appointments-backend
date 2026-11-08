package com.samdev.appointments.shared;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * One page of a list that grows without bound. Requests name the page with {@code page} (0-based, default 0) and
 * {@code size} (1 to {@value #MAX_SIZE}, default {@value #DEFAULT_SIZE}); lists bounded by the branch itself (the
 * catalog, the staff, one day's appointments) are returned whole.
 *
 * @param items the page's items, in the list's order
 * @param page the page number, 0-based
 * @param size the requested page size
 * @param totalItems the number of items on all pages
 * @param totalPages the number of pages; 0 for an empty list
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapping) {
		return new PageResponse<>(page.getContent().stream().map(mapping).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	/**
	 * The page asked for; the controller's parameter constraints have checked the bounds.
	 */
	public static Pageable request(int page, int size) {
		return PageRequest.of(page, size);
	}
}
