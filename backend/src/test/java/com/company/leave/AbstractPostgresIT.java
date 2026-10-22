package com.company.leave;

import java.util.TimeZone;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Base for integration tests: one shared Postgres 16 container per JVM. H2 is never used. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	static {
		// Same UTC pin as the application, for runs that bypass surefire's -Duser.timezone.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
