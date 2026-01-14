package org.fb.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("fileOperation")
public class FileOperation {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String fileName;
    
    private String fileType;
    
    private String operationType;
    
    private LocalDateTime operationTime;
    
    private String status;

    public FileOperation() {
    }

    public FileOperation(String fileName, String fileType, String operationType, String status) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.operationType = operationType;
        this.operationTime = LocalDateTime.now();
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public LocalDateTime getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(LocalDateTime operationTime) {
        this.operationTime = operationTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
