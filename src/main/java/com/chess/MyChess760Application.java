package com.chess;

import org.springframework.boot.SpringApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class MyChess760Application {

	public static void main(String[] args) {
		SpringApplication.run(MyChess760Application.class, args);
		log.debug("i am Spiderman");
	}

}
