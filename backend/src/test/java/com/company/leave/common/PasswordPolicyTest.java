package com.company.leave.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void acceptsEightCharsWithLetterAndDigit() {
		assertThat(policy.isValid("abc12345")).isTrue();
	}

	@Test
	void rejectsTooShort() {
		assertThat(policy.isValid("short1")).isFalse();
	}

	@Test
	void rejectsNoDigit() {
		assertThat(policy.isValid("abcdefgh")).isFalse();
	}

	@Test
	void rejectsNoLetter() {
		assertThat(policy.isValid("12345678")).isFalse();
	}

	@Test
	void rejectsNull() {
		assertThat(policy.isValid(null)).isFalse();
	}
}
