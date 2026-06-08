package ru.hack.aiprojectmanager.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupContextRepository extends JpaRepository<GroupContext, Long> {}
