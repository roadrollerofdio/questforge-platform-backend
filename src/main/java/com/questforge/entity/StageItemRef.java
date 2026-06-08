package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("stage_item_ref")
public class StageItemRef implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long stageId;
    private Long itemId;
    private Integer itemType; // 1-学习材料, 2-考核试题
    private Integer scoreWeight;
    private Integer sortNum;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}