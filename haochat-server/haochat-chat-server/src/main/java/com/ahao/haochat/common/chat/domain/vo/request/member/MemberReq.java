package com.ahao.haochat.common.chat.domain.vo.request.member;

import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-07-17
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberReq extends CursorPageBaseReq {
    @ApiModelProperty("房间号")
    private Long roomId = 1L;
}
