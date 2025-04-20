package kz.finance.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinanceSecurityApplication {

	public static void main(String[] args) {SpringApplication.run(FinanceSecurityApplication.class, args);
	}

}
