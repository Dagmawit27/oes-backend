package com.oes.onlineexamsystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OnlineexamsystemApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() throws SQLException {
		assertNotNull(dataSource, "DataSource should be configured and injected");
		try (Connection connection = dataSource.getConnection()) {
			assertNotNull(connection, "Database connection should be established");
			assertTrue(connection.isValid(5), "Database connection should be valid");
		}
	}

}
