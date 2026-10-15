package ru.zhendozzz.tgbot.messageStatBot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MessageStatBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(MessageStatBotApplication.class, args);
	}

}
