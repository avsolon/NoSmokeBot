package org.example.nosmoke.repository;

import org.example.nosmoke.model.UserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserStateRepository extends JpaRepository<UserState, Long> {
    UserState findByUserId(Long userId);
    List<UserState> findAll();
}
