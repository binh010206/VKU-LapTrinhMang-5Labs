-- TẠO CSDL VÀ TOÀN BỘ CÁC BẢNG CHO 5 LAB TRÊN XAMPP MYSQL
CREATE DATABASE IF NOT EXISTS vku_network_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vku_network_db;

-- Lab 1: Chat App
CREATE TABLE IF NOT EXISTS chat_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    time VARCHAR(50),
    sender VARCHAR(50),
    receiver VARCHAR(50),
    type VARCHAR(30),
    content TEXT,
    file_name VARCHAR(255),
    file_size BIGINT DEFAULT 0
);

-- Lab 2: FTP System
CREATE TABLE IF NOT EXISTS ftp_users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    permission VARCHAR(20) DEFAULT 'READ_WRITE',
    home_dir VARCHAR(255) DEFAULT '/',
    created_at VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS ftp_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp VARCHAR(50),
    username VARCHAR(50),
    client_ip VARCHAR(50),
    action VARCHAR(20),
    target VARCHAR(255),
    file_size BIGINT DEFAULT 0,
    status VARCHAR(20)
);

INSERT IGNORE INTO ftp_users (username, password, permission, home_dir, created_at) VALUES
('admin', 'admin123', 'READ_WRITE', '/', NOW()),
('student', 'vku123', 'READ_WRITE', '/', NOW()),
('anonymous', 'guest', 'READ_ONLY', '/', NOW());

-- Lab 3: Mail System
CREATE TABLE IF NOT EXISTS mail_users (
    username VARCHAR(100) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    created_at VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS mail_emails (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(150) NOT NULL,
    receiver VARCHAR(150) NOT NULL,
    subject TEXT,
    body TEXT,
    timestamp VARCHAR(50),
    status VARCHAR(20)
);

INSERT IGNORE INTO mail_users (username, password, email, created_at) VALUES
('binh', '123456', 'binhtq.24it@vku.udn.vn', NOW()),
('phat', '123456', 'phatln.24it@vku.udn.vn', NOW()),
('thaygiao', '123456', 'thaygiao@vku.udn.vn', NOW());

-- Lab 4: Web Browser History
CREATE TABLE IF NOT EXISTS browser_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    time VARCHAR(50),
    method VARCHAR(10),
    url TEXT,
    post_data TEXT,
    status_code INT,
    content_length BIGINT DEFAULT 0
);

-- Lab 5: RMI Database Sync
CREATE TABLE IF NOT EXISTS student_records (
    id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100),
    email VARCHAR(100),
    major VARCHAR(100),
    gpa DOUBLE,
    origin_server VARCHAR(50),
    timestamp BIGINT
);

INSERT IGNORE INTO student_records (id, full_name, email, major, gpa, origin_server, timestamp) VALUES
('24IT001', 'Trần Quang Bình', 'binhtq.24it@vku.udn.vn', 'Công nghệ thông tin', 3.85, 'SERVER_1', UNIX_TIMESTAMP()),
('24IT002', 'Lê Nhuận Phát', 'phatln.24it@vku.udn.vn', 'Kỹ thuật phần mềm', 3.75, 'SERVER_2', UNIX_TIMESTAMP());
