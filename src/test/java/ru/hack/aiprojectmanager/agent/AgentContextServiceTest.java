package ru.hack.aiprojectmanager.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentContextServiceTest {

    @Mock AgentContextRepository repository;
    @InjectMocks AgentContextService service;

    @Test
    void rememberTask_createsNewContext_whenNoneExists() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.rememberTask(1L, "task-123", "Написать тесты");

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLastTaskId()).isEqualTo("task-123");
        assertThat(captor.getValue().getLastTaskTitle()).isEqualTo("Написать тесты");
    }

    @Test
    void rememberTask_preservesExistingAssignee_whenUpdatingTask() {
        AgentContext existing = AgentContext.builder()
                .telegramId(1L)
                .lastAssigneeId("user-99")
                .lastAssigneeName("Вася")
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.rememberTask(1L, "task-456", "Новая задача");

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(repository).save(captor.capture());
        AgentContext saved = captor.getValue();
        assertThat(saved.getLastTaskId()).isEqualTo("task-456");
        assertThat(saved.getLastAssigneeId()).isEqualTo("user-99");
    }

    @Test
    void rememberTask_ignoresCall_whenTaskIdIsNull() {
        service.rememberTask(1L, null, "title");

        verify(repository, never()).save(any());
    }

    @Test
    void rememberTask_ignoresCall_whenTelegramIdIsNull() {
        service.rememberTask(null, "task-1", "title");

        verify(repository, never()).save(any());
    }

    @Test
    void rememberAssignee_updatesAssigneeFields() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.rememberAssignee(1L, "user-42", "Петя");

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLastAssigneeId()).isEqualTo("user-42");
        assertThat(captor.getValue().getLastAssigneeName()).isEqualTo("Петя");
    }

    @Test
    void rememberAssignee_ignoresCall_whenYougileIdIsNull() {
        service.rememberAssignee(1L, null, "Петя");

        verify(repository, never()).save(any());
    }

    @Test
    void rememberStatus_updatesStatusAndTask() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.rememberStatus(1L, "task-77", "IN_PROGRESS");

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLastTaskId()).isEqualTo("task-77");
        assertThat(captor.getValue().getLastStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void get_delegatesToRepository() {
        AgentContext ctx = AgentContext.builder().telegramId(1L).build();
        when(repository.findById(1L)).thenReturn(Optional.of(ctx));

        Optional<AgentContext> result = service.get(1L);

        assertThat(result).contains(ctx);
    }
}
