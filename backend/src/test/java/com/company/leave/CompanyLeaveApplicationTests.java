package com.company.leave;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CompanyLeaveApplicationTests extends AbstractPostgresIT {

	@Autowired
	DataSource dataSource;

	@Test
	void contextLoadsAgainstPostgres() throws Exception {
		try (var conn = dataSource.getConnection()) {
			assertThat(conn.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
			assertThat(conn.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
		}
	}
}
