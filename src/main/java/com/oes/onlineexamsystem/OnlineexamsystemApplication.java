package com.oes.onlineexamsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnlineexamsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnlineexamsystemApplication.class, args);
	}

}
