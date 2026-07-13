package com.ahao.haochat.common.file.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.util.Date;
@Data @TableName("file_asset") public class FileAsset {
 @TableId(value="id",type=IdType.AUTO) private Long id; private Long uid; @TableField("room_id") private Long roomId;
 private String scene; @TableField("object_key") private String objectKey; @TableField("original_name") private String originalName;
 @TableField("content_type") private String contentType; private Long size; private Integer status; @TableField("thumbnail_key") private String thumbnailKey;
 @TableField("virus_status") private Integer virusStatus; @TableField("expire_time") private Date expireTime;
 @TableField("create_time") private Date createTime; @TableField("update_time") private Date updateTime;
}
