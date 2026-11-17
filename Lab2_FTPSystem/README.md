# BÁO CÁO & HƯỚNG DẪN DEMO LAB 2: HỆ THỐNG FTP SERVER & CLIENT
**Học phần**: Lập trình mạng (VKU)  
**Tiêu chuẩn**: Chuẩn giao thức FTP RFC 959, Server có Cơ sở dữ liệu, Client giao diện FileZilla hai khung.

---

## 📌 1. CẤU TRÚC DỰ ÁN
```
Lab2_FTPSystem/
├── src/
│   ├── common/
│   │   └── FTPConstants.java        (Định nghĩa mã trạng thái chuẩn RFC 959: 150, 200, 220, 226, 227, 230, 331, 550...)
│   ├── server/
│   │   ├── FTPServerApp.java        (Giao diện máy chủ, hiển thị IP LAN, cổng kết nối, quản lý Client online)
│   │   ├── FTPClientHandler.java    (Xử lý đa luồng Socket TCP cho từng kết nối FTP của Client)
│   │   └── FTPDatabase.java         (Cơ sở dữ liệu quản lý tài khoản người dùng và nhật ký upload/download)
│   └── client/
│       ├── FTPClientApp.java        (Giao diện Client chuẩn FileZilla: Cục bộ bên trái, Máy chủ bên phải)
│       └── FTPClientEngine.java     (Xử lý Socket TCP gửi lệnh FTP, mở luồng truyền tệp nhị phân qua Passive Mode)
├── ftp_root/                        (Thư mục lưu trữ tệp tin trên máy chủ)
├── compile.bat                      (Lệnh biên dịch toàn bộ bằng javac chuẩn)
├── run_server.bat                   (Khởi động giao diện FTPServer)
├── run_client.bat                   (Khởi động giao diện FTPClient)
└── README.md
```

---

## 🚀 2. HƯỚNG DẪN DEMO TỪNG BƯỚC

### Bước 1: Khởi động Server
- Chạy lệnh hoặc double-click `run_server.bat` (hoặc mở Terminal: `java -cp bin server.FTPServerApp`).
- Giao diện Server hiển thị:
  - Địa chỉ IP LAN (ví dụ `192.168.1.x` hoặc `127.0.0.1`).
  - Cổng mặc định: `2121`.
  - Nút bấm trạng thái Server (đang chạy màu xanh).
  - Tab 1: Nhật ký lệnh Socket thời gian thực.
  - Tab 2: Danh sách máy Client đang kết nối.
  - Tab 3: Bảng Cơ sở dữ liệu (Tài khoản và Lịch sử truyền file).

### Bước 2: Khởi động Client
- Chạy lệnh hoặc double-click `run_client.bat` (hoặc mở Terminal: `java -cp bin client.FTPClientApp`).
- Nhập thông tin kết nối nhanh (Quick Connect):
  - **Host**: `127.0.0.1` (hoặc IP LAN của máy Server).
  - **Port**: `2121`.
  - **Username**: `admin` (hoặc `student`, `anonymous`).
  - **Password**: `admin123` (hoặc `vku123`).
- Bấm **"Kết nối nhanh"**.

### Bước 3: Thao tác kiểm thử tính năng
1. **Duyệt thư mục**: Double-click vào thư mục `TaiLieu` trên Remote Site để mở, bấm nút `⬆ Lên thư mục cha` để quay lại.
2. **Download tệp**: Chọn tệp bất kỳ bên Remote Site (ví dụ `chao_mung.txt`), bấm **"📥 Download về máy"**. Tệp được kéo về thư mục máy Client ngay lập tức.
3. **Upload tệp**: Chọn tệp bất kỳ bên Local Site, bấm **"📤 Upload lên Server ->"**. Tệp được truyền nhị phân lên Server.
4. **Kiểm tra Database Server**: Mở lại Server -> Tab **"Quản lý Cơ sở Dữ liệu"** -> Thấy ngay bản ghi Upload/Download vừa thực hiện với đầy đủ: ID, Thời gian, Người dùng, IP, Tên tệp, Kích thước, Trạng thái.

---

## ❓ 3. CÂU HỎI THƯỜNG GẶP KHI VẤN ĐÁP VỚI THẦY

**Câu 1: Tại sao FTP lại dùng 2 kênh kết nối (2 Port) khác nhau?**
- **Trả lời**: FTP tách biệt thành:
  - **Control Connection (Kênh điều khiển)**: Duy trì liên tục trong suốt phiên làm việc trên cổng 21 (hoặc 2121) để gửi nhận lệnh văn bản ASCII ngắn (USER, PASS, CWD, RETR...).
  - **Data Connection (Kênh dữ liệu)**: Chỉ mở tạm thời khi cần truyền tệp (STOR, RETR) hoặc danh sách thư mục (LIST), truyền xong sẽ đóng ngay. Giúp kênh điều khiển không bị nghẽn khi đang truyền tệp dung lượng lớn.

**Câu 2: Chế độ Passive Mode (PASV) hoạt động như thế nào và tại sao cần nó?**
- **Trả lời**: Trong chế độ Passive Mode:
  - Client gửi lệnh `PASV` tới Server qua kênh điều khiển.
  - Server tự động mở một cổng ngẫu nhiên (ephemeral port) và trả về địa chỉ IP + Port trong phản hồi `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`.
  - Client chủ động tạo kết nối TCP mới tới cổng dữ liệu đó của Server.
  - **Lợi ích**: Giúp vượt qua Firewall / NAT ở phía Client (nếu dùng Active Mode, Server sẽ phải kết nối ngược lại Client, điều này thường bị tường lửa Client chặn).
