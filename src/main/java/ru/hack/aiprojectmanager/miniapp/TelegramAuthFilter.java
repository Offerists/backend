package ru.hack.aiprojectmanager.miniapp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(1)
public class TelegramAuthFilter extends OncePerRequestFilter {

    private static final String INIT_DATA_HEADER = "X-Telegram-Init-Data";
    public static final String USER_ID_ATTR = "telegramUserId";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.miniapp.dev-mode:false}")
    private boolean devMode;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (devMode) {
            String devUserId = request.getHeader("X-Dev-Telegram-User-Id");
            if (devUserId != null) {
                request.setAttribute(USER_ID_ATTR, Long.parseLong(devUserId));
                chain.doFilter(request, response);
                return;
            }
        }

        String initData = request.getHeader(INIT_DATA_HEADER);
        if (initData == null || initData.isBlank()) {
            writeError(response, 401, "Missing " + INIT_DATA_HEADER + " header");
            return;
        }

        try {
            Long userId = validateAndExtractUserId(initData);
            request.setAttribute(USER_ID_ATTR, userId);
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("Init data validation failed: {}", e.getMessage());
            writeError(response, 401, "Invalid or expired init data");
        }
    }

    private Long validateAndExtractUserId(String initData) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }

        String receivedHash = params.remove("hash");
        if (receivedHash == null) throw new IllegalArgumentException("No hash in initData");

        String authDateStr = params.get("auth_date");
        if (authDateStr != null) {
            long authDate = Long.parseLong(authDateStr);
            if (System.currentTimeMillis() / 1000 - authDate > 86_400) {
                throw new IllegalArgumentException("initData expired");
            }
        }

        String dataCheckString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        byte[] expectedHashBytes = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
        String expectedHash = HexFormat.of().formatHex(expectedHashBytes);

        if (!MessageDigest.isEqual(receivedHash.getBytes(StandardCharsets.UTF_8),
                                   expectedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Hash mismatch");
        }

        String userJson = params.get("user");
        if (userJson == null) throw new IllegalArgumentException("No user field in initData");
        JsonNode userNode = MAPPER.readTree(userJson);
        long userId = userNode.path("id").asLong(0);
        if (userId == 0) throw new IllegalArgumentException("No user.id in initData");
        return userId;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
