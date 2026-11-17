import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerApp extends JFrame {
    public static class ClientSession {
        public final String username;
        public final Socket socket;
        public final ObjectOutputStream out;
        public final String ip;
        public final String loginTime;

        public ClientSession(String username, Socket socket, ObjectOutputStream out, String ip, String loginTime) {
            this.username = username;
            this.socket = socket;
            this.out = out;
            this.ip = ip;
            this.loginTime = loginTime;
        }
    }

    private final ChatDatabase database = new ChatDatabase();
    private final Map<String, ClientSession> activeClients = new ConcurrentHashMap<>();
    private ServerSocket tcpServerSocket;
    private boolean isRunning = false;
    private final File filesDir = new File("chat_files").getAbsoluteFile();

    private JLabel lblStatus;
    private JLabel lblLanIp;
    private DefaultTableModel clientsModel;
    private DefaultTableModel historyModel;
    private JTextArea txtLogs;
    private JTextField txtBroadcastInput;

    public ChatServerApp() {
        super("VKU Chat Server (TCP 7777 + UDP 9999 Broadcast + Multicast 230.0.0.1)");
        if (!filesDir.exists()) filesDir.mkdirs();
        initComponents();
        startServer();
    }

    private void initComponents() {
        setSize(980, 680);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // 1. Top Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(230, 245, 255));

        JPanel ipPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        ipPanel.setOpaque(false);
        String ip = getLanIpAddress();
        lblLanIp = new JLabel("🌐 ĐỊA CHỈ IP MÁY CHỦ: " + ip);
        lblLanIp.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblLanIp.setForeground(new Color(0, 102, 204));

        JLabel lblPorts = new JLabel("TCP Port: " + ChatConstants.TCP_PORT + " | UDP Broadcast: " + ChatConstants.UDP_BROADCAST_PORT + " | UDP Multicast: " + ChatConstants.MULTICAST_IP + ":" + ChatConstants.MULTICAST_PORT);
        lblPorts.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPorts.setForeground(Color.DARK_GRAY);

        ipPanel.add(lblLanIp);
        ipPanel.add(lblPorts);
        topPanel.add(ipPanel, BorderLayout.WEST);

        lblStatus = new JLabel("● ĐANG HOẠT ĐỘNG", SwingConstants.RIGHT);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblStatus.setForeground(new Color(40, 167, 69));
        topPanel.add(lblStatus, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // 2. Center Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Tab 1: Clients online & Server Broadcast
        JPanel tabClients = new JPanel(new BorderLayout(8, 8));
        tabClients.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        clientsModel = new DefaultTableModel(new String[]{"Tên người dùng (Client)", "Địa chỉ IP", "Cổng kết nối", "Thời gian đăng nhập"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tableClients = new JTable(clientsModel);
        tableClients.setRowHeight(24);
        tabClients.add(new JScrollPane(tableClients), BorderLayout.CENTER);

        // Server Broadcast Bar
        JPanel broadcastBar = new JPanel(new BorderLayout(6, 6));
        broadcastBar.setBorder(BorderFactory.createTitledBorder("📢 Gửi Thông Báo Broadcast Toàn Mạng (UDP Socket Port 9999)"));
        txtBroadcastInput = new JTextField();
        JButton btnSendBroadcast = new JButton("Gửi Broadcast Ngay");
        btnSendBroadcast.setBackground(new Color(220, 53, 69));
        btnSendBroadcast.setForeground(Color.WHITE);
        btnSendBroadcast.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSendBroadcast.addActionListener(e -> sendUdpBroadcastFromGui());
        broadcastBar.add(txtBroadcastInput, BorderLayout.CENTER);
        broadcastBar.add(btnSendBroadcast, BorderLayout.EAST);
        tabClients.add(broadcastBar, BorderLayout.SOUTH);

        tabbedPane.addTab("👥 Danh sách Client Online", tabClients);

        // Tab 2: Database History
        JPanel tabHistory = new JPanel(new BorderLayout(5, 5));
        tabHistory.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        historyModel = new DefaultTableModel(new String[]{"ID", "Thời gian", "Người gửi", "Người nhận", "Loại tin nhắn", "Nội dung", "Tệp đính kèm", "Dung lượng"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tableHistory = new JTable(historyModel);
        tableHistory.setRowHeight(22);
        tabHistory.add(new JScrollPane(tableHistory), BorderLayout.CENTER);

        JPanel histBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRefreshHist = new JButton("Làm mới bảng");
        btnRefreshHist.addActionListener(e -> refreshHistoryTable());
        histBtns.add(btnRefreshHist);
        tabHistory.add(histBtns, BorderLayout.SOUTH);

        tabbedPane.addTab("🗄️ Cơ sở Dữ liệu Tin nhắn (Chat Database)", tabHistory);

        // Tab 3: Socket Logs
        JPanel tabLogs = new JPanel(new BorderLayout(5, 5));
        tabLogs.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(25, 25, 25));
        txtLogs.setForeground(new Color(220, 220, 220));
        tabLogs.add(new JScrollPane(txtLogs), BorderLayout.CENTER);
        tabbedPane.addTab("📜 Nhật ký Socket (TCP/UDP)", tabLogs);

        add(tabbedPane, BorderLayout.CENTER);
        refreshHistoryTable();
    }

    private void startServer() {
        isRunning = true;
        new Thread(this::listenTcp).start();
        log("[TCP SERVER] Đã mở cổng TCP " + ChatConstants.TCP_PORT + " sẵn sàng phục vụ 5-6 client kết nối đồng thời.");
    }

    private void listenTcp() {
        try {
            tcpServerSocket = new ServerSocket(ChatConstants.TCP_PORT);
            while (isRunning) {
                Socket clientSocket = tcpServerSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            if (isRunning) log("[LỖI TCP] " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();
        int clientPort = socket.getPort();
        String username = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;

        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Đợi thông điệp LOGIN
            ChatMessage firstMsg = (ChatMessage) in.readObject();
            if (firstMsg.getType() == ChatMessage.Type.LOGIN) {
                username = firstMsg.getSender().trim();
                if (username.isEmpty() || activeClients.containsKey(username)) {
                    out.writeObject(new ChatMessage(ChatMessage.Type.SYSTEM_NOTICE, "SERVER", username, "Tên tài khoản đã tồn tại hoặc không hợp lệ!"));
                    socket.close();
                    return;
                }

                String loginTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                ClientSession session = new ClientSession(username, socket, out, clientIp + ":" + clientPort, loginTime);
                activeClients.put(username, session);

                addClientToTable(username, clientIp, clientPort, loginTime);
                log("[TCP KẾT NỐI] Client '" + username + "' đã đăng nhập từ " + clientIp + ":" + clientPort);

                // Gửi danh sách online cập nhật cho toàn bộ Client
                broadcastUserList();

                // Gửi thông báo chào mừng cho mọi người
                broadcastTcpMessage(new ChatMessage(ChatMessage.Type.SYSTEM_NOTICE, "SERVER", "ALL", "👋 '" + username + "' vừa tham gia phòng chat."));
            }

            // Vòng lặp nhận tin nhắn từ Client
            while (isRunning) {
                ChatMessage msg = (ChatMessage) in.readObject();
                handleIncomingMessage(msg);
            }
        } catch (Exception e) {
            // Client ngắt kết nối
        } finally {
            if (username != null) {
                activeClients.remove(username);
                removeClientFromTable(username);
                log("[TCP NGẮT KẾT NỐI] Client '" + username + "' đã rời khỏi hệ thống.");
                broadcastUserList();
                broadcastTcpMessage(new ChatMessage(ChatMessage.Type.SYSTEM_NOTICE, "SERVER", "ALL", "🚪 '" + username + "' đã rời khỏi phòng chat."));
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleIncomingMessage(ChatMessage msg) {
        // Lưu vào CSDL
        database.saveMessage(msg);
        refreshHistoryTable();

        // Xử lý tệp đính kèm (Lưu trữ vào thư mục chat_files trên máy chủ)
        if (msg.getFileData() != null && msg.getFileName() != null) {
            try {
                File savedFile = new File(filesDir, System.currentTimeMillis() + "_" + msg.getFileName());
                java.nio.file.Files.write(savedFile.toPath(), msg.getFileData());
                log("[FILE TRUYỀN TẢI] Đã lưu tệp đính kèm từ " + msg.getSender() + " (" + msg.getFileSize() + " bytes) vào server.");
            } catch (IOException ignored) {}
        }

        // Chuyển tiếp tin nhắn
        if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
            broadcastTcpMessage(msg);
            log("[TCP PHÒNG CHUNG] [" + msg.getSender() + "]: " + (msg.getContent() != null ? msg.getContent() : msg.getFileName()));
        } else {
            // Tin nhắn riêng tư 1-1 (Private Message)
            sendPrivateTcpMessage(msg);
            log("[TCP RIÊNG TƯ] [" + msg.getSender() + " -> " + msg.getReceiver() + "]: " + (msg.getContent() != null ? msg.getContent() : msg.getFileName()));
        }
    }

    private synchronized void broadcastTcpMessage(ChatMessage msg) {
        for (ClientSession session : activeClients.values()) {
            try {
                session.out.writeObject(msg);
                session.out.flush();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private synchronized void sendPrivateTcpMessage(ChatMessage msg) {
        // Gửi tới người nhận
        ClientSession recipient = activeClients.get(msg.getReceiver());
        if (recipient != null) {
            try {
                recipient.out.writeObject(msg);
                recipient.out.flush();
            } catch (IOException ignored) {}
        }
        // Gửi lại một bản cho chính người gửi (để hiển thị lên khung chat của họ)
        ClientSession sender = activeClients.get(msg.getSender());
        if (sender != null && sender != recipient) {
            try {
                sender.out.writeObject(msg);
                sender.out.flush();
            } catch (IOException ignored) {}
        }
    }

    private synchronized void broadcastUserList() {
        List<String> userList = new ArrayList<>(activeClients.keySet());
        ChatMessage listMsg = new ChatMessage(ChatMessage.Type.USER_LIST, "SERVER", "ALL", "");
        listMsg.setOnlineUsers(userList);
        broadcastTcpMessage(listMsg);
    }

    private void sendUdpBroadcastFromGui() {
        String text = txtBroadcastInput.getText().trim();
        if (text.isEmpty()) return;

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                String payload = "SERVER_BROADCAST::" + text;
                byte[] buffer = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddr, ChatConstants.UDP_BROADCAST_PORT);
                socket.send(packet);

                // Đồng thời gửi qua TCP để các client cùng máy 127.0.0.1 nhận tức thì
                ChatMessage bMsg = new ChatMessage(ChatMessage.Type.BROADCAST_TEXT, "SERVER", "ALL", "📢 [THÔNG BÁO BROADCAST TOÀN MẠNG]: " + text);
                broadcastTcpMessage(bMsg);
                database.saveMessage(bMsg);
                refreshHistoryTable();

                log("[UDP BROADCAST GỬI ĐI] " + text);
                SwingUtilities.invokeLater(() -> txtBroadcastInput.setText(""));
            } catch (Exception e) {
                log("[LỖI UDP BROADCAST] " + e.getMessage());
            }
        }).start();
    }

    private void addClientToTable(String user, String ip, int port, String time) {
        SwingUtilities.invokeLater(() -> clientsModel.addRow(new Object[]{user, ip, String.valueOf(port), time}));
    }

    private void removeClientFromTable(String user) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientsModel.getRowCount(); i++) {
                if (clientsModel.getValueAt(i, 0).equals(user)) {
                    clientsModel.removeRow(i);
                    break;
                }
            }
        });
    }

    private void refreshHistoryTable() {
        SwingUtilities.invokeLater(() -> {
            historyModel.setRowCount(0);
            for (ChatDatabase.HistoryEntry h : database.getHistory()) {
                historyModel.addRow(new Object[]{h.id, h.time, h.sender, h.receiver, h.type, h.content, h.fileName, h.fileSize});
            }
        });
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLogs.append("[" + time + "] " + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    private String getLanIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            new ChatServerApp().setVisible(true);
        });
    }
}
