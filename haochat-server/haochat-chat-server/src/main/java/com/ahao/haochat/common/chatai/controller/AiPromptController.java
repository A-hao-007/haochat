package com.ahao.haochat.common.chatai.controller;

import com.ahao.haochat.common.chatai.domain.entity.AiPromptTemplate;
import com.ahao.haochat.common.chatai.service.AiPromptService;
import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.domain.enums.RoleEnum;
import com.ahao.haochat.common.user.service.IRoleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only prompt template management. Version activation is explicit and auditable. */
@RestController
@RequestMapping("/capi/ai/prompts")
@Api(tags = "AI Prompt 模板")
@RequiredArgsConstructor
public class AiPromptController {
    private final AiPromptService promptService;
    private final IRoleService roleService;

    @GetMapping
    @ApiOperation("查询 AI Prompt 模板")
    public ApiResult<List<AiPromptTemplate>> list(@RequestParam(required = false) String code) {
        assertAdmin();
        return ApiResult.success(promptService.list(code));
    }

    @PostMapping
    @ApiOperation("创建 AI Prompt 模板")
    public ApiResult<AiPromptTemplate> create(@RequestBody AiPromptTemplate template) {
        assertAdmin();
        template.setId(null);
        return ApiResult.success(promptService.save(template, RequestHolder.get().getUid()));
    }

    @PutMapping
    @ApiOperation("修改 AI Prompt 模板")
    public ApiResult<AiPromptTemplate> update(@RequestBody AiPromptTemplate template) {
        assertAdmin();
        AssertUtil.isNotEmpty(template.getId(), "模板ID不能为空");
        return ApiResult.success(promptService.save(template, RequestHolder.get().getUid()));
    }

    @PutMapping("/{id}/activate")
    @ApiOperation("启用指定版本的 AI Prompt 模板")
    public ApiResult<Void> activate(@PathVariable Long id) {
        assertAdmin();
        promptService.activate(id);
        return ApiResult.success();
    }

    private void assertAdmin() {
        Long uid = RequestHolder.get().getUid();
        AssertUtil.isTrue(roleService.hasPower(uid, RoleEnum.ADMIN), "没有权限");
    }
}
