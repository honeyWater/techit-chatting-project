package Chatting;

import java.io.PrintWriter;

public class ClientInfo {
    private PrintWriter printWriter;
    private String nickName;

    public ClientInfo(PrintWriter printWriter, String nickName) {
        this.printWriter = printWriter;
        this.nickName = nickName;
    }

    public PrintWriter getPrintWriter() {
        return printWriter;
    }

    public void setPrintWriter(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
}
