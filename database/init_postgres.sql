-- Bảng cho Lab 3: Mail System
CREATE TABLE IF NOT EXISTS mail_users (
    username VARCHAR(100) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    created_at VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS mail_emails (
    id SERIAL PRIMARY KEY,
    sender VARCHAR(150) NOT NULL,
    receiver VARCHAR(150) NOT NULL,
    subject TEXT,
    body TEXT,
    timestamp VARCHAR(50),
    status VARCHAR(20)
);

-- Khởi tạo user mặc định cho Mail
INSERT INTO mail_users (username, password, email, created_at)
VALUES 
    ('binh', '123456', 'binhtq.24it@vku.udn.vn', NOW()::text),
    ('phat', '123456', 'phatln.24it@vku.udn.vn', NOW()::text),
    ('thaygiao', '123456', 'thaygiao@vku.udn.vn', NOW()::text)
ON CONFLICT (username) DO NOTHING;

-- Bảng cho Lab 2: FTP System
CREATE TABLE IF NOT EXISTS ftp_users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    permission VARCHAR(20) DEFAULT 'READ_WRITE',
    home_dir VARCHAR(255) DEFAULT '/',
    created_at VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS ftp_logs (
    id SERIAL PRIMARY KEY,
    timestamp VARCHAR(50),
    username VARCHAR(50),
    client_ip VARCHAR(50),
    action VARCHAR(20),
    target VARCHAR(255),
    file_size BIGINT DEFAULT 0,
    status VARCHAR(20)
);

INSERT INTO ftp_users (username, password, permission, home_dir, created_at)
VALUES 
    ('admin', 'admin123', 'READ_WRITE', '/', NOW()::text),
    ('student', 'vku123', 'READ_WRITE', '/', NOW()::text),
    ('anonymous', 'guest', 'READ_ONLY', '/', NOW()::text)
ON CONFLICT (username) DO NOTHING;

-- Bảng cho Lab 1: Chat App
CREATE TABLE IF NOT EXISTS chat_messages (
    id SERIAL PRIMARY KEY,
    time VARCHAR(50),
    sender VARCHAR(50),
    receiver VARCHAR(50),
    type VARCHAR(30),
    content TEXT,
    file_name VARCHAR(255),
    file_size BIGINT DEFAULT 0
);