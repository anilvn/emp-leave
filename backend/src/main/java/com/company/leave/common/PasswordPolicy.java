package com.company.leave.common;

import org.springframework.stereotype.Component;

/** At least 8 characters, containing at least one letter and one digit. */
@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public boolean isValid(String raw) {
		if (raw == null || raw.length() < MIN_LENGTH) {
			return false;
		}
		boolean letter = false;
		boolean digit = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			letter |= Character.isLetter(c);
			digit |= Character.isDigit(c);
		}
		return letter && digit;
	}
}
