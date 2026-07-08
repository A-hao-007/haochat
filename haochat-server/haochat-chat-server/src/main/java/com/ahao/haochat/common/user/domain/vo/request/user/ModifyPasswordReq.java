package com.ahao.haochat.common.user.domain.vo.request.user;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * Description: 修改密码
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModifyPasswordReq {

    @NotBlank(message = "请输入原密码")
    @ApiModelProperty("原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
            message = "新密码至少 8 位，且需同时包含大小写字母和数字"
    )
    @ApiModelProperty("新密码")
    private String newPassword;

}
