package server_socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import server_socket.entity.Room;


public class SimpleGUIserver {
	
	public static List<ConnectedSocket> connectedSocketList = new ArrayList<>();
	public static List<Room> roomList = new ArrayList<>();
	
	public static void main(String[] args) {
		try {
			ServerSocket serverSocket = new ServerSocket(8000);
			System.out.println("[ 서버실행 ]"); // 서버를 실행 합니다.
			
			while(true) {
				Socket socket = serverSocket.accept(); // 기다립니다. 어디서 응답을 기다리는지
				System.out.println("접속");
				ConnectedSocket connectedSocket = new ConnectedSocket(socket);
				connectedSocket.start(); // connectedSocket 시작합니다.
				connectedSocketList.add(connectedSocket);
			}
		}catch (IOException e) {
			e.printStackTrace(); 
		}
	}
}
