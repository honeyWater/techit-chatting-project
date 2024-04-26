package Chatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

import static Chatting.CommandSet.COMMAND_SET;

public class ChatThread extends Thread {
    private final Socket socket;
    private String nickName;
    private int roomNumber = 0; // 대기실 : 0, 나머지 방 : 1 이상
    private final Map<String, PrintWriter> chatClients;
    private final Map<Integer, List<PrintWriter>> chattingRooms;
    private BufferedReader in;
    private PrintWriter out;

    public ChatThread(Socket socket, Map<String, PrintWriter> chatClients, Map<Integer, List<PrintWriter>> chattingRooms) {
        this.socket = socket;
        this.chatClients = chatClients;
        this.chattingRooms = chattingRooms;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 닉네임 검사 및 추가
            do {
                nickName = in.readLine();
            } while (!checkNickName());

            // 사용자 접속 확인 및 사용자의 IP 주소 출력
            System.out.println("'" + nickName + "'님이 접속했습니다.");
            System.out.println("'" + nickName + "'의 IP : " + socket.getLocalAddress().getHostAddress());
            System.out.println(in);

        } catch (Exception e) {
            e.getStackTrace();
        }
    }

    @Override
    public void run() {
        sendMsgToThisClient("\n반갑습니다. '" + nickName + "'님 채팅 서비스 접속을 환영합니다.");
        sendMsgToThisClient(COMMAND_SET); // 명령어 모음 전송

        // 명령어에 따라서 맞는 기능 수행
        String line;
        try {
            while ((line = in.readLine()) != null) {
                String[] msg = line.split(" "); // 0 : command, 1 : roomNum

                if (msg[0].equalsIgnoreCase("/bye")) {
                    bye();
                    break;
                } else if (msg[0].equalsIgnoreCase("/command")) {
                    sendMsgToThisClient(COMMAND_SET);
                } else if (msg[0].equalsIgnoreCase("/list")) {
                    showRoomList();
                } else if (msg[0].equalsIgnoreCase("/create")) {
                    createRoom();
                } else if (msg[0].equalsIgnoreCase("/join")) {
                    joinRoom(msg[1]);
                } else if (msg[0].equalsIgnoreCase("/exit")) {
                    getOutRoom();
                } else {
                    sendMsgToSameRoomClients(roomNumber, nickName + " : " + line);
                }
            }

        } catch (Exception e) {
            e.getStackTrace();
        } finally {
            // 자원 해제
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (Exception e) {
                e.getStackTrace();
            }
        }
    }

    // 해당 클라이언트한테만 메시지를 보내는 메서드
    public void sendMsgToThisClient(String msg) {
        out.println(msg);
    }

    // 추가 기능 - 메시지를 특정 사용자한테만 보내는 메서드(귓속말)
    // 아직 구현 x

    // 메시지를 같은 방(로비) 내 사용자들에게 보내는 메서드
    public void sendMsgToSameRoomClients(int roomNum, String msg) {
        synchronized (chatClients) {
            for (PrintWriter out : chattingRooms.get(roomNum)) {
                out.println(msg);
            }
        }
    }

    // 방 목록 보기
    public void showRoomList() {
        StringBuilder sb = new StringBuilder();

        sb.append("--------- 방 목록 --------\n");
        synchronized (chattingRooms) {
            if (!chattingRooms.isEmpty()) {
                for (int roomNum : chattingRooms.keySet()) {
                    sb.append("[채팅방 ").append(roomNum).append("] - [").append(chattingRooms.get(roomNum).size()).append("명 접속중]\n");
                }
                sb.append("--------------------------\n");
                sendMsgToThisClient(String.valueOf(sb));
            } else {
                sendMsgToThisClient("현재 생성된 채팅방이 없습니다.\n");
            }
        }
    }

    // 방 생성 메서드
    public void createRoom() {
        // 사용자가 이미 방에 들어와 있는 경우
        if (roomNumber != 0) {
            sendMsgToThisClient("현재 방에 들어와 있는 상태이므로 '/create' 명령어는 사용할 수 없습니다.\n");
            return;
        }

        synchronized (chattingRooms) {
            // 방이 하나도 없으면 null -> 방 번호 1
            if (chattingRooms.isEmpty()) {
                this.roomNumber = 1;
            }
            // 방이 이미 있으면 방 번호의 최댓값 + 1
            else {
                int maxRoomNum = Integer.MIN_VALUE;
                for (int roomNum : chattingRooms.keySet()) {
                    if (maxRoomNum < roomNum) {
                        maxRoomNum = roomNum;
                    }
                }
                this.roomNumber = ++maxRoomNum;
            }

            // 방 생성시 인원수는 최초로, 1명
            chattingRooms.put(roomNumber, new ArrayList<>(Arrays.asList(out)));
        }

        // 방 생성에 대한 출력 (서버, 해당 클라이언트)
        System.out.println("[" + roomNumber + "]번 방이 생성되었습니다.\n" +
                "'" + nickName + "'님이 [" + roomNumber + "]번 방에 입장했습니다.\n");
        sendMsgToThisClient("방 번호 [" + roomNumber + "]가 생성되었습니다.\n" +
                "[" + roomNumber + "]번 방에 입장했습니다.\n");
    }

    // 방 입장
    public void joinRoom(String num) {
        int roomNum;

        try {    // 사용자가 방 번호를 다른 문자로 입력했을 시 오류 처리
            roomNum = Integer.parseInt(num);
        } catch (Exception e) {
            System.out.println("방 번호를 정확히 입력해주세요.");
            return;
        }

        // 사용자가 이미 방에 들어와 있는 경우
        if (roomNumber != 0) {
            sendMsgToThisClient("현재 방에 들어와 있는 상태이므로 '/join' 명령어는 사용할 수 없습니다.\n");
        }

        synchronized (chattingRooms) {
            // 입력한 방이 존재하지 않는 방일 경우 처리
            if (!chattingRooms.containsKey(roomNum)) {
                sendMsgToThisClient("존재하지 않는 방 번호입니다. 다시 입력해주세요");
                showRoomList();
            }
            // 존재하는 방일 경우 join
            else {
                this.roomNumber = roomNum;
                synchronized (chattingRooms) {
                    chattingRooms.get(roomNumber).add(out);
                }

                sendMsgToSameRoomClients(roomNum, "'" + nickName + "'님이 방에 입장했습니다.\n");
            }
        }
    }

    // 방 나가기
    public void getOutRoom() {
        if (roomNumber == 0) {
            sendMsgToThisClient("현재는 방에 있는 상태가 아닙니다.\n");
            return;
        }

        synchronized (chattingRooms) {
            chattingRooms.get(roomNumber).removeIf(clientOut -> clientOut == out);

            // 방에 아무도 남지 않으면
            if (chattingRooms.get(roomNumber).isEmpty()) {
                chattingRooms.remove(roomNumber);
                System.out.println(nickName + "님이 [" + roomNumber + "]번 방을 나갑니다." +
                        "[" + roomNumber + "]번 방에 인원이 없어 삭제되었습니다.");
                sendMsgToThisClient("\n[" + roomNumber + "]번 방을 나갑니다.\n" +
                        "방에 아무도 없어 [" + roomNumber + "]번 방이 삭제되었습니다.\n");
            }
            // 방에 사람이 남아 있으면
            else {
                System.out.println(nickName + "님이 [" + roomNumber + "]번 방을 나갑니다.");
                sendMsgToSameRoomClients(roomNumber, nickName + "님이 방을 나갔습니다.");
                sendMsgToThisClient("[" + roomNumber + "]번 방을 나갑니다.\n");
            }
        }

        this.roomNumber = 0;
    }

    // bye
    public void bye() {
        synchronized (chatClients) {
            chatClients.remove(nickName);
        }
        System.out.println("'" + nickName + "'님이 연결을 끊었습니다.");
    }

    // 닉네임 중복과 '/'로 시작하는지 확인 및 응답 로직
    public boolean checkNickName() {
        synchronized (chatClients) {
            if (nickName.charAt(0) == '/') {
                sendMsgToThisClient("'/'로 시작하는 닉네임은 만들 수 없습니다. 다시 입력해주세요.\n");
                return false;
            } else if (chatClients.containsKey((nickName))) {
                sendMsgToThisClient("이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해주세요.\n");
                return false;
            } else {
                chatClients.put(this.nickName, out);
                sendMsgToThisClient("OK");
                return true;
            }
        }
    }
}
