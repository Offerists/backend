package ru.hack.aiprojectmanager.telemost;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.hack.aiprojectmanager.notification.NotificationSender;
import ru.hack.aiprojectmanager.stt.SttProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private static final long WHISPER_MAX_BYTES = 20L * 1024 * 1024; // 20MB
    private static final String AUDIO_FILE = "output.ogg";

    private final SttProvider sttProvider;
    private final NotificationSender notificationSender;
    private final ChatClient chatClient;

    @Value("${telemost.image-name:telemost-recorder}")
    private String imageName;

    @Value("${telemost.output-base-dir:/tmp/telemost}")
    private String outputBaseDir;

    public void startRecording(Long chatId, String url) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("Starting recording session={} chatId={} url={}", sessionId, chatId, url);
        notificationSender.send(chatId, "🎙 Подключаюсь к встрече, начинаю запись...");
        Thread.ofVirtual().start(() -> runRecording(chatId, sessionId, url));
    }

    private void runRecording(Long chatId, String sessionId, String url) {
        Path outputDir = Path.of(outputBaseDir, sessionId);
        try {
            Files.createDirectories(outputDir);
            runContainer(sessionId, url, outputDir);

            Path audio = outputDir.resolve(AUDIO_FILE);
            if (!Files.exists(audio) || Files.size(audio) == 0) {
                notificationSender.send(chatId, "⚠️ Запись завершена, но аудиофайл не найден.");
                return;
            }

            long size = Files.size(audio);
            if (size > WHISPER_MAX_BYTES) {
                long mb = size / (1024 * 1024);
                log.warn("Audio too large for Whisper: {}MB session={}", mb, sessionId);
                notificationSender.send(chatId,
                        "⚠️ Запись слишком длинная (" + mb + " МБ). Максимум ~2.5 часа для транскрипции.");
                return;
            }

            notificationSender.send(chatId, "🔄 Транскрибирую запись...");
            String transcription = sttProvider.transcribe(Files.readAllBytes(audio), AUDIO_FILE);

            if (transcription == null || transcription.isBlank()) {
                notificationSender.send(chatId, "⚠️ Не удалось распознать речь в записи.");
                return;
            }

            String summary = summarize(transcription);
            notificationSender.send(chatId, "📋 Саммари встречи:\n\n" + summary);

        } catch (Exception e) {
            log.error("Recording failed session={}", sessionId, e);
            notificationSender.send(chatId, "❌ Ошибка при записи: " + e.getMessage());
        } finally {
            cleanup(outputDir);
        }
    }

    private void runContainer(String sessionId, String url, Path outputDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--cpus=0.5",
                "--memory=384m",
                "--shm-size=128m",
                "-v", outputDir.toAbsolutePath() + ":/app/output",
                "--name", "telemost-" + sessionId,
                imageName,
                "node", "recorder.js", url, AUDIO_FILE
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (var reader = process.inputReader()) {
            reader.lines().forEach(line -> log.debug("[telemost-{}] {}", sessionId, line));
        }

        int exitCode = process.waitFor();
        log.info("Container session={} exited with code={}", sessionId, exitCode);
    }

    private String summarize(String transcription) {
        try {
            return chatClient.prompt()
                    .system("""
                            Ты — ассистент для суммаризации рабочих встреч.
                            Создай краткое резюме на русском языке.
                            Структура: ключевые темы, принятые решения, договорённости и ответственные (если упоминались).
                            Пиши лаконично, без воды.
                            """)
                    .user("Транскрипция встречи:\n\n" + transcription)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Summarization failed, returning truncated transcription", e);
            return transcription.length() > 2000
                    ? transcription.substring(0, 2000) + "...\n[обрезано]"
                    : transcription;
        }
    }

    private void cleanup(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            log.warn("Cleanup failed for {}: {}", dir, e.getMessage());
        }
    }
}
