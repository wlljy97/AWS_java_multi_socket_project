package server_socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import server_socket.dto.RequestBodyDto;
import server_socket.dto.SendMessage;
import server_socket.entity.Room;



@RequiredArgsConstructor
@Data
public class ConnectedSocket extends Thread { 
	// Thread 클래스를 상속받아 멀티스레드로 동작
	// 이렇게 함으로써 여러 클라이언트와 동시에 통신할 수 있습니다.
	
	// ConnectedSocket 클래스는 클라이언트와의 통신을 담당하여, 다양한 요청을 처리합니다.
	// 서버 소켓을 통해 클라이언트와 통신하는 기능을 구현한 클래스
	
	private final Socket socket; // 멤버 변수는 클라이언트와의 연결을 나타내는 소켓 객체입니다.
	
	private Gson gson; 
	// Gson은 JSON 형식의 데이터를 자바 객체로 변환하거나 자바 객체를 JSON 형식으로 변환하는 기능을 제공하는 라이브러리입니다.
	
	// 멤버 변수 (요청 처리에 사용되는)
	private String username;
	private SendMessage toUsername;
	private String fromUsername;
	private String whisperMessage;
	private String room;
	private Object roomName; 
	
	@Override
	public void run() { // Thread 시작메소드
	    gson = new Gson();
	    
	    try {
	        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream())); 

