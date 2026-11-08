package com.samdev.appointments.branchmanagement.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.application.command.ChangePoolBaseCapacity;
import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.web.dto.request.ChangePoolRequest;
import com.samdev.appointments.branchmanagement.web.dto.request.DefinePoolRequest;
import com.samdev.appointments.branchmanagement.web.dto.response.PoolResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

@Tag(name = "Capacity pools (admin)",
		description = "Named groups of staff at the branch whose capacity is shared by offerings.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin/capacity-pools")
class AdminCapacityPoolController {

	private final CapacityPoolCommandHandler commands;
	private final BranchQueries queries;

	AdminCapacityPoolController(CapacityPoolCommandHandler commands, BranchQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "List the capacity pools")
	List<PoolResponse> pools() {
		return queries.pools().stream().map(PoolResponse::of).toList();
	}

	@GetMapping("/{poolId}")
	@Operation(summary = "Get a capacity pool with its ETag")
	ResponseEntity<PoolResponse> pool(@PathVariable UUID poolId) {
		return current(new CapacityPoolId(poolId));
	}

	@PostMapping
	@Operation(summary = "Define a capacity pool")
	ResponseEntity<PoolResponse> define(@RequestBody @Valid DefinePoolRequest request) {
		var poolId = commands.handle(new DefineCapacityPool(request.name(), request.baseCapacity()));
		var pool = PoolResponse.of(queries.pool(poolId));

		return ResponseEntity.created(URI.create("/api/v1/admin/capacity-pools/" + poolId))
				.eTag(ETags.of(pool.version()))
				.body(pool);
	}

	@PatchMapping("/{poolId}")
	@Operation(summary = "Change a pool's base capacity (If-Match)")
	ResponseEntity<PoolResponse> change(@PathVariable UUID poolId,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ChangePoolRequest request) {

		var capacityPoolId = new CapacityPoolId(poolId);

		commands.handle(new ChangePoolBaseCapacity(capacityPoolId, ETags.expectedVersion(ifMatch),
				request.baseCapacity()));

		return current(capacityPoolId);
	}

	private ResponseEntity<PoolResponse> current(CapacityPoolId poolId) {

		var pool = PoolResponse.of(queries.pool(poolId));

		return ResponseEntity.ok().eTag(ETags.of(pool.version())).body(pool);
	}
}
