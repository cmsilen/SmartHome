package it.unipi.SmartHome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartHomeApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartHomeApplication.class, args);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Shutting down the application...");
        }));
	}

}
