package org.example.nosmoke.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BotConfig {
    @Value("${bot.token}")
    private String token;

    public String getToken() {
        return token;
    }

}
