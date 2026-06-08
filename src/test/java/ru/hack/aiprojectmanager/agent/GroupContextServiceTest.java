package ru.hack.aiprojectmanager.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupContextServiceTest {

    @Mock GroupContextRepository groupContextRepository;
    @Mock MessageHistoryRepository historyRepository;
    @Mock ChatClient chatClient;
    @InjectMocks GroupContextService service;

    @Test
    void saveMessage_persistsToMessageHistory() {
        when(groupContextRepository.findById(-100L))
                .thenReturn(Optional.of(GroupContext.builder().chatId(-100L).totalMessages(0).build()));

        service.saveMessage(-100L, 1L, "Вася", "Привет");

        ArgumentCaptor<MessageHistory> captor = ArgumentCaptor.forClass(MessageHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Вася: Привет");
        assertThat(captor.getValue().getRole()).isEqualTo("user");
    }

    @Test
    void saveMessage_incrementsTotalMessages() {
        GroupContext ctx = GroupContext.builder().chatId(-100L).totalMessages(5).build();
        when(groupContextRepository.findById(-100L)).thenReturn(Optional.of(ctx));

        service.saveMessage(-100L, 1L, "Вася", "текст");

        ArgumentCaptor<GroupContext> captor = ArgumentCaptor.forClass(GroupContext.class);
        verify(groupContextRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalMessages()).isEqualTo(6);
    }

    @Test
    void saveMessage_triggersSummaryOnEvery15thMessage() {
        GroupContext ctx = GroupContext.builder().chatId(-100L).totalMessages(14).build();
        when(groupContextRepository.findById(-100L)).thenReturn(Optional.of(ctx));
        when(historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(-100L))
                .thenReturn(new java.util.ArrayList<>(List.of(history("Вася: сделал задачу"))));

        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(any(String.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Саммари встречи");

        service.saveMessage(-100L, 1L, "Вася", "15-е сообщение");

        verify(chatClient).prompt();
    }

    @Test
    void saveMessage_doesNotTriggerSummary_beforeEvery15thMessage() {
        GroupContext ctx = GroupContext.builder().chatId(-100L).totalMessages(7).build();
        when(groupContextRepository.findById(-100L)).thenReturn(Optional.of(ctx));

        service.saveMessage(-100L, 1L, "Вася", "текст");

        verify(chatClient, never()).prompt();
    }

    @Test
    void buildContextBlock_includesSummary_whenPresent() {
        GroupContext ctx = GroupContext.builder()
                .chatId(-100L).summary("Обсуждали релиз").build();
        when(groupContextRepository.findById(-100L)).thenReturn(Optional.of(ctx));
        when(historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(-100L)).thenReturn(List.of());

        String block = service.buildContextBlock(-100L);

        assertThat(block).contains("Обсуждали релиз");
    }

    @Test
    void buildContextBlock_limitsToRecentLines() {
        when(groupContextRepository.findById(-100L)).thenReturn(Optional.empty());
        List<MessageHistory> messages = new java.util.ArrayList<>(List.of(
                history("msg1"), history("msg2"), history("msg3"),
                history("msg4"), history("msg5"), history("msg6"), history("msg7")
        ));
        when(historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(-100L)).thenReturn(messages);

        String block = service.buildContextBlock(-100L);

        // Mock returns DESC (newest first): msg1=newest, msg7=oldest.
        // After reverse + subList: 5 newest (msg1-msg5) stay, 2 oldest (msg6,msg7) are cut.
        assertThat(block).doesNotContain("msg6").doesNotContain("msg7");
        assertThat(block).contains("msg1").contains("msg5");
    }

    private MessageHistory history(String content) {
        return MessageHistory.builder().chatId(-100L).role("user").content(content).build();
    }
}
