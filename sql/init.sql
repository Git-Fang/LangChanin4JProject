-- RAG Translation System Database Initialization
-- MySQL Database Initialization Script

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS rag_translation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE rag_translation;

-- 用户表（示例，根据实际需求调整）
CREATE TABLE IF NOT EXISTS `users` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 翻译记录表
CREATE TABLE IF NOT EXISTS `translation_records` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
    `source_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '原文',
    `translated_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '译文',
    `source_language` varchar(10) DEFAULT 'auto' COMMENT '源语言',
    `target_language` varchar(10) NOT NULL COMMENT '目标语言',
    `translation_model` varchar(50) DEFAULT NULL COMMENT '翻译模型',
    `use_rag` tinyint(1) DEFAULT '0' COMMENT '是否使用RAG',
    `rag_references` text COMMENT 'RAG参考文档',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译记录表';

-- 文档上传表
CREATE TABLE IF NOT EXISTS `documents` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
    `file_name` varchar(255) NOT NULL COMMENT '文件名',
    `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
    `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型',
    `storage_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
    `content_hash` varchar(64) DEFAULT NULL COMMENT '内容哈希',
    `status` varchar(20) DEFAULT 'pending' COMMENT '状态（pending, processing, completed, failed）',
    `vector_status` varchar(20) DEFAULT 'pending' COMMENT '向量化状态',
    `process_message` text COMMENT '处理消息',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_time` (`created_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档上传表';

-- 插入一些测试数据
INSERT INTO `users` (`username`, `email`) VALUES
    ('test_user', 'test@example.com'),
    ('admin', 'admin@example.com');

-- 查看创建的表
SHOW TABLES;

-- 查看用户表中的数据
SELECT * FROM `users`;