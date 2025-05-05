package org.example.nosmoke.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class UserState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private long userId;

    @Column(name = "current_cigs_per_day")
    private int currentCigsPerDay;

    @Column(name = "target_cigs_per_day")
    private int targetCigsPerDay;

    @Column(name = "awaiting_target")
    @Builder.Default
    private Boolean awaitingTarget = false; // Признак, показывающий, ждет ли бот ввода цели

    @Column(name = "last_smoked_time")
    private Long lastSmokedTime;

    @Column(name = "is_timer_running")
    private Boolean isTimerRunning;

    @Column(name = "total_cigs_today")
    @Builder.Default
    private Integer totalCigsToday = 0;

    @Column(name = "next_allowed_time")
    private Long nextAllowedTime;

    @Column(name = "money_saved_today")
    private Double moneySavedToday;

    public boolean isAwaitingTarget() {
        return awaitingTarget != null && awaitingTarget.booleanValue();
    }
}
