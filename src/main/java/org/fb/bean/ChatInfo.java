package org.fb.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 
 * @TableName appointment
 */
@TableName(value ="chatInfo")
@Data
public class ChatInfo {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String chatMemoryId;

    /**
     * 
     */
    private String chatInfo;

    /**
     * 
     */
    private String chatType;

    /**
     *
     */
    private String createTime;

}