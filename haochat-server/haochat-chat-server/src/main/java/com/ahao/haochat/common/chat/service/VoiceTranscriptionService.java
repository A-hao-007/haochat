package com.ahao.haochat.common.chat.service;

import com.ahao.haochat.common.common.exception.BusinessException;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.file.domain.FileAsset;
import com.ahao.haochat.common.file.domain.FileScene;
import com.ahao.haochat.common.file.service.FileAssetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceTranscriptionService {
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final FileAssetService fileAssetService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(30))
            .build();

    @Value("${haochat.voice-transcription.url}")
    private String transcriptionUrl;

    @Value("${haochat.voice-transcription.api-key:}")
    private String transcriptionApiKey;

    public Map<String, Object> transcribe(Long uid, Long assetId) {
        FileAsset asset = fileAssetService.readable(uid, assetId);
        AssertUtil.equal(FileScene.CHAT_FILE.code, asset.getScene(), "asset is not a chat attachment");
        AssertUtil.isTrue(asset.getSize() != null && asset.getSize() > 0 && asset.getSize() <= MAX_AUDIO_BYTES,
                "voice file is too large");
        String contentType = StringUtils.defaultIfBlank(asset.getContentType(), "application/octet-stream");
        String lowerName = StringUtils.defaultString(asset.getOriginalName()).toLowerCase(Locale.ROOT);
        boolean audioType = contentType.startsWith("audio/")
                || lowerName.matches(".*\\.(wav|webm|ogg|mp3|m4a|aac|flac)$");
        AssertUtil.isTrue(audioType, "asset is not an audio file");

        try (InputStream stream = fileAssetService.openObject(asset)) {
            byte[] bytes = stream.readAllBytes();
            MediaType mediaType = MediaType.parse(contentType);
            if (mediaType == null) mediaType = MediaType.parse("application/octet-stream");
            RequestBody body = RequestBody.create(bytes, mediaType);
            String suffix = lowerName.contains(".") ? lowerName.substring(lowerName.lastIndexOf('.')) : ".webm";
            Request.Builder builder = new Request.Builder()
                    .url(transcriptionUrl)
                    .post(body)
                    .header("X-Filename", "voice-" + assetId + suffix);
            if (StringUtils.isNotBlank(transcriptionApiKey)) {
                builder.header("Authorization", "Bearer " + transcriptionApiKey);
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                String payload = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    log.warn("Voice transcription upstream failed status={} assetId={}", response.code(), assetId);
                    throw new BusinessException("语音转写服务暂不可用，请稍后重试");
                }
                JsonNode root = objectMapper.readTree(payload);
                String text = root.path("text").asText("").trim();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("text", text);
                result.put("language", root.path("language").asText("zh"));
                result.put("duration", root.path("duration").asDouble(0));
                return result;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Voice transcription failed assetId={}", assetId, exception);
            throw new BusinessException("语音转写失败，请稍后重试");
        }
    }
}
