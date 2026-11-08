package com.samdev.appointments.branchmanagement.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.application.command.SetWeeklyHours;
import com.samdev.appointments.branchmanagement.application.handler.OpeningHoursCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.web.dto.request.WeeklyHoursRequest;
import com.samdev.appointments.branchmanagement.web.dto.response.BranchResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

@Tag(name = "Branch (admin)",
		description = "The branch's details come from configuration; the branch manager sets its weekly hours.")
@SecurityRequirement(name = "keycloak")
@RestController
@RequestMapping("/api/v1/admin/branch")
class AdminBranchController {

	private final OpeningHoursCommandHandler commands;
	private final BranchQueries queries;

	AdminBranchController(OpeningHoursCommandHandler commands, BranchQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "Get the branch with its weekly hours; the ETag is the version of the hours")
	@RequiresRole(StaffRole.BRANCH_MANAGER)
	ResponseEntity<BranchResponse> branch() {
		return current();
	}

	@PutMapping("/weekly-hours")
	@Operation(summary = "Replace the weekly hours (branch manager, If-Match)")
	@RequiresRole(StaffRole.BRANCH_MANAGER)
	ResponseEntity<BranchResponse> weeklyHours(
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid WeeklyHoursRequest request) {

		commands.handle(new SetWeeklyHours(ETags.expectedVersion(ifMatch), request.toWeeklyHours()));

		return current();
	}

	private ResponseEntity<BranchResponse> current() {

		var branch = BranchResponse.of(queries.branch(), queries.openingHours());

		return ResponseEntity.ok().eTag(ETags.of(branch.version())).body(branch);
	}
}
