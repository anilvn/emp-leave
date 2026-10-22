package com.company.leave.common.internal;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class CommonConfig {

	/** The only source of "now"; tests replace it with a fixed clock. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
