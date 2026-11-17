import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FTPClientEngine {
    public interface LogListener {
        void onLog(String message, boolean isCommand);
    }

    public interface ProgressListener {
        void onProgress(long transferredBytes, long totalBytes);
    }

    public static class RemoteFileItem {
        private final String name;
        private final long size;
        private final boolean isDirectory;
        private final String permissions;
        private final String date;

        public RemoteFileItem(String name, long size, boolean isDirectory, String permissions, String date) {
            this.name = name;
            this.size = size;
            this.isDirectory = isDirectory;
            this.permissions = permissions;
            this.date = date;
        }

        public String getName() { return name; }
        public long getSize() { return size; }
        public boolean isDirectory() { return isDirectory; }
        public String getPermissions() { return permissions; }
        public String getDate() { return date; }

        @Override
        public String toString() {
            return (isDirectory ? "[DIR] " : "") + name + (isDirectory ? "" : " (" + size + " bytes)");
        }
    }

    private Socket controlSocket;
    private BufferedReader controlReader;
    private BufferedWriter controlWriter;
    private LogListener logListener;
    private String currentRemoteDir = "/";
    private boolean isConnected = false;

    public FTPClientEngine(LogListener logListener) {
        this.logListener = logListener;
    }

    public boolean isConnected() {
        return isConnected && controlSocket != null && !controlSocket.isClosed();
    }

    public String getCurrentRemoteDir() {
        return currentRemoteDir;
    }

    public synchronized void connect(String host, int port) throws IOException {
        controlSocket = new Socket();
        controlSocket.connect(new InetSocketAddress(host, port), 5000);
        controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));
        
        String welcome = readResponse();
        if (!welcome.startsWith("220")) {
            throw new IOException("Server rejected connection: " + welcome);
        }
        isConnected = true;
    }

    public synchronized boolean login(String user, String pass) throws IOException {
        String respUser = sendCommand("USER " + user);
        if (respUser.startsWith("331")) {
            String respPass = sendCommand("PASS " + pass);
            if (respPass.startsWith("230")) {
                pwd();
                return true;
            }
        } else if (respUser.startsWith("230")) {
            pwd();
            return true;
        }
        return false;
    }

    public synchronized String pwd() throws IOException {
        String resp = sendCommand("PWD");
        Pattern p = Pattern.compile("\"([^\"]+)\"");
        Matcher m = p.matcher(resp);
        if (m.find()) {
            currentRemoteDir = m.group(1);
        }
        return currentRemoteDir;
    }

    public synchronized boolean cwd(String path) throws IOException {
        String resp = sendCommand("CWD " + path);
        if (resp.startsWith("250")) {
            pwd();
            return true;
        }
        return false;
    }

    public synchronized boolean cdup() throws IOException {
        String resp = sendCommand("CDUP");
        if (resp.startsWith("250")) {
            pwd();
            return true;
        }
        return false;
    }

    private synchronized Socket openDataConnection() throws IOException {
        String resp = sendCommand("PASV");
        if (!resp.startsWith("227")) {
            throw new IOException("PASV mode failed: " + resp);
        }
        // Example: 227 Entering Passive Mode (192,168,1,5,8,72)
        int openParen = resp.indexOf('(');
        int closeParen = resp.indexOf(')', openParen);
        if (openParen == -1 || closeParen == -1) {
            throw new IOException("Cannot parse PASV response: " + resp);
        }
        String[] parts = resp.substring(openParen + 1, closeParen).split(",");
        String host = controlSocket.getInetAddress().getHostAddress();
        int port = (Integer.parseInt(parts[4]) << 8) + Integer.parseInt(parts[5]);

        Socket dataSocket = new Socket();
        dataSocket.connect(new InetSocketAddress(host, port), 10000);
        return dataSocket;
    }

    public synchronized List<RemoteFileItem> listFiles() throws IOException {
        List<RemoteFileItem> items = new ArrayList<>();
        Socket dataSocket = openDataConnection();
        String listResp = sendCommand("LIST");
        if (!listResp.startsWith("150")) {
            dataSocket.close();
            throw new IOException("LIST failed: " + listResp);
        }

        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                RemoteFileItem item = parseUnixListLine(line);
                if (item != null) {
                    items.add(item);
                }
            }
        } finally {
            dataSocket.close();
        }

        readResponse(); // Read 226 Directory send OK
        return items;
    }

    private RemoteFileItem parseUnixListLine(String line) {
        try {
            // drwxrwxrwx 1 ftp ftp 4096 Nov 03 00:00 folderName
            // -rwxrwxrwx 1 ftp ftp 1234 Nov 03 00:00 filename.txt
            String[] tokens = line.split("\\s+", 9);
            if (tokens.length >= 9) {
                String perms = tokens[0];
                boolean isDir = perms.startsWith("d");
                long size = 0;
                try {
                    size = Long.parseLong(tokens[4]);
                } catch (NumberFormatException ignored) {}
                String date = tokens[5] + " " + tokens[6] + " " + tokens[7];
                String name = tokens[8];
                return new RemoteFileItem(name, size, isDir, perms, date);
            }
        } catch (Exception ignored) {}
        return new RemoteFileItem(line, 0, false, "", "");
    }

    public synchronized void downloadFile(String remoteFileName, File localDestination, ProgressListener progressListener) throws IOException {
        sendCommand("TYPE I");
        Socket dataSocket = openDataConnection();
        String retrResp = sendCommand("RETR " + remoteFileName);
        if (!retrResp.startsWith("150")) {
            dataSocket.close();
            throw new IOException("RETR failed: " + retrResp);
        }

        long totalSize = -1;
        Pattern p = Pattern.compile("\\(([0-9]+)\\s+bytes\\)");
        Matcher m = p.matcher(retrResp);
        if (m.find()) {
            totalSize = Long.parseLong(m.group(1));
        }

        try (InputStream dataIn = dataSocket.getInputStream();
             OutputStream fos = new FileOutputStream(localDestination)) {
            byte[] buffer = new byte[8192];
            int read;
            long transferred = 0;
            while ((read = dataIn.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                transferred += read;
                if (progressListener != null) {
                    progressListener.onProgress(transferred, totalSize);
                }
            }
            fos.flush();
        } finally {
            dataSocket.close();
        }
        readResponse(); // Read 226 Transfer complete
    }

    public synchronized void uploadFile(File localFile, String remoteFileName, ProgressListener progressListener) throws IOException {
        sendCommand("TYPE I");
        Socket dataSocket = openDataConnection();
        String storResp = sendCommand("STOR " + remoteFileName);
        if (!storResp.startsWith("150")) {
            dataSocket.close();
            throw new IOException("STOR failed: " + storResp);
        }

        long totalSize = localFile.length();
        try (InputStream fis = new FileInputStream(localFile);
             OutputStream dataOut = dataSocket.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long transferred = 0;
            while ((read = fis.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
                transferred += read;
                if (progressListener != null) {
                    progressListener.onProgress(transferred, totalSize);
                }
            }
            dataOut.flush();
        } finally {
            dataSocket.close();
        }
        readResponse(); // Read 226 Transfer complete
    }

    public synchronized boolean deleteFile(String remoteName) throws IOException {
        String resp = sendCommand("DELE " + remoteName);
        return resp.startsWith("250");
    }

    public synchronized boolean makeDirectory(String dirName) throws IOException {
        String resp = sendCommand("MKD " + dirName);
        return resp.startsWith("257");
    }

    public synchronized boolean removeDirectory(String dirName) throws IOException {
        String resp = sendCommand("RMD " + dirName);
        return resp.startsWith("250");
    }

    public synchronized void disconnect() {
        if (isConnected) {
            try {
                sendCommand("QUIT");
            } catch (Exception ignored) {}
            try {
                if (controlSocket != null) controlSocket.close();
            } catch (Exception ignored) {}
            isConnected = false;
        }
    }

    public synchronized String sendCommand(String cmd) throws IOException {
        if (logListener != null) {
            logListener.onLog("Lệnh: " + cmd, true);
        }
        controlWriter.write(cmd + "\r\n");
        controlWriter.flush();
        return readResponse();
    }

    private String readResponse() throws IOException {
        String line = controlReader.readLine();
        if (line == null) throw new IOException("Connection closed by server.");
        if (logListener != null) {
            logListener.onLog("Phản hồi: " + line, false);
        }
        return line;
    }
}
