import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN,
        LOGOUT,
        USER_LIST,
        PUBLIC_TEXT,
        PRIVATE_TEXT,
        MULTICAST_TEXT,
        BROADCAST_TEXT,
        FILE_ATTACHMENT,
        AUDIO_CLIP,
        VIDEO_CLIP,
        SYSTEM_NOTICE
    }

    private Type type;
    private String sender;
    private String receiver; // "ALL", "MULTICAST", or specific username
    private String content;
    private String fileName;
    private byte[] fileData;
    private long fileSize;
    private List<String> onlineUsers;
    private long timestamp;

    public ChatMessage(Type type, String sender, String receiver, String content) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public static ChatMessage createFileMessage(Type type, String sender, String receiver, String caption, String fileName, byte[] data) {
        ChatMessage msg = new ChatMessage(type, sender, receiver, caption);
        msg.fileName = fileName;
        msg.fileData = data;
        msg.fileSize = data != null ? data.length : 0;
        return msg;
    }

    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
    public String getFileName() { return fileName; }
    public byte[] getFileData() { return fileData; }
    public long getFileSize() { return fileSize; }
    public List<String> getOnlineUsers() { return onlineUsers; }
    public long getTimestamp() { return timestamp; }

    public void setOnlineUsers(List<String> users) { this.onlineUsers = users; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }
}
