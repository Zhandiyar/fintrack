package kz.finance.fintrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FinTrackApplication {

	public static void main(String[] args) {SpringApplication.run(FinTrackApplication.class, args);
	}

}
