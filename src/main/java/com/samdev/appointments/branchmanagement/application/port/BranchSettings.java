package com.samdev.appointments.branchmanagement.application.port;

import com.samdev.appointments.branchmanagement.BranchProfile;

/**
 * The branch this application runs for, from configuration.
 */
public interface BranchSettings {

	BranchProfile branch();
}
