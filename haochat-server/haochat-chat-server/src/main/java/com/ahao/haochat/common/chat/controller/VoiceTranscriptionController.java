package com.ahao.haochat.common.chat.controller;

import com.ahao.haochat.common.chat.domain.vo.request.VoiceTranscriptionReq;
import com.ahao.haochat.common.chat.service.VoiceTranscriptionService;
import com.ahao.haochat.common.common.annotation.FrequencyControl;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.RequestHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/capi/chat")
@Api(tags = "语音消息")
@RequiredArgsConstructor
public class VoiceTranscriptionController {
    private final VoiceTranscriptionService transcriptionService;

    @PostMapping("/msg/voice/transcribe")
    @ApiOperation("将当前用户有权访问的语音消息转成文字")
    @FrequencyControl(time = 60, count = 6, target = FrequencyControl.Target.UID)
    public ApiResult<Map<String, Object>> transcribe(@Valid @RequestBody VoiceTranscriptionReq request) {
        return ApiResult.success(transcriptionService.transcribe(RequestHolder.get().getUid(), request.getAssetId()));
    }
}
