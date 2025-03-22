package kz.finance.fintrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
public class FintrackApplication {

	public static void main(String[] args) {SpringApplication.run(FintrackApplication.class, args);
	}

}
