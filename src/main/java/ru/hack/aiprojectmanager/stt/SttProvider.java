package ru.hack.aiprojectmanager.stt;

public interface SttProvider {

    String transcribe(byte[] audio, String fileName);

    // previousContext — последние ~200 символов предыдущего чанка, передаётся в Whisper как prompt
    default String transcribe(byte[] audio, String fileName, String previousContext) {
        return transcribe(audio, fileName);
    }
}
