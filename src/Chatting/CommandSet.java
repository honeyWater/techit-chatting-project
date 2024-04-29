package Chatting;

public class CommandSet {
    static final String COMMAND_SET = "\n명령어 보기 : /command\n"
            + "현재 위치 보기 : /state\n" // 콘솔이다 보니 채팅하다 보면 현재 위치에 대한 상태 확인이 어려움
            + "방 목록 보기 : /list\n"
            + "방 생성 : /create\n"
            + "방 입장 : /join [방번호]\n"
            + "전체 사용자 목록 : /users\n"
            + "현재 방 사용자 목록 : /roomusers\n"
            + "귓속말 : /whisper [닉네임] [메시지]\n"
            + "방 나가기 : /exit\n"
            + "접속종료 : /bye\n";
}
