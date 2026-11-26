package personalfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "personalfinance")
public class PersonalFinanceBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PersonalFinanceBackendApplication.class, args);
	}

}
