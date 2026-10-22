package com.company.leave;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(CompanyLeaveApplication.class).verify();
	}
}
