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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private static final long WHISPER_MAX_BYTES = 20L * 1024 * 1024; // 20 MB (~1h25m при 32kbps)
    private static final int CHUNK_DURATION_SECS = 70 * 60;          // 70 минут на чанк
    private static final int CONTEXT_CHARS = 200;
    private static final String AUDIO_FILE = "output.ogg";
    private static final String DEBUG_AUDIO_PATH = "/tmp/telemost/last_recording.ogg";

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
        Path audio = outputDir.resolve(AUDIO_FILE);
        try {
            Files.createDirectories(outputDir);
            runContainer(sessionId, url, outputDir);

            if (!Files.exists(audio) || Files.size(audio) == 0) {
                dumpRecorderLog(sessionId, outputDir);
                preserveDiagnostics(sessionId, outputDir);
                notificationSender.send(chatId, "⚠️ Запись завершена, но аудиофайл не найден.");
                return;
            }

            saveDebugCopy(audio);

            notificationSender.send(chatId, "🔄 Транскрибирую запись...");
            String transcription = transcribeAudio(audio);

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

    String transcribeAudio(Path audioFile) throws Exception {
        long size = Files.size(audioFile);
        if (size <= WHISPER_MAX_BYTES) {
            return sttProvider.transcribe(Files.readAllBytes(audioFile), AUDIO_FILE);
        }
        log.info("Audio too large ({}MB), splitting into chunks", size / 1024 / 1024);
        return transcribeInChunks(audioFile);
    }

    private String transcribeInChunks(Path audioFile) throws Exception {
        Path chunksDir = audioFile.getParent().resolve("chunks");
        Files.createDirectories(chunksDir);
        try {
            try {
                splitAudio(audioFile, chunksDir);
            } catch (Exception e) {
                log.warn("FFmpeg split failed ({}), falling back to direct transcription", e.getMessage());
                return sttProvider.transcribe(Files.readAllBytes(audioFile), AUDIO_FILE);
            }

            List<Path> chunks = Files.list(chunksDir)
                    .filter(p -> p.getFileName().toString().startsWith("chunk_"))
                    .sorted()
                    .toList();

            if (chunks.isEmpty()) {
                log.warn("FFmpeg split produced no chunks, falling back to direct transcription");
                return sttProvider.transcribe(Files.readAllBytes(audioFile), AUDIO_FILE);
            }

            log.info("Transcribing {} chunks", chunks.size());
            StringBuilder full = new StringBuilder();
            String context = null;

            for (Path chunk : chunks) {
                String part = sttProvider.transcribe(
                        Files.readAllBytes(chunk),
                        chunk.getFileName().toString(),
                        context
                );
                if (!full.isEmpty()) full.append(' ');
                full.append(part);
                context = part.length() > CONTEXT_CHARS
                        ? part.substring(part.length() - CONTEXT_CHARS)
                        : part;
            }

            return full.toString();
        } finally {
            cleanup(chunksDir);
        }
    }

    private void splitAudio(Path audioFile, Path chunksDir) throws IOException, InterruptedException {
        new ProcessBuilder(
                "ffmpeg", "-i", audioFile.toString(),
                "-f", "segment",
                "-segment_time", String.valueOf(CHUNK_DURATION_SECS),
                "-reset_timestamps", "1",
                "-c", "copy",
                chunksDir.resolve("chunk_%03d.ogg").toString()
        ).redirectErrorStream(true).start().waitFor();
    }

    private void runContainer(String sessionId, String url, Path outputDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--cpus=1.5",
                // Полноценный (не headless) Chromium + node + ffmpeg + pulseaudio +
                // Xvfb легко съедают >1 ГБ. На 768m рендерер падал по OOM ещё на
                // этапе подключения к звонку.
                "--memory=2g",
                "--shm-size=512m",
                "-p", "5900:5900",
                "-v", outputDir.toAbsolutePath() + ":/app/output",
                "--name", "telemost-" + sessionId,
                imageName,
                "node", "recorder.js", url, AUDIO_FILE
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Стримим логи контейнера на INFO уровне в отдельном потоке
        Thread logThread = Thread.ofVirtual().start(() -> {
            try (var reader = process.inputReader()) {
                reader.lines().forEach(line -> log.info("[telemost-{}] {}", sessionId, line));
            } catch (Exception ignored) {}
        });

        int exitCode = process.waitFor();
        logThread.join(5000); // ждём дочитки логов до 5 сек
        log.info("Container session={} exited with code={}", sessionId, exitCode);

        if (exitCode != 0) {
            log.warn("Container session={} exited non-zero ({}), output.ogg may be missing", sessionId, exitCode);
        }
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

    private void saveDebugCopy(Path audio) {
        try {
            if (!Files.exists(audio)) return;
            Path dest = Path.of(DEBUG_AUDIO_PATH);
            Files.copy(audio, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Debug copy saved to {}", DEBUG_AUDIO_PATH);
        } catch (Exception e) {
            log.warn("Failed to save debug copy: {}", e.getMessage());
        }
    }

    /**
     * Когда запись не удалась, переносим логи и скриншоты контейнера в постоянную
     * папку, чтобы их не стёр {@link #cleanup}. Без этого диагностировать, почему
     * бот не подключился к встрече, невозможно.
     */
    private void preserveDiagnostics(String sessionId, Path outputDir) {
        try {
            Path debugDir = Path.of(outputBaseDir, "debug", sessionId);
            Files.createDirectories(debugDir);
            try (var stream = Files.list(outputDir)) {
                stream.filter(p -> {
                            String name = p.getFileName().toString();
                            return name.endsWith(".png") || name.endsWith(".log");
                        })
                        .forEach(p -> {
                            try {
                                Files.copy(p, debugDir.resolve(p.getFileName()),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                log.warn("Failed to preserve {}: {}", p, e.getMessage());
                            }
                        });
            }
            log.warn("[telemost-{}] Диагностика (логи + скриншоты) сохранена в {}", sessionId, debugDir);
        } catch (Exception e) {
            log.warn("[telemost-{}] Не удалось сохранить диагностику: {}", sessionId, e.getMessage());
        }
    }

    private void dumpRecorderLog(String sessionId, Path outputDir) {
        Path logFile = outputDir.resolve("recorder.log");
        if (!Files.exists(logFile)) {
            log.warn("[telemost-{}] recorder.log не найден — логи контейнера недоступны", sessionId);
            return;
        }
        try {
            String content = Files.readString(logFile);
            // Выводим последние 100 строк чтобы не флудить
            String[] lines = content.split("\n");
            int from = Math.max(0, lines.length - 100);
            log.warn("[telemost-{}] === recorder.log (последние {} строк) ===", sessionId, lines.length - from);
            for (int i = from; i < lines.length; i++) {
                log.warn("[telemost-{}] {}", sessionId, lines[i]);
            }
        } catch (Exception e) {
            log.warn("[telemost-{}] Не удалось прочитать recorder.log: {}", sessionId, e.getMessage());
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
