-- MySQL Database Initialization Script for RAGTranslation Project
-- Database: mydocker

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS mydocker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mydocker;

-- Create chatInfo table
CREATE TABLE IF NOT EXISTS chatInfo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_memory_id VARCHAR(255) DEFAULT NULL,
    chat_info TEXT DEFAULT NULL,
    chat_type VARCHAR(50) DEFAULT NULL,
    create_time VARCHAR(50) DEFAULT NULL,
    INDEX idx_chat_memory_id (chat_memory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create appointment table
CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) DEFAULT NULL,
    id_card VARCHAR(50) DEFAULT NULL,
    department VARCHAR(100) DEFAULT NULL,
    date VARCHAR(50) DEFAULT NULL,
    time VARCHAR(50) DEFAULT NULL,
    doctor_name VARCHAR(100) DEFAULT NULL,
    INDEX idx_id_card (id_card),
    INDEX idx_username (username),
    INDEX idx_doctor_name (doctor_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create fileOperation table
CREATE TABLE IF NOT EXISTS fileOperation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) DEFAULT NULL,
    file_type VARCHAR(50) DEFAULT NULL,
    operation_type VARCHAR(50) DEFAULT NULL,
    operation_time DATETIME DEFAULT NULL,
    status VARCHAR(50) DEFAULT NULL,
    finished_time DATETIME DEFAULT NULL,
    INDEX idx_status (status),
    INDEX idx_operation_type (operation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
