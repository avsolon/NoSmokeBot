package org.example.nosmoke.service;

import lombok.RequiredArgsConstructor;
import org.example.nosmoke.controller.TelegramBot;
import org.example.nosmoke.model.UserState;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class UserResetScheduler {

    private final SmokingService smokingService;
    private final TelegramBot telegramBot;

    @Scheduled(cron = "0 * * * * ?")
    public void resetDailyTargets() {
        System.out.println("Метод resetDailyTargets вызван");
        List<UserState> allUsers = smokingService.findAllUsers();
        System.out.println("Найдено пользователей: " + allUsers.size());

        for (UserState user : allUsers) {
            if (user != null) {
                user.setTotalCigsToday(0);
                user.setAwaitingTarget(true);
                smokingService.saveUserState(user);
                String message = "Установите цель на сегодняшний день. Введите кол-во сижек:";
                telegramBot.sendMessage(message, user.getUserId());
            } else {
                System.out.println("Пользователь равен null");
            }
        }
    }
}

