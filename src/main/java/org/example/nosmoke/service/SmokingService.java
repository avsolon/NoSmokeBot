package org.example.nosmoke.service;

import lombok.RequiredArgsConstructor;
import org.example.nosmoke.controller.TelegramBot;
import org.example.nosmoke.model.UserState;
import org.example.nosmoke.repository.UserStateRepository;


import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class SmokingService {

    private final UserStateRepository repository;

    public void saveUserState(UserState state) {
        repository.save(state);
    }

    public UserState findByUserId(long userId) {
        return repository.findByUserId(userId);
    }

    public List<UserState> findAllUsers() {
        return repository.findAll();
    }

    public boolean canSmokeNow(UserState state) {
        return System.currentTimeMillis() >= state.getNextAllowedTime();
    }

    public long calculateInterval(UserState state) {
        final long hoursInADay = 16L;
        return (hoursInADay * 3600 * 1000) / state.getTargetCigsPerDay();
    }


    public void setNextSmokeTime(UserState state) {
        state.setLastSmokedTime(System.currentTimeMillis());
        state.setNextAllowedTime(state.getLastSmokedTime() + calculateInterval(state));
    }

    public double calculateMoneySaved(UserState state) {
        return (state.getCurrentCigsPerDay() - state.getTotalCigsToday()) * 10;
    }


}
