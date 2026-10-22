package com.company.leave.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class CommonConfigTest {

	private final CommonConfig config = new CommonConfig();

	@Test
	void clockIsUtc() {
		assertThat(config.clock().getZone()).isEqualTo(ZoneOffset.UTC);
	}

	@Test
	void encoderHashesWithBcryptAndMatches() {
		PasswordEncoder encoder = config.passwordEncoder();
		String hash = encoder.encode("abc12345");
		assertThat(hash).startsWith("$2").isNotEqualTo("abc12345");
		assertThat(encoder.matches("abc12345", hash)).isTrue();
		assertThat(encoder.matches("wrong123", hash)).isFalse();
	}
}
