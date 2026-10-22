package com.company.leave;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CompanyLeaveApplication {

	public static void main(String[] args) {
		// Pin the JVM to UTC: timestamps are timestamptz, and Postgres rejects legacy zone ids such as Asia/Calcutta.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(CompanyLeaveApplication.class, args);
	}

}
