package com.ahao.haochat.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Description: 新建群组
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-03-29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupAddReq {
    @NotNull
    @Size(min = 1, max = 50)
    @ApiModelProperty("邀请的uid")
    private List<Long> uidList;

    @Size(max = 30)
    @ApiModelProperty("群名称，不填则使用默认名称")
    private String name;
}