	        while (true) {
	            String requestBody = bufferedReader.readLine(); // 읽는것을 기다리고 있다. 이 데이터를

	            if (requestBody == null) {
	                System.out.println("클라이언트와 연결이 끊어졌습니다.");
	                break;
	            }
	            requestController(requestBody);
	            // run 메서드에서는 소켓으로부터 요청을 읽어들이고,
	            // 요청이 없을 때까지 계속해서 requestController 메서드를 호출하여 요청을 처리합니다.
	        }
	    } catch (SocketException e) { // 종료의 예외처리를 한다.
	        System.out.println("프로그램을 종료합니다.");
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
	
	private void requestController(String requestBody) {
		
		String resource = gson.fromJson(requestBody, RequestBodyDto.class).getResource();

		
		switch (resource) { // 요청 하는 것에 따라 실행된다.
			case "connection":
				connection(requestBody);
				break;
		
			case "createRoom":
				creatRoom(requestBody);
				break;
		
			case "join":
				join(requestBody);
				break;
				
			case "sendMessage":
				sendMessage(requestBody);
				break;
				
			case "roomExit":
				roomExit(requestBody);
				break;
				
            case "sendWhisperMessage":
            	sendWhisperMessage(requestBody);
                break;
		}
	}
	
	// 메소드 들을 뺀다.
	
	// 클라이언트 접속
	private void connection(String requestBody) {
		username = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		List<String> roomNameList = new ArrayList<>();
		
		SimpleGUIserver.roomList.forEach(room -> {
			roomNameList.add(room.getRoomName());
		});
		
		RequestBodyDto<List<String>> updateRoomListRequestBodyDto = 
				new RequestBodyDto<List<String>>("updateRoomList", roomNameList);
		
		ServerSender.getInstance().send(socket, updateRoomListRequestBodyDto);
	}
	
	// 방 만들기
	private void creatRoom(String requestBody) {
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		
		Room newRoom = Room.builder()
			.roomName(roomName)	//받아온 roomName
			.owner(username) // 방장
			.userList(new ArrayList<ConnectedSocket>())
			.build();
		
		SimpleGUIserver.roomList.add(newRoom);
		
		List<String> roomNameList = new ArrayList<>();
		
		SimpleGUIserver.roomList.forEach(room -> {
			roomNameList.add(room.getRoomName());
		});
		
		RequestBodyDto<List<String>> updateRoomListRequestBodyDto = 
				new RequestBodyDto<List<String>>("updateRoomList", roomNameList);
		
		SimpleGUIserver.connectedSocketList.forEach(con -> {
			ServerSender.getInstance().send(con.socket, updateRoomListRequestBodyDto);
		});
	}
	
	// 방 들어가기
	private void join(String requestBody) {
		
		// 방 나갔을시 채팅창 초기화
		RequestBodyDto<String> chattingTextClearDto =
				new RequestBodyDto<String>("chattingTextClear", null);
		ServerSender.getInstance().send(socket, chattingTextClearDto);
		
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		SimpleGUIserver.roomList.forEach(room -> {
			if(room.getRoomName().equals(roomName)) {
				room.getUserList().add(this);
				
				List<String> usernameList = new ArrayList<>();
				
				room.getUserList().forEach(con -> {
					usernameList.add(con.username);
				});
				
				room.getUserList().forEach(connectedSocket -> {
					RequestBodyDto<List<String>> updateUserListDto = new RequestBodyDto<List<String>> ("updateUserList", usernameList);
					RequestBodyDto<String> joinMessageDto = new RequestBodyDto<String>("showMessage", username + " 님이 들어왔습니다.");
					RequestBodyDto<Object> userListInitSelectedDto = new RequestBodyDto<Object>("userListInitSelected", null);
					
					ServerSender.getInstance().send(connectedSocket.socket, updateUserListDto);
					try {
						sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					ServerSender.getInstance().send(connectedSocket.socket, joinMessageDto);
				});
			}
		});
	}
	
	// 메세지 보내기
	private void sendMessage(String requestBody) {
		TypeToken<RequestBodyDto<SendMessage>> typeToken = new TypeToken<>() {};
		
		RequestBodyDto<SendMessage> requestBodyDto = gson.fromJson(requestBody, typeToken.getType());
		SendMessage sendMessage = requestBodyDto.getBody();
		
		SimpleGUIserver.roomList.forEach(room -> {
			if(room.getUserList().contains(this)) {
				room.getUserList().forEach(connectedSocket -> {
					RequestBodyDto<String> dto = 
							new RequestBodyDto<String>("showMessage", 
									sendMessage.getFromUsername() + " : " + sendMessage.getMessageBody());
					
					ServerSender.getInstance().send(connectedSocket.socket, dto);
				});
			}
		});
	}
	
	// 방 나가기
	private void roomExit(String requestBody) {
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		for(int i = 0; i < SimpleGUIserver.roomList.size(); i++) {
			Room room = SimpleGUIserver.roomList.get(i);
			
			if(room.getRoomName().equals(roomName)) { 
				room.getUserList().remove(this);
				
				if(room.getUserList().size() != 0) {
					List<String> usernameList = new ArrayList<>();
					
					for(ConnectedSocket connectedSocket : room.getUserList()) {
						usernameList.add(connectedSocket.username);
					}
					
					for(ConnectedSocket con : room.getUserList()) {
						RequestBodyDto<List<String>> updateUserListDto =
								new RequestBodyDto<List<String>>("updateUserList", usernameList);
						RequestBodyDto<String> roomExitMessageDto = 
								new RequestBodyDto<String>("showMessage", username + " 님이 나갔습니다.");
						
						ServerSender.getInstance().send(con.socket, updateUserListDto);
						usernameList.get(0);
						
						try {
							sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						ServerSender.getInstance().send(con.socket, roomExitMessageDto);
					}
				} else {
					SimpleGUIserver.roomList.remove(i);
					
					List<String> roomNameList = new ArrayList<>();
					
					for(Room newRoom : SimpleGUIserver.roomList) {
						roomNameList.add(newRoom.getRoomName());
					}
					
					RequestBodyDto<List<String>> updateRoomListDto = new RequestBodyDto<List<String>>("updateRoomList", roomNameList);
					for(ConnectedSocket connectedSocket : SimpleGUIserver.connectedSocketList) {
						ServerSender.getInstance().send(connectedSocket.socket, updateRoomListDto);
					}
					break;
				}
			}
		}
	}

	//귓속말
	private void sendWhisperMessage(String requestBody) {
	    TypeToken<RequestBodyDto<SendMessage>> typeToken = new TypeToken<>() {};
	    RequestBodyDto<SendMessage> requestBodyDto = gson.fromJson(requestBody, typeToken.getType());
	    SendMessage sendMessage = requestBodyDto.getBody();

	    String fromUsername = sendMessage.getFromUsername();
	    String toUsername = sendMessage.getToUsername();
	    String messageBody = sendMessage.getMessageBody();

	    ConnectedSocket targetSocket = null;
	    List<ConnectedSocket> roomMembers = new ArrayList<>();

	    for (ConnectedSocket connectedSocket : SimpleGUIserver.connectedSocketList) {
	        if (connectedSocket.username.equals(toUsername)) {
	            targetSocket = connectedSocket;
	        }
	    }

	    	if (fromUsername.equals(toUsername)) {
	        String errorMessage = "자신에게 귓속말을 보낼 수 없습니다.";
	        RequestBodyDto<String> errorDto = new RequestBodyDto<>("errorMessage", errorMessage);
	        ServerSender.getInstance().send(socket, errorDto);
	    	} else {
	        RequestBodyDto<SendMessage> whisperMessageDto = new RequestBodyDto<>("receiveWhisperMessage", sendMessage);
	        ServerSender.getInstance().send(targetSocket.socket, whisperMessageDto);

	        RequestBodyDto<SendMessage> selfWhisperMessageDto = new RequestBodyDto<>("receiveWhisperMessage", sendMessage);
	        selfWhisperMessageDto.getBody().setFromUsername(username);
	        ServerSender.getInstance().send(socket, selfWhisperMessageDto);
	        
	    }
	}
}