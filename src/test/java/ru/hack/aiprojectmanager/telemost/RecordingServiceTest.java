package ru.hack.aiprojectmanager.telemost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import ru.hack.aiprojectmanager.notification.NotificationSender;
import ru.hack.aiprojectmanager.stt.SttProvider;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingServiceTest {

    @Mock SttProvider sttProvider;
    @Mock NotificationSender notificationSender;
    @Mock ChatClient chatClient;
    @InjectMocks RecordingService service;

    @TempDir Path tmpDir;

    @Test
    void transcribeAudio_smallFile_usesDirectTranscription() throws Exception {
        Path audio = tmpDir.resolve("output.ogg");
        Files.write(audio, new byte[]{1, 2, 3});
        when(sttProvider.transcribe(any(), any())).thenReturn("текст транскрипции");

        String result = service.transcribeAudio(audio);

        assertThat(result).isEqualTo("текст транскрипции");
        verify(sttProvider).transcribe(any(), eq("output.ogg"));
    }

    @Test
    void transcribeAudio_largeFile_fallsBackToDirectWhenFfmpegUnavailable() throws Exception {
        // 21 MB — выше порога 20 MB, ffmpeg в тестовой среде недоступен → fallback
        Path audio = tmpDir.resolve("output.ogg");
        Files.write(audio, new byte[21 * 1024 * 1024]);
        when(sttProvider.transcribe(any(), any())).thenReturn("транскрипция большого файла");

        String result = service.transcribeAudio(audio);

        assertThat(result).isEqualTo("транскрипция большого файла");
        verify(sttProvider).transcribe(any(), any());
    }
}
