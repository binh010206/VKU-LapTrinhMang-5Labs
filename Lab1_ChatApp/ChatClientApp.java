import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class ChatClientApp extends JFrame {
    private String myUsername;
    private String serverHost;
    private int serverPort;

    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private DatagramSocket udpBroadcastListener;

    private boolean isConnected = false;
    private String currentTarget = "ALL"; // "ALL", "MULTICAST", or username

    // UI Components
    private JLabel lblCurrentTarget;
    private JLabel lblProtocolInfo;
    private JTextPane chatPane;
    private JTextField txtInput;
    private DefaultListModel<String> channelsModel;
    private JList<String> listChannels;
    private DefaultListModel<String> usersModel;
    private JList<String> listUsers;

    // Local message buffers for tabs: key = target ("ALL", "MULTICAST", "user")
    private final Map<String, StringBuilder> chatHistories = new HashMap<>();
    private final Map<String, byte[]> receivedFiles = new HashMap<>(); // fileName -> data

    public ChatClientApp() {
        super("VKU Chat Client - Zalo / Messenger Style");
        if (showLoginDialog()) {
            initComponents();
            initNetworking();
        } else {
            System.exit(0);
        }
    }

    private boolean showLoginDialog() {
        JTextField txtHost = new JTextField("127.0.0.1");
        JTextField txtPort = new JTextField(String.valueOf(ChatConstants.TCP_PORT));
        String defaultName = "SinhVien_" + (new Random().nextInt(900) + 100);
        JTextField txtName = new JTextField(defaultName);

        Object[] fields = {
                "Địa chỉ Server Host:", txtHost,
                "Cổng TCP:", txtPort,
                "Tên hiển thị (Nickname):", txtName
        };

        int option = JOptionPane.showConfirmDialog(null, fields, "Đăng nhập VKU Chat", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            this.serverHost = txtHost.getText().trim();
            try {
                this.serverPort = Integer.parseInt(txtPort.getText().trim());
            } catch (Exception e) {
                this.serverPort = ChatConstants.TCP_PORT;
            }
            this.myUsername = txtName.getText().trim();
            if (this.myUsername.isEmpty()) this.myUsername = defaultName;
            return true;
        }
        return false;
    }

    private void initComponents() {
        setTitle("VKU Chat - [" + myUsername + "] (TCP & UDP Multicast/Broadcast)");
        setSize(980, 700);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Left Sidebar: Channels & Users
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(280, 700));
        leftPanel.setBackground(new Color(245, 247, 250));
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(220, 225, 230)));

        // Profile banner
        JPanel profilePanel = new JPanel(new BorderLayout(8, 8));
        profilePanel.setBorder(new EmptyBorder(15, 12, 15, 12));
        profilePanel.setBackground(new Color(0, 104, 255)); // Zalo blue
        JLabel lblMyName = new JLabel("👤 " + myUsername);
        lblMyName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblMyName.setForeground(Color.WHITE);
        JLabel lblMyStatus = new JLabel("● Đang trực tuyến (Online)");
        lblMyStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMyStatus.setForeground(new Color(210, 235, 255));
        profilePanel.add(lblMyName, BorderLayout.NORTH);
        profilePanel.add(lblMyStatus, BorderLayout.SOUTH);
        leftPanel.add(profilePanel, BorderLayout.NORTH);

        // Channel list + User list
        JPanel listsPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        listsPanel.setOpaque(false);

        // Channels
        JPanel chPanel = new JPanel(new BorderLayout());
        chPanel.setOpaque(false);
        chPanel.setBorder(BorderFactory.createTitledBorder("📢 Kênh truyền thông"));
        channelsModel = new DefaultListModel<>();
        channelsModel.addElement("🌐 Phòng Chung (TCP Server)");
        channelsModel.addElement("👥 Nhóm Đa Hướng (UDP Multicast)");
        listChannels = new JList<>(channelsModel);
        listChannels.setFont(new Font("Segoe UI", Font.BOLD, 13));
        listChannels.setSelectedIndex(0);
        listChannels.setFixedCellHeight(35);
        listChannels.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                listUsers.clearSelection();
                int idx = listChannels.getSelectedIndex();
                if (idx == 0) switchChatTarget("ALL", "🌐 Phòng Chung (Giao thức TCP Server tin cậy)");
                else if (idx == 1) switchChatTarget("MULTICAST", "👥 Nhóm Đa Hướng (Giao thức UDP Multicast 230.0.0.1:8888)");
            }
        });
        chPanel.add(new JScrollPane(listChannels), BorderLayout.CENTER);
        listsPanel.add(chPanel);

        // Online Users
        JPanel usPanel = new JPanel(new BorderLayout());
        usPanel.setOpaque(false);
        usPanel.setBorder(BorderFactory.createTitledBorder("🟢 Bạn bè Online (Chat riêng 1-1 TCP)"));
        usersModel = new DefaultListModel<>();
        listUsers = new JList<>(usersModel);
        listUsers.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        listUsers.setFixedCellHeight(30);
        listUsers.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String selected = listUsers.getSelectedValue();
                if (selected != null) {
                    listChannels.clearSelection();
                    switchChatTarget(selected, "💬 Chat riêng tư với " + selected + " (Giao thức TCP)");
                }
            }
        });
        usPanel.add(new JScrollPane(listUsers), BorderLayout.CENTER);
        listsPanel.add(usPanel);

        leftPanel.add(listsPanel, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        // 2. Right Main Chat Area
        JPanel mainChatPanel = new JPanel(new BorderLayout(5, 5));
        mainChatPanel.setBackground(Color.WHITE);

        // Chat Header
        JPanel chatHeader = new JPanel(new BorderLayout(5, 2));
        chatHeader.setBorder(new EmptyBorder(12, 15, 12, 15));
        chatHeader.setBackground(new Color(250, 252, 255));
        chatHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 230, 235)));

        lblCurrentTarget = new JLabel("🌐 Phòng Chung");
        lblCurrentTarget.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblProtocolInfo = new JLabel("Giao thức: TCP Socket (Cổng " + serverPort + ")");
        lblProtocolInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblProtocolInfo.setForeground(new Color(0, 102, 204));
        chatHeader.add(lblCurrentTarget, BorderLayout.NORTH);
        chatHeader.add(lblProtocolInfo, BorderLayout.SOUTH);
        mainChatPanel.add(chatHeader, BorderLayout.NORTH);

        // Chat Message View
        chatPane = new JTextPane();
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(248, 250, 252));
        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(null);
        mainChatPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Controls: Emoji toolbar + Text input + Attachment buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(new EmptyBorder(8, 12, 12, 12));
        bottomPanel.setBackground(Color.WHITE);

        // Emoji & Tool Bar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolBar.setOpaque(false);

        String[] emojis = {"😀", "😂", "😍", "👍", "🎉", "🔥", "❤️", "👏", "🚀", "💡", "🥳", "💯"};
        for (String emo : emojis) {
            JButton btnEmo = new JButton(emo);
            btnEmo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            btnEmo.setMargin(new Insets(2, 4, 2, 4));
            btnEmo.setFocusPainted(false);
            btnEmo.setContentAreaFilled(false);
            btnEmo.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            btnEmo.addActionListener(e -> txtInput.setText(txtInput.getText() + emo));
            toolBar.add(btnEmo);
        }

        toolBar.add(Box.createHorizontalStrut(10));
        JButton btnAttach = new JButton("📎 Đính kèm tệp");
        btnAttach.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnAttach.addActionListener(e -> chooseAndSendFile(ChatMessage.Type.FILE_ATTACHMENT));
        toolBar.add(btnAttach);

        JButton btnAudio = new JButton("🎵 Gửi Audio");
        btnAudio.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnAudio.addActionListener(e -> chooseAndSendFile(ChatMessage.Type.AUDIO_CLIP));
        toolBar.add(btnAudio);

        JButton btnVideo = new JButton("🎥 Gửi Video");
        btnVideo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnVideo.addActionListener(e -> chooseAndSendFile(ChatMessage.Type.VIDEO_CLIP));
        toolBar.add(btnVideo);

        JButton btnDownloadLast = new JButton("💾 Tải tệp nhận gần nhất");
        btnDownloadLast.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnDownloadLast.addActionListener(e -> saveReceivedFile());
        toolBar.add(btnDownloadLast);

        bottomPanel.add(toolBar, BorderLayout.NORTH);

        // Input & Send bar
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setOpaque(false);
        txtInput = new JTextField();
        txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtInput.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSend();
                }
            }
        });
        JButton btnSend = new JButton("Gửi 🚀");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setBackground(new Color(0, 104, 255));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setPreferredSize(new Dimension(85, 36));
        btnSend.addActionListener(e -> performSend());

        inputBar.add(txtInput, BorderLayout.CENTER);
        inputBar.add(btnSend, BorderLayout.EAST);
        bottomPanel.add(inputBar, BorderLayout.SOUTH);

        mainChatPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(mainChatPanel, BorderLayout.CENTER);
    }

    private void switchChatTarget(String target, String headerTitle) {
        this.currentTarget = target;
        lblCurrentTarget.setText(headerTitle);
        if ("MULTICAST".equals(target)) {
            lblProtocolInfo.setText("Giao thức: UDP Multicast Socket (Địa chỉ nhóm: " + ChatConstants.MULTICAST_IP + ":" + ChatConstants.MULTICAST_PORT + ")");
            lblProtocolInfo.setForeground(new Color(200, 100, 0));
        } else {
            lblProtocolInfo.setText("Giao thức: TCP Socket tin cậy (Cổng " + serverPort + ")");
            lblProtocolInfo.setForeground(new Color(0, 102, 204));
        }
        renderChatHistory();
    }

    private void initNetworking() {
        // 1. Kết nối TCP tới Server
        new Thread(this::connectTcp).start();
        // 2. Tham gia nhóm UDP Multicast
        new Thread(this::joinMulticastGroup).start();
        // 3. Lắng nghe UDP Broadcast trên mạng
        new Thread(this::listenUdpBroadcast).start();
    }

    private void connectTcp() {
        try {
            tcpSocket = new Socket(serverHost, serverPort);
            out = new ObjectOutputStream(tcpSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(tcpSocket.getInputStream());
            isConnected = true;

            // Gửi gói LOGIN
            out.writeObject(new ChatMessage(ChatMessage.Type.LOGIN, myUsername, "SERVER", ""));
            out.flush();

            // Vòng lặp nhận dữ liệu TCP
            while (isConnected) {
                ChatMessage msg = (ChatMessage) in.readObject();
                handleIncomingTcpMessage(msg);
            }
        } catch (Exception e) {
            appendMessage("ALL", "HỆ THỐNG", "Mất kết nối tới TCP Server: " + e.getMessage(), true);
        }
    }

    private void handleIncomingTcpMessage(ChatMessage msg) {
        if (msg.getType() == ChatMessage.Type.USER_LIST) {
            SwingUtilities.invokeLater(() -> {
                usersModel.clear();
                if (msg.getOnlineUsers() != null) {
                    for (String u : msg.getOnlineUsers()) {
                        if (!u.equalsIgnoreCase(myUsername)) {
                            usersModel.addElement(u);
                        }
                    }
                }
            });
        } else if (msg.getType() == ChatMessage.Type.SYSTEM_NOTICE) {
            appendMessage("ALL", "HỆ THỐNG", msg.getContent(), true);
        } else if (msg.getType() == ChatMessage.Type.BROADCAST_TEXT) {
            appendMessage("ALL", "📢 TOÀN MẠNG", msg.getContent(), true);
        } else if (msg.getType() == ChatMessage.Type.PUBLIC_TEXT) {
            appendMessage("ALL", msg.getSender(), msg.getContent(), false);
        } else if (msg.getType() == ChatMessage.Type.PRIVATE_TEXT) {
            String tabKey = msg.getSender().equalsIgnoreCase(myUsername) ? msg.getReceiver() : msg.getSender();
            appendMessage(tabKey, msg.getSender() + " (Riêng)", msg.getContent(), false);
        } else if (msg.getType() == ChatMessage.Type.FILE_ATTACHMENT ||
                   msg.getType() == ChatMessage.Type.AUDIO_CLIP ||
                   msg.getType() == ChatMessage.Type.VIDEO_CLIP) {
            String tabKey = "ALL".equals(msg.getReceiver()) ? "ALL" : (msg.getSender().equalsIgnoreCase(myUsername) ? msg.getReceiver() : msg.getSender());
            String icon = msg.getType() == ChatMessage.Type.AUDIO_CLIP ? "🎵 [AUDIO]" : (msg.getType() == ChatMessage.Type.VIDEO_CLIP ? "🎥 [VIDEO]" : "📎 [FILE]");
            String note = icon + " " + msg.getFileName() + " (" + (msg.getFileSize() / 1024 + 1) + " KB) - " + (msg.getContent() != null ? msg.getContent() : "");
            if (msg.getFileData() != null) {
                receivedFiles.put(msg.getFileName(), msg.getFileData());
            }
            appendMessage(tabKey, msg.getSender(), note + " <i>(Đã nhận thành công, bấm nút 'Tải tệp nhận' để lưu)</i>", false);
        }
    }

    private void joinMulticastGroup() {
        try {
            multicastSocket = new MulticastSocket(ChatConstants.MULTICAST_PORT);
            multicastGroup = InetAddress.getByName(ChatConstants.MULTICAST_IP);
            SocketAddress groupAddress = new InetSocketAddress(multicastGroup, ChatConstants.MULTICAST_PORT);
            NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (netIf == null) {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface n = interfaces.nextElement();
                    if (n.isUp() && !n.isLoopback()) {
                        netIf = n;
                        break;
                    }
                }
            }
            multicastSocket.joinGroup(groupAddress, netIf);
            appendMessage("MULTICAST", "HỆ THỐNG", "Đã tham gia nhóm UDP Multicast: " + ChatConstants.MULTICAST_IP + ":" + ChatConstants.MULTICAST_PORT, true);

            byte[] buffer = new byte[4096];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);
                String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                // Format: SENDER::CONTENT
                String[] parts = data.split("::", 2);
                if (parts.length == 2) {
                    appendMessage("MULTICAST", parts[0], parts[1], false);
                }
            }
        } catch (Exception e) {
            appendMessage("MULTICAST", "HỆ THỐNG", "Lỗi Multicast: " + e.getMessage(), true);
        }
    }

    private void listenUdpBroadcast() {
        try {
            udpBroadcastListener = new DatagramSocket(ChatConstants.UDP_BROADCAST_PORT);
            byte[] buf = new byte[2048];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                udpBroadcastListener.receive(packet);
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (raw.startsWith("SERVER_BROADCAST::")) {
                    String notice = raw.substring("SERVER_BROADCAST::".length());
                    appendMessage("ALL", "📢 MÁY CHỦ BROADCAST (UDP)", notice, true);
                }
            }
        } catch (Exception ignored) {}
    }

    private void performSend() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        if ("MULTICAST".equals(currentTarget)) {
            // Gửi qua UDP Multicast Socket
            new Thread(() -> {
                try {
                    String payload = myUsername + "::" + text;
                    byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(data, data.length, multicastGroup, ChatConstants.MULTICAST_PORT);
                    multicastSocket.send(packet);
                } catch (Exception e) {
                    appendMessage("MULTICAST", "LỖI", e.getMessage(), true);
                }
            }).start();
        } else {
            // Gửi qua TCP Socket
            if (!isConnected) {
                JOptionPane.showMessageDialog(this, "Chưa kết nối tới TCP Server!");
                return;
            }
            ChatMessage.Type type = "ALL".equals(currentTarget) ? ChatMessage.Type.PUBLIC_TEXT : ChatMessage.Type.PRIVATE_TEXT;
            ChatMessage msg = new ChatMessage(type, myUsername, currentTarget, text);
            new Thread(() -> {
                try {
                    out.writeObject(msg);
                    out.flush();
                } catch (IOException e) {
                    appendMessage(currentTarget, "LỖI GỬI", e.getMessage(), true);
                }
            }).start();
        }
        txtInput.setText("");
    }

    private void chooseAndSendFile(ChatMessage.Type fileType) {
        if ("MULTICAST".equals(currentTarget)) {
            JOptionPane.showMessageDialog(this, "Gửi tệp đính kèm lớn hoạt động qua kênh TCP Tin cậy! Vui lòng chọn Phòng Chung hoặc Chat riêng để gửi tệp.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.length() > 25 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, "Dung lượng tệp vượt quá 25MB!");
                return;
            }
            new Thread(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String caption = JOptionPane.showInputDialog(this, "Nhập chú thích cho tệp (hoặc để trống):", file.getName());
                    if (caption == null) caption = "";

                    ChatMessage msg = ChatMessage.createFileMessage(fileType, myUsername, currentTarget, caption, file.getName(), bytes);
                    out.writeObject(msg);
                    out.flush();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi đọc tệp: " + ex.getMessage());
                }
            }).start();
        }
    }

    private void saveReceivedFile() {
        if (receivedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có tệp nào được nhận!");
            return;
        }
        String[] fileNames = receivedFiles.keySet().toArray(new String[0]);
        String selectedFile = (String) JOptionPane.showInputDialog(this, "Chọn tệp cần lưu về máy:", "Lưu Tệp Nhận Được", JOptionPane.QUESTION_MESSAGE, null, fileNames, fileNames[0]);
        if (selectedFile != null) {
            byte[] data = receivedFiles.get(selectedFile);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(selectedFile));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.write(chooser.getSelectedFile().toPath(), data);
                    JOptionPane.showMessageDialog(this, "Đã lưu tệp thành công vào: " + chooser.getSelectedFile().getAbsolutePath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi ghi tệp: " + ex.getMessage());
                }
            }
        }
    }

    private void appendMessage(String targetKey, String sender, String text, boolean isSystem) {
        StringBuilder sb = chatHistories.computeIfAbsent(targetKey, k -> new StringBuilder());
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());

        if (isSystem) {
            sb.append("<div style='margin: 4px 0; text-align: center; color: #888888; font-size: 11px;'>")
              .append("[").append(time).append("] <i>").append(text).append("</i></div>");
        } else {
            boolean isMe = sender.equalsIgnoreCase(myUsername);
            String align = isMe ? "right" : "left";
            String bg = isMe ? "#dcf8c6" : "#ffffff";
            String border = isMe ? "#b2e28a" : "#e0e0e0";

            sb.append("<div style='margin: 6px 0; text-align: ").append(align).append(";'>")
              .append("<div style='display: inline-block; max-width: 75%; background-color: ").append(bg)
              .append("; border: 1px solid ").append(border).append("; border-radius: 8px; padding: 6px 10px; text-align: left;'>")
              .append("<div style='font-weight: bold; font-size: 11px; color: ").append(isMe ? "#0b8043" : "#1a73e8").append(";'>")
              .append(sender).append(" <span style='color: #888; font-size: 9px;'>").append(time).append("</span></div>")
              .append("<div style='margin-top: 3px; font-size: 13px; color: #222;'>").append(text).append("</div>")
              .append("</div></div>");
        }

        if (targetKey.equals(currentTarget)) {
            renderChatHistory();
        }
    }

    private void renderChatHistory() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = chatHistories.getOrDefault(currentTarget, new StringBuilder());
            String html = "<html><body style='font-family: Segoe UI, sans-serif; padding: 10px;'>" + sb.toString() + "</body></html>";
            chatPane.setText(html);
            chatPane.setCaretPosition(chatPane.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(ChatClientApp::new);
    }
}
