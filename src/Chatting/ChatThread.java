package Chatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

import static Chatting.CommandSet.COMMAND_SET;

public class ChatThread extends Thread {
    private final Socket socket;
    private String nickName;    // ClientInfo의 nickName으로 사용해도 됨(수정사항이 많아서 보류)
    private int roomNumber = 0; // 대기실 : 0, 나머지 방 : 1 이상
    private final Map<String, PrintWriter> chatClients;
    private final Map<Integer, List<ClientInfo>> chattingRooms;
    private BufferedReader in;
    private PrintWriter out;
    private ClientInfo clientInfo;

    public ChatThread(Socket socket, Map<String, PrintWriter> chatClients, Map<Integer, List<ClientInfo>> chattingRooms) {
        this.socket = socket;
        this.chatClients = chatClients;
        this.chattingRooms = chattingRooms;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 닉네임 검사 및 추가
            synchronized (chatClients) {
                checkNickName();
            }

            // 사용자 접속 확인 및 사용자의 IP 주소 출력
            System.out.println("'" + nickName + "'님이 접속했습니다.");
            System.out.println("'" + nickName + "'의 IP : " + socket.getLocalAddress().getHostAddress());

        } catch (Exception e) {
            e.getStackTrace();
        }
    }

    @Override
    public void run() {
        sendMsgToThisClient("\n반갑습니다. '" + nickName + "'님 채팅 서비스 접속을 환영합니다." +
                "현재 로비에 입장하셨습니다.");
        sendMsgToThisClient(COMMAND_SET); // 명령어 모음 전송

        // 명령어에 따라서 맞는 기능 수행
        String line;
        try {
            while ((line = in.readLine()) != null) {
                String[] msg = line.split(" "); // 0 : command, 1,2는 명령어에 따라 다름

                if (msg[0].equalsIgnoreCase("/bye")) {
                    bye();
                    break;
                } else if (msg[0].equalsIgnoreCase("/command")) {
                    sendMsgToThisClient(COMMAND_SET);
                } else if (msg[0].equalsIgnoreCase("/state")) {
                    viewCurrentState();
                } else if (msg[0].equalsIgnoreCase("/list")) {
                    showRoomList();
                } else if (msg[0].equalsIgnoreCase("/create")) {
                    createRoom();
                } else if (msg[0].equalsIgnoreCase("/join")) {
                    joinRoom(line);
                } else if (msg[0].equalsIgnoreCase("/users")) {
                    showAllUserList();
                } else if (msg[0].equalsIgnoreCase("/roomusers")) {
                    showAllUserListInRoom();
                } else if (msg[0].equalsIgnoreCase("/whisper")) {
                    whisper(line);
                } else if (msg[0].equalsIgnoreCase("/exit")) {
                    getOutRoom();
                } else {
                    sendMsgToSameRoomClients(roomNumber, nickName + " : " + line);
                }
            }

        } catch (Exception e) {
            bye();  // 비정상적인 종료가 일어났을 시에도 bye 수행
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

    // 추가 기능 - 닉네임 중복과 '/'로 시작하는지 확인 및 응답 로직
    public void checkNickName() throws IOException {
        while (true) {
            nickName = in.readLine();

            if(nickName.charAt(0) == '/'){
                sendMsgToThisClient("NO");
            } else if (!chatClients.containsKey(nickName)) {
                sendMsgToThisClient("OK");
                clientInfo = new ClientInfo(out, nickName);
                chatClients.put(nickName, out);
                addClientToLobby(); // 사용자를 로비에 배정
                break;
            } else {
                sendMsgToThisClient("NO");
            }
        }
    }

    // 기본 기능 - 해당 클라이언트한테만 메시지를 보내는 메서드
    public void sendMsgToThisClient(String msg) {
        out.println(msg);
    }

    // 기본 기능 - 메시지를 같은 방(로비) 내 사용자들에게 보내는 메서드
    public void sendMsgToSameRoomClients(int roomNum, String msg) {
        synchronized (chatClients) {
            for (ClientInfo client : chattingRooms.get(roomNum)) {
                client.getPrintWriter().println(msg);
            }
        }
    }

    // 추가 기능 - 현재 로비인지 특정 방에 있는지 보여주는 메서드
    public void viewCurrentState() {
        if (roomNumber == 0) {
            sendMsgToThisClient("현재 위치는 로비입니다.\n");
        } else {
            sendMsgToThisClient("현재 위치는 " + roomNumber + "번 방입니다.\n");
        }
    }

    // 기본 기능 - 방 목록 보기(/list)
    public void showRoomList() {
        StringBuilder sb = new StringBuilder();

        sb.append("--------- 방 목록 --------\n");
        synchronized (chattingRooms) {
            // 방이 존재한다면
            if (!chattingRooms.isEmpty()) {
                for (int roomNum : chattingRooms.keySet()) {
                    if (roomNum > 0) {
                        sb.append("[채팅방 ").append(roomNum).append("] - [").append(chattingRooms.get(roomNum).size()).append("명 접속중]\n");
                    } else {
                        sb.append("[로비] - [").append(chattingRooms.get(roomNum).size()).append("명 접속중]\n");
                    }
                }
                sb.append("--------------------------\n");
                sendMsgToThisClient(String.valueOf(sb));
            } else {
                sendMsgToThisClient("현재 생성된 채팅방이 없습니다.\n");
            }
        }
    }

    // 기본 기능 - 방 생성 메서드(/create)
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
            chattingRooms.put(roomNumber, new ArrayList<>(Arrays.asList(clientInfo)));
            removeFromLobby();
        }

        // 방 생성에 대한 출력 (서버, 해당 클라이언트)
        System.out.println("[" + roomNumber + "]번 방이 생성되었습니다.\n" +
                "'" + nickName + "'님이 [" + roomNumber + "]번 방에 입장했습니다.");
        sendMsgToThisClient("방 번호 [" + roomNumber + "]가 생성되었습니다.\n" +
                "[" + roomNumber + "]번 방에 입장했습니다.\n");
    }

    // 기본 기능 - 방 입장(/join)
    public void joinRoom(String line) {
        String[] msg = line.split(" ", 2);
        if (msg.length != 2) {
            sendMsgToThisClient("잘못된 명령어입니다. 다시 입력해주세요.\n");
            return;
        }

        int roomNum;
        try {    // 사용자가 방 번호를 다른 문자로 입력했을 시 오류 처리
            roomNum = Integer.parseInt(msg[1]);
        } catch (Exception e) {
            sendMsgToThisClient("방 번호를 정확히 입력해주세요.\n");
            return;
        }

        // 사용자가 이미 방에 들어와 있는 경우
        if (roomNumber != 0) {
            sendMsgToThisClient("현재 방에 들어와 있는 상태이므로 '/join' 명령어는 사용할 수 없습니다.\n");
            return;
        }

        synchronized (chattingRooms) {
            // 입력한 방이 존재하지 않는 방일 경우 처리
            if (!chattingRooms.containsKey(roomNum)) {
                sendMsgToThisClient("존재하지 않는 방 번호입니다. 다시 입력해주세요.\n");
                showRoomList();
            }
            // 로비인 상태에서 로비로 들어가려고 하는 경우
            else if (roomNum == 0) {
                sendMsgToThisClient("이미 로비에 있습니다.\n");
            }
            // 방에 있는 상태에서 /join 명령어로 로비로 가려고 하는 경우
            else if (roomNumber != 0 && roomNum == 0) {
                sendMsgToThisClient("방에서는 '/exit' 명령어로 나가주시기 바랍니다.\n");
            }
            // 존재하는 방일 경우 join
            else {
                this.roomNumber = roomNum;
                synchronized (chattingRooms) {
                    chattingRooms.get(roomNumber).add(clientInfo);
                }
                removeFromLobby();

                sendMsgToSameRoomClients(roomNum, "'" + nickName + "'님이 방에 입장했습니다.\n");
            }
        }
    }

    // 추가 기능 - 현재 접속 중인 모든 사용자의 목록 보기(/users)
    public void showAllUserList() {
        StringBuilder sb = new StringBuilder();

        sb.append("-------- 현재 접속 유저 -------\n");
        synchronized (chatClients) {
            for (String name : chatClients.keySet()) {
                sb.append(name).append('\n');
            }
        }
        sb.append("-------------------------------\n");
        sendMsgToThisClient(String.valueOf(sb));
    }

    // 추가 기능 - 현재 방에 있는 모든 사용자의 목록 확인(/roomusers)
    public void showAllUserListInRoom() {
        StringBuilder sb = new StringBuilder();

        synchronized (chattingRooms) {
            if (roomNumber == 0) {
                sb.append("-------- ").append("로비 유저 목록").append(" --------\n");
            } else {
                sb.append("------- ").append(roomNumber).append("번 방 유저 목록").append(" -------\n");
            }

            for (ClientInfo client : chattingRooms.get(roomNumber)) {
                sb.append(client.getNickName()).append('\n');
            }
        }
        sb.append("--------------------------------\n");
        sendMsgToThisClient(String.valueOf(sb));
    }

    // 추가 기능 - 메시지를 특정 사용자한테만 보내는 메서드(/whisper)
    public void whisper(String msg) {
        int firstSpaceIndex = msg.indexOf(" ");
        if (firstSpaceIndex == -1) {     // 공백이 없다면
            sendMsgToThisClient("명령어를 올바른 형식으로 써주시기 바랍니다.\n" +
                    "ex) /whisper id msg\n");
            return;
        }

        int secondSpaceIndex = msg.indexOf(" ", firstSpaceIndex + 1);
        if (secondSpaceIndex == -1) {     // 두번 째 공백이 없다는 것도 메시지가 잘못된 것
            sendMsgToThisClient("명령어를 올바른 형식으로 써주시기 바랍니다.\n" +
                    "ex) /whisper id msg\n");
            return;
        }

        String to = msg.substring(firstSpaceIndex + 1, secondSpaceIndex);
        String message = msg.substring(secondSpaceIndex + 1);

        synchronized (chatClients) {
            // to가 존재하는 사용자인가?
            if (!chatClients.containsKey(to)) {
                sendMsgToThisClient("'" + to + "'님은 존재하지 않는 사용자입니다.\n");
            } else {
                PrintWriter pw = chatClients.get(to);
                pw.println("[귓속말] " + nickName + " : " + message + "\n");
            }
        }
    }

    // 기본 기능 - 방 나가기(/exit)
    public void getOutRoom() {
        if (roomNumber == 0) {
            sendMsgToThisClient("현재는 방에 있는 상태가 아닙니다.\n");
            return;
        }

        synchronized (chattingRooms) {
            chattingRooms.get(roomNumber).removeIf(clientOut -> clientOut == clientInfo); // out

            // 방에 아무도 남지 않으면
            if (chattingRooms.get(roomNumber).isEmpty()) {
                chattingRooms.remove(roomNumber);
                System.out.println("'" + nickName + "'님이 [" + roomNumber + "]번 방을 나갑니다." +
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
        addClientToLobby();
    }

    // 기본 기능 - /bye
    public void bye() {
        // 방에서 '/bye'를 치면 방에서 나간 후 로비에서 제거
        // 이미 로비라면 로비에서 제거만 하면 된다.
        if (roomNumber > 0) {
            getOutRoom();
        }
        removeFromLobby();

        // 사용자 정보 제거
        synchronized (chatClients) {
            chatClients.remove(nickName);
        }

        System.out.println("'" + nickName + "'님이 연결을 끊었습니다.");
    }

    // 추가 기능 - 사용자를 로비에 추가
    public void addClientToLobby() {
        synchronized (chattingRooms) {
            // 맨 처음 사용자이면 로비(0)에 대한 키 조차도 생성 안됐을 경우
            if (!chattingRooms.containsKey(0)) {
                chattingRooms.put(0, new ArrayList<>(Arrays.asList(clientInfo)));
            }
            // 키는 있는데 ClientInfo List가 null일 경우
            else if (chattingRooms.get(0) == null) {
                chattingRooms.put(0, new ArrayList<>(Arrays.asList(clientInfo)));
            }
            // 로비에 한 명 이상 있을 경우
            else {
                chattingRooms.get(0).add(clientInfo);
            }
        }
    }

    // 추가 기능 - 사용자를 로비에서 제거
    public void removeFromLobby() {
        synchronized (chattingRooms) {
            chattingRooms.get(0).remove(clientInfo);
        }
    }
}
