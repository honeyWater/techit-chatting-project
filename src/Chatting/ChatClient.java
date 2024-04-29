package Chatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        String hostName = "localhost"; // 서버가 실행 중인 호스트의 이름 또는 IP 주소
        int portNumber = 12345; // 서버와 동일한 포트 번호 사용

        Socket socket;
        PrintWriter out;
        BufferedReader in;
        try {
            socket = new Socket(hostName, portNumber);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner stdIn = new Scanner(System.in);

            String isDuplicated = "false";
            while (true) {
                System.out.print("사용할 닉네임을 입력하세요 : ");
                String nickName = stdIn.nextLine();

                out.println(nickName); // 서버에 닉네임을 전송
                String response = in.readLine(); // 서버의 응답 받기

                if (response.equals("OK")) {
                    break;
                } else {
                    System.out.println("중복된 닉네임 이거나 '/'로 시작하는 닉네임입니다.");
                }
            }

            // 서버로부터 메시지를 읽어 화면에 출력하는 별도의 스레드
            Thread readThread = new Thread(new ServerMessageReader(in));
            readThread.start(); // 메시지 읽기 스레드 시작

            // 사용자 입력 처리
            String userInput;
            while (true) {
                userInput = stdIn.nextLine();

                // '/bye'를 입력하면 클라이언트를 종료합니다.
                if ("/bye".equals(userInput)) {
                    out.println(userInput);
                    break;
                }

                // 서버에 메시지를 전송합니다.
                out.println(userInput);
            } // end of while

            /*
             * 클라이언트와 서버는 명시적으로 close를 합니다.
             * close를 할 경우 상대방쪽의 readLine()이 null을 반환됩니다.
             * 이 값을 이용하여 접속이 종료된 것을 알 수 있습니다.
             */
            in.close();
            out.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("Exception caught when trying to connect to " + hostName + " on port " + portNumber);
            e.getStackTrace();
        }
    }
}
