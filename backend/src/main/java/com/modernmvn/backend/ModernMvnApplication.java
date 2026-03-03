package com.modernmvn.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ModernMvnApplication {

	public static void main(String[] args) {
		SpringApplication.run(ModernMvnApplication.class, args);
	}

}
