# BÁO CÁO & HƯỚNG DẪN DEMO LAB 1: CHAT ĐA NĂNG (CLIENT - SERVER & P2P)
**Học phần**: Lập trình mạng (VKU)  
**Tiêu chuẩn**: Giao diện Zalo/Messenger, Multicast (UDP 230.0.0.1:8888), Broadcast (UDP 9999), TCP Socket (7777), Icon biểu cảm, gửi Text/Audio/Video/Tệp đính kèm, CSDL tin nhắn, chạy đồng thời 5 đến 6 Client.

---

## 📌 1. CẤU TRÚC MÃ NGUỒN (KHÔNG DÙNG FILE BAT ĐÓNG GÓI)
```
Lab1_ChatApp/
├── src/
│   ├── common/
│   │   ├── ChatConstants.java    (Cổng TCP 7777, Cổng UDP Broadcast 9999, Nhóm Multicast 230.0.0.1:8888)
│   │   └── ChatMessage.java      (Mô hình tin nhắn đa năng: Text, Emoji, File, Audio, Video, Multicast, Broadcast)
│   ├── server/
│   │   ├── ChatServerApp.java    (Giao diện máy chủ, quản lý đa luồng kết nối TCP, gửi UDP Broadcast)
│   │   └── ChatDatabase.java     (CSDL lưu trữ toàn bộ lịch sử trò chuyện và thông tin file vào CSV)
│   └── client/
│       └── ChatClientApp.java    (Giao diện Client Zalo: bong bóng chat, bảng icon emoji, nút gửi file/audio/video, nhận UDP multicast)
├── chat_files/                   (Thư mục lưu trữ tệp tin/audio/video được truyền tải trên hệ thống)
└── README.md
```

---

## 🚀 2. LỆNH CHẠY TRỰC TIẾP (TERMINAL HOẶC IDE)

### Biên dịch (chỉ cần chạy 1 lần hoặc mở bằng IDE VS Code/IntelliJ):
```bash
cd "D:\Lập trình mạng\Lab1_ChatApp"
javac -encoding UTF-8 -d bin src/common/*.java src/server/*.java src/client/*.java
```

### Bước 1: Khởi động Server
```bash
java -cp bin server.ChatServerApp
```

### Bước 2: Khởi động 5 đến 6 Client để demo đồng thời
Mở các cửa sổ Terminal mới (hoặc mở trực tiếp từ IDE):
```bash
# Client 1:
java -cp bin client.ChatClientApp
(Nhập tên: Binh)

# Client 2:
java -cp bin client.ChatClientApp
(Nhập tên: Phat)

# Client 3:
java -cp bin client.ChatClientApp
(Nhập tên: Hoang)

# Client 4:
java -cp bin client.ChatClientApp
(Nhập tên: Trang)

# Client 5:
java -cp bin client.ChatClientApp
(Nhập tên: Linh)
```

---

## 🎯 3. KỊCH BẢN DEMO CÁC TÍNH NĂNG
1. **Phòng chung (TCP Server)**: Gõ tin nhắn và chèn emoji (`😀`, `🔥`, `🎉`...) -> Tất cả các Client lập tức nhận được theo thời gian thực.
2. **Chat riêng tư 1-1 (Private TCP)**: Nhìn danh sách "🟢 Bạn bè Online", bấm vào tên một bạn (ví dụ `Phat`) -> Nhắn tin riêng chỉ 2 người thấy.
3. **Kênh Đa Hướng (UDP Multicast 230.0.0.1:8888)**: Bấm vào kênh `👥 Nhóm Đa Hướng (UDP Multicast)` -> Nhắn tin trao đổi nhóm qua địa chỉ mạng lớp D IP Multicast.
4. **Gửi File / Audio / Video**: Bấm nút `📎 Đính kèm tệp` hoặc `🎵 Gửi Audio` hoặc `🎥 Gửi Video` -> Chọn file -> File được chuyển nhị phân qua TCP Socket nguyên vẹn sang các máy khác, các máy nhận có thể bấm `💾 Tải tệp nhận gần nhất` để lưu về máy.
5. **Gửi Thông báo Broadcast toàn mạng**: Trên cửa sổ **ChatServerApp**, nhập nội dung thông báo vào ô đỏ phía dưới -> Bấm **"Gửi Broadcast Ngay"** -> Gói tin UDP Broadcast được phát ra toàn mạng và hiển thị lên toàn bộ các Client.
6. **Kiểm tra Cơ sở Dữ liệu**: Trên Server, bấm sang tab **"🗄️ Cơ sở Dữ liệu Tin nhắn"** -> Xem toàn bộ các tin nhắn, loại tin nhắn, thời gian, tên tệp đã được lưu trữ bền vững.

---

## ❓ 4. CÂU HỎI VẤN ĐÁP CỦA THẦY VỀ TÌNH HUỐNG (TCP vs UDP)

**Câu hỏi: Trong ứng dụng này, tình huống nào dùng TCP và tình huống nào dùng UDP? Tại sao?**
- **Trả lời**:
  1. **Tình huống dùng TCP (Cổng 7777)**:
     - Dùng cho: Đăng nhập/xác thực, cập nhật danh sách bạn bè online, tin nhắn riêng tư 1-1 và truyền tệp tin (File / Audio / Video).
     - Lý do: TCP là giao thức hướng kết nối (Connection-oriented), có cơ chế bắt tay 3 bước (Three-way handshake), kiểm soát luồng, kiểm soát tắc nghẽn và đảm bảo tin cậy 100% không mất gói (Reliable, No packet loss). File và tin nhắn văn bản nếu mất gói sẽ bị hỏng file hoặc mất nội dung nên bắt buộc phải dùng TCP.
  2. **Tình huống dùng UDP Multicast (230.0.0.1:8888) & UDP Broadcast (9999)**:
     - Dùng cho: Nhóm trò chuyện thời gian thực nhanh không cần kết nối cố định, thông báo khẩn cấp từ máy chủ tới toàn mạng LAN.
     - Lý do: UDP là giao thức phi kết nối (Connectionless), không tốn chi phí bắt tay, header nhẹ (chỉ 8 bytes so với 20+ bytes của TCP), cho phép phát tin tới nhiều máy cùng một lúc (One-to-Many / Many-to-Many) với độ trễ cực thấp.
