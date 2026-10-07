package com.anticipate.listr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ListrApplication {

	public static void main(String[] args) {
		SpringApplication.run(ListrApplication.class, args);
	}

}
