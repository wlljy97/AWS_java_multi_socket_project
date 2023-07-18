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
	public void run() { 
		
		// Thread 시작메소드
	    gson = new Gson();
	    
	    try {
	        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream())); 

	        while (true) {
	            String requestBody = bufferedReader.readLine(); 
	            // 소켓에서 읽은 데이터를 기다리고, 읽은 데이터를 requestBody에 저장
	            
	            if (requestBody == null) {
	                System.out.println("클라이언트와 연결이 끊어졌습니다.");
	                break;
	            }
	            requestController(requestBody);
	            // run 메서드에서는 소켓으로부터 요청을 읽어들이고,
	            // 요청이 없을 때까지 계속해서 requestController 메서드를 호출하여 요청을 처리합니다.
	        }
	    } catch (SocketException e) { 
	    	// 소켓 예외 처리 : 클라이언트와의 연결이 끊어진 경우
	        System.out.println("프로그램을 종료합니다.");
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
	
	private void requestController(String requestBody) {
		// 클라이언트로 부터 받은 요청을 처리하는 메서드
		
		// 요청 내용에서 리소스를 파싱하여 처리할 메서드를 선택
		String resource = gson.fromJson(requestBody, RequestBodyDto.class).getResource();

		
		switch (resource) { 
		// 요청 타입에 따라 적절한 메서드를 호출하여 요청 처리
		
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
	
	// 메소드 들을 밑으로 빼둠
	
	// 클라이언트 접속
	private void connection(String requestBody) {
		// 클라이언트의 유저네임 정보를 받아와서 저장하고, 
		// 접속한 클라이언트에게 현재 방 목록을 전송
		
		username = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		List<String> roomNameList = new ArrayList<>();
		
		// 방 목록에서 방 이름을 추출하여 리스트에 저장
		SimpleGUIserver.roomList.forEach(room -> {
			roomNameList.add(room.getRoomName());
		});
		
		// 현재 방 목록을 클라이언트에게 전송
		RequestBodyDto<List<String>> updateRoomListRequestBodyDto = 
				new RequestBodyDto<List<String>>("updateRoomList", roomNameList);
		
		ServerSender.getInstance().send(socket, updateRoomListRequestBodyDto);
	}
	
	// 방 만들기
	private void creatRoom(String requestBody) {
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		// 새로운 방 객체를 생성, 방 목록에 추가
		Room newRoom = Room.builder()
			.roomName(roomName)	//받아온 roomName
			.owner(username) // 방장 설정
			.userList(new ArrayList<ConnectedSocket>()) // 사용자 목록 초기화
			.build();
		
		SimpleGUIserver.roomList.add(newRoom);
		
		List<String> roomNameList = new ArrayList<>();
		
		// 업데이트된 방 목록을 클라이언트에게 전송
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
		// 클라리언트의 요청으로 특정 방에 들어가고,
		// 해당 방의 사용자 목록을 업데이트하여 클라이언트에게 전송
		
		// 방 들어가기 전에 채팅창 초기화
		RequestBodyDto<String> chattingTextClearDto =
				new RequestBodyDto<String>("chattingTextClear", null);
		ServerSender.getInstance().send(socket, chattingTextClearDto);
		
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		SimpleGUIserver.roomList.forEach(room -> {
			if(room.getRoomName().equals(roomName)) {
				room.getUserList().add(this); // 자기 자신을 userList에 추가
				
				List<String> usernameList = new ArrayList<>();
				
				// 업데이트 된 사용자 목록을 클라이언트에게 전송
				room.getUserList().forEach(con -> {
					usernameList.add(con.username);
				});
				
				room.getUserList().forEach(connectedSocket -> {
					RequestBodyDto<List<String>> updateUserListDto = new RequestBodyDto<List<String>> ("updateUserList", usernameList);
					
					RequestBodyDto<String> joinMessageDto = new RequestBodyDto<String>("showMessage", username + " 님이 들어왔습니다.");
					
					// 'userListInitSelectedDto'는 값이 없는 새로운 'userListInitSelected' 메시지를 만들어서 
					// 클라이언트의 사용자 목록을 초기화하는 메시지
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
		// 클라이언트가 보낸 메세지를 방의 다른 사용자들에게 전송
		
		TypeToken<RequestBodyDto<SendMessage>> typeToken = new TypeToken<>() {};
		
		RequestBodyDto<SendMessage> requestBodyDto = gson.fromJson(requestBody, typeToken.getType());
		SendMessage sendMessage = requestBodyDto.getBody();
		
		SimpleGUIserver.roomList.forEach(room -> {
			if(room.getUserList().contains(this)) {
				room.getUserList().forEach(connectedSocket -> {
					// 메세지를 형식에 맞춰 방의 다른 사용자들에게 전송
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
		// 클라이언트가 특정 방에서 나가면 해당 방의 사용자 목록을 업데이트하고,
		// 방에 사용자가 남아 있는지 체크하여 방 목록을 업데이트하여 클라이언트에게 전송
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
	
		for(int i = 0; i < SimpleGUIserver.roomList.size(); i++) {
			// 현재 방 객체를 roomList에서 가져옴
			Room room = SimpleGUIserver.roomList.get(i);
			
			// 방의 이름이 지정된 'roomName'과 일치하는지 확인
			if(room.getRoomName().equals(roomName)) { 
				
				// 현재 사용자가 방의 userLits에 존재하면,
				// 해당 사용자를 userList에서 제거
				room.getUserList().remove(this);
				
				// 방에 다른 사용자들이 아직 남아 있는지 확인
				if(room.getUserList().size() != 0) {
					
					// 'userList'라는 새 리스트를 생성하여 방에 있는 사용자들의 사용자명을 저장
					List<String> usernameList = new ArrayList<>();
					
					// 방의 usetList에 있는 모든 연결된 소켓들(사용자들)을 반복문 순회
					for(ConnectedSocket connectedSocket : room.getUserList()) {
						// 각 연결된 소켓의 사용자명을 'usernameList'에 추가
						usernameList.add(connectedSocket.username);
					}
					
					// 방의 userList에 있는 모든 연결된 소켓들(사용자들)을 다시 반복문으로 순회
					for(ConnectedSocket con : room.getUserList()) {
					// 업데이트된 사용자 목록을 방의 다른 사용자들에게 전송
						RequestBodyDto<List<String>> updateUserListDto =
								new RequestBodyDto<List<String>>("updateUserList", usernameList);
					// 방에 있는 다른 사용자들에게 현재 사용자가 방을 나갔음을 알리기 위해 새로운 RequestBodyDto를 생성
						RequestBodyDto<String> roomExitMessageDto = 
								new RequestBodyDto<String>("showMessage", username + " 님이 나갔습니다.");
						
						// 'updateUserListDto'를 connectedSocket(사용자)에게 보내서 사용자 목록을 업데이트
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
					// 방에 사용자가 남아있지 않다면 방 목록에서 제거하고,
					// 업데이트된 방 목록을 클라이언트 에게 전송
					SimpleGUIserver.roomList.remove(i);
					
					List<String> roomNameList = new ArrayList<>();
					
					// 업데이트된 SimpleGUIserver의 roomList를 순회하며 남은 방들의 이름을 'roomNameList'에 추가
					for(Room newRoom : SimpleGUIserver.roomList) {
						roomNameList.add(newRoom.getRoomName());
					}
					
					// 'roomNameList'를 사용하여 모든 연결된 클라이언트에게 방 목록을 업데이트하는 데 사용될 새로운 RequestBodyDto를 생성
					RequestBodyDto<List<String>> updateRoomListDto = new RequestBodyDto<List<String>>("updateRoomList", roomNameList);
					
					// SimpleGUIserver의 모든 연결된 소켓(사용자)들에게 'updateRoomListDto'를 보냄
					for(ConnectedSocket connectedSocket : SimpleGUIserver.connectedSocketList) {
						ServerSender.getInstance().send(connectedSocket.socket, updateRoomListDto);
					}
					// 루프 종료
					break;
				}
			}
		}
	}

	//귓속말
	private void sendWhisperMessage(String requestBody) {
		// 클라이언트가 보낸 귓속말 메세지를 해당 유저에게 전송하고,
		// 자신에게도 메세지를 보냄
		
		// TypeToken을 사용하여 역직렬화를 위한 타입 정보를 제공함 (Gson은 제네릭 타입을 직접 역직렬화할 수 없음
	    TypeToken<RequestBodyDto<SendMessage>> typeToken = new TypeToken<>() {};
	    
	    // 주어진 typeToken을 사용하여 JSON 요청 본문을 RequestBodyDto<SendMessage> 객체로 역직렬화
	    RequestBodyDto<SendMessage> requestBodyDto = gson.fromJson(requestBody, typeToken.getType());
	    
	    // RequestBodyDto<SendMessage> 객체에서 실제 SendMessage 객체를 가져옴
	    SendMessage sendMessage = requestBodyDto.getBody();
	    
	    // SendMessage 객체로부터 필요한 필드를 추출
	    String fromUsername = sendMessage.getFromUsername();
	    String toUsername = sendMessage.getToUsername();
	    String messageBody = sendMessage.getMessageBody();
	    
	    // 대상 소켓 (메시지의 수신자)와 방의 멤버 목록을 저장할 변수를 초기화
	    ConnectedSocket targetSocket = null; // 메세지를 보낼 대상 소켓
	    List<ConnectedSocket> roomMembers = new ArrayList<>(); // 방의 연결된 소켓 목록을 저장할 리스트
	    
	    // 연결된 소켓 목록을 순화하며 대상 소켓 (메세지의 수신자)를 찾음
	    for (ConnectedSocket connectedSocket : SimpleGUIserver.connectedSocketList) {
	        if (connectedSocket.username.equals(toUsername)) {
	        	// 연결된 소켓의 사용자명이 수신자의 사용자명과 일치할 경우,
	        	// 대상 소켓으로 해당 연결된 소켓을 저장
	            targetSocket = connectedSocket;
	        }
	    }

	    	if (fromUsername.equals(toUsername)) {
	    	// 자기 자신에게 귓속말을 보낼 수 없음
	        String errorMessage = "자신에게 귓속말을 보낼 수 없습니다.";
	        RequestBodyDto<String> errorDto = new RequestBodyDto<>("errorMessage", errorMessage);
	        ServerSender.getInstance().send(socket, errorDto);
	    	} else {
	    		
	    	// 귓속말 메세지를 보내는 대상과 자신에게 메세지를 전송
	        RequestBodyDto<SendMessage> whisperMessageDto = new RequestBodyDto<>("receiveWhisperMessage", sendMessage);
	        ServerSender.getInstance().send(targetSocket.socket, whisperMessageDto);
	        
		    // 새로운 RequestBodyDto를 생성하고, "receiveWhisperMessage"라는 타입의 메시지를 담아 sendMessage 객체로 초기화
		    // 이 메시지는 자신에게 귓속말을 받았음을 알리기 위해 사용
	        RequestBodyDto<SendMessage> selfWhisperMessageDto = new RequestBodyDto<>("receiveWhisperMessage", sendMessage);
	        
	        // selfWhisperMessageDto의 바디 부분을 가져와서, 해당 메시지의 송신자 이름을 'username'으로 설정
	        // 즉, 귓속말을 보낸 사용자의 이름을 송신자 이름으로 설정
	        selfWhisperMessageDto.getBody().setFromUsername(username);
	        
		     // ServerSender 클래스의 getInstance() 메서드를 통해 서버의 인스턴스를 얻고, 해당 소켓(socket)에 selfWhisperMessageDto를 보냄
		     // 이로써, 자신에게 귓속말이 도착했음을 알리는 메시지가 해당 사용자의 클라이언트로 전송
	        ServerSender.getInstance().send(socket, selfWhisperMessageDto);
	        
	    }
	}
}