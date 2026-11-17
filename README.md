# BÁO CÁO & HƯỚNG DẪN HOÀN THÀNH 5 BÀI LAB HỌC PHẦN LẬP TRÌNH MẠNG (VKU)

**Học phần:** Lập trình mạng (Network Programming)  
**Sinh viên thực hiện:** 
- Trần Quang Bình (Mã SV: 24IT027)
- Lê Nhuận Phát (Mã SV: 24IT197)

---

## 📌 BẢNG ĐỐI CHIẾU TIÊU CHÍ CHẤM ĐIỂM CỦA GIẢNG VIÊN

| STT | Tên Lab | Yêu cầu của đề tài | Cách chấm & Tiêu chuẩn hoàn thành | CSDL Tích hợp |
| :---: | :--- | :--- | :--- | :---: |
| **1** | **Chat (Client-Server / P2P)** | - Giao diện thân thiện (Zalo/Messenger)<br>- Multicast UDP (230.0.0.1) & Broadcast (9999)<br>- Icon cảm xúc, Text, Audio, Video, Đính kèm file<br>- Có Database | Chạy đồng thời 5 - 6 Client, vấn đáp code theo tình huống TCP - UDP | PostgreSQL (`chat_messages`) & MySQL |
| **2** | **FTP Server & Client** | - Chuẩn giao thức FTP RFC 959 (USER, PASS, LIST, RETR, STOR, PASV, PWD...)<br>- Giao diện 2 khung giống FileZilla | Server có CSDL quản lý User và nhật ký truyền file | PostgreSQL (`ftp_users`, `ftp_logs`) & MySQL |
| **3** | **Mail Server & Client** | - Giao thức POP3 (port 1110) & SMTP (port 2525 / port 465 SSL)<br>- Gửi được qua Gmail thật & Localhost | Gửi nhận thư thực tế, Server lưu dữ liệu vào Database | PostgreSQL (`mail_users`, `mail_emails`) & MySQL |
| **4** | **Web Server & Browser** | - Giao thức HTTP (RFC 2616)<br>- Server phục vụ đa dịch vụ (.pdf, .mp3, .mp4, .html...)<br>- Browser hỗ trợ GET, HEAD, POST, History, Download | Giống Chrome / Cốc Cốc, CSDL lưu lịch sử duyệt web | PostgreSQL (`browser_history`) & MySQL |
| **5** | **RMI Đồng bộ 2 Server** | - Phân tán dữ liệu RMI giữa 2 Server độc lập<br>- Giao diện Desktop JavaFX hiển thị IP LAN thời gian thực | Server chạy trên 2 máy thực, Client chạy máy ảo/LAN, tự bù dữ liệu khi ngắt kết nối mạng | PostgreSQL 18 (`students`, `sync_events`) |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN TỔNG HỢP

```
VKU_LapTrinhMang_5Labs/
├── Lab1_ChatApp/          # Ứng dụng Chat đa năng Client-Server & UDP Broadcast/Multicast
├── Lab2_FTPSystem/        # Hệ thống FTP Server & Client 2 khung FileZilla
├── Lab3_MailSystem/       # Mail Server LAN, SMTP Client (Gmail & Localhost), POP3 Client
├── Lab4_WebBrowser/       # Web Server HTTP đa phương tiện (.pdf/.mp3/.mp4) & Trình duyệt Web
├── Lab5_RMISync/          # Hệ thống đồng bộ CSDL RMI 2 Server phân tán (JavaFX + PostgreSQL)
├── database/              # File script khởi tạo CSDL
│   ├── init_postgres.sql  # Script tạo bảng và dữ liệu mẫu cho PostgreSQL
│   └── init_mysql.sql     # Script tạo bảng và dữ liệu mẫu cho MySQL XAMPP
├── lib/                   # Driver JDBC kết nối CSDL (PostgreSQL & MySQL)
└── README.md
```

---

## 🚀 HƯỚNG DẪN CHẠY DEMO TỪNG BÀI LAB

### 1. Khởi tạo Cơ sở dữ liệu (PostgreSQL / MySQL)
- **Với PostgreSQL:** Mở PostgreSQL chạy file `database/init_postgres.sql`.
- **Với MySQL XAMPP:** Mở phpMyAdmin bấm **Nhập (Import)** file `database/init_mysql.sql`.

---

### 2. Lab 1: Chat App (Client - Server)
```bash
cd Lab1_ChatApp
# Khởi động Server:
java -cp ".;../lib/*" ChatServerApp

# Mở đồng thời nhiều Client:
java -cp ".;../lib/*" ChatClientApp
```

---

### 3. Lab 2: FTP System (FileZilla)
```bash
cd Lab2_FTPSystem
# Khởi động FTP Server (Cổng 2121):
java -cp ".;../lib/*" FTPServerApp

# Khởi động FTP Client (2 khung):
java -cp ".;../lib/*" FTPClientApp
```

---

### 4. Lab 3: Mail System (SMTP & POP3)
```bash
cd Lab3_MailSystem
# Khởi động LAN Mail Server (SMTP: 2525, POP3: 1110):
java -cp ".;../lib/*" LANMailServer

# Khởi động SMTP Client (Soạn & Gửi thư):
java -cp ".;../lib/*" SMTPClient

# Khởi động POP3 Client (Xem hộp thư đến):
java -cp ".;../lib/*" POP3Client
```

---

### 5. Lab 4: Web Server & Simple Browser (HTTP)
```bash
cd Lab4_WebBrowser
# Khởi động Web Server (Cổng 8080):
java -cp ".;../lib/*" httpServer

# Khởi động Trình duyệt Browser:
java -cp ".;../lib/*" SimpleBrowser
```

---

### 6. Lab 5: RMI Database Replication (2 Server phân tán)
```bash
cd Lab5_RMISync
# Khởi động Server 1 (Máy 1 - Cổng 1099):
.\mvnw.ps1 javafx:run

# Khởi động Server 2 (Máy 2 - Cổng 1098):
.\scripts\run-server2.ps1
```
