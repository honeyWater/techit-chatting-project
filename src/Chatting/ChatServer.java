package Chatting;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatServer {
    public static void main(String[] args) {
        // 서버 소켓 생성
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("서버가 준비되었습니다.");
            // 전체 클라이언트의 정보를 기억할 공간 <닉네임, PrintWriter>
            Map<String, PrintWriter> chatClients = new HashMap<>();
            // 각 방에 어떤 클라이언트가 있는지 - <방 번호, ClientInfo(PrintWriter, nickName)>
            Map<Integer, List<ClientInfo>> chattingRooms = new HashMap<>();

            while (true) {
                Socket socket = serverSocket.accept();

                new ChatThread(socket, chatClients, chattingRooms).start();
            }
        } catch (Exception e) {
            e.getStackTrace();
        }
    }
}
