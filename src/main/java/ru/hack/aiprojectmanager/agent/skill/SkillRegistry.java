package ru.hack.aiprojectmanager.agent.skill;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SkillRegistry {

    private final Map<String, Skill> skills;

    public SkillRegistry(List<Skill> skills) {
        this.skills = skills.stream()
                .collect(Collectors.toMap(Skill::getName, Function.identity()));
    }

    public Optional<Skill> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<Skill> all() {
        return List.copyOf(skills.values());
    }
}
