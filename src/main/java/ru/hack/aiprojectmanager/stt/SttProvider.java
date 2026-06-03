package ru.hack.aiprojectmanager.stt;

public interface SttProvider {
    String transcribe(byte[] audio, String fileName);
}
