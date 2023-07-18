package clinet_socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import clinet_socket.dto.RequestBodyDto;
import clinet_socket.dto.SendMessage;

public class ClientReceiver extends Thread {
	
	private Gson gson;

	
	@Override
	public void run() {
		gson = new Gson();
		
		SimpleGUIClient simpleGUIClient = SimpleGUIClient.getInstance();

		while(true) {
			try {
				// 서버로부터의 입력을 받기 위해 BufferedReader를 생성
				BufferedReader bufferedReader = 
						new BufferedReader(new InputStreamReader
								(SimpleGUIClient.getInstance().getSocket().getInputStream()));
				// 서버로부터의 메시지를 읽어옴
				String requestBody = bufferedReader.readLine();
				
				// 읽어온 메시지를 처리하는 메서드 호출
				requestController(requestBody);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	// 서버로부터 받은 메시지의 종류에 따라 처리하는 메서드를 선택하는 컨트롤러
	private void requestController(String requestBody) {
		
		// JSON 형식으로 받은 메시지에서 리소스 정보를 추출
		String resource = gson.fromJson(requestBody, RequestBodyDto.class).getResource();
		
		// 리소스에 따라 적절한 처리 메서드 호출
		switch(resource) {
			case "updateRoomList":
				updateRoomList(requestBody);
				break;
				
			case "showMessage":
				showMessage(requestBody);
				break;
			
			case "updateUserList":
				updateUserList(requestBody);
				break;
				
			case "chattingTextClear":
				chattingTextClear(requestBody);
				break;
				
            case "receiveWhisperMessage":
                receiveWhisperMessage(requestBody);
                break;
                		
		}
	}
	
	// <처리 메서드 ↓>
	
	//귓속말
	private void receiveWhisperMessage(String requestBody) {
	    TypeToken<RequestBodyDto<SendMessage>> typeToken = new TypeToken<>() {};
	    RequestBodyDto<SendMessage> requestBodyDto = gson.fromJson(requestBody, typeToken.getType());
	    SendMessage whisperMessage = requestBodyDto.getBody();

	    String fromUsername = whisperMessage.getFromUsername();
	    String toUsername = whisperMessage.getToUsername();
	    String messageBody = whisperMessage.getMessageBody();
	    
	    String whisperMessageContent = "[귓말] " + toUsername + " --> " + messageBody + "\n";
	    SimpleGUIClient.getInstance().getChattingTextArea().append(whisperMessageContent);
	}
	
	// 방 목록 업데이트 리스트
	private void updateRoomList(String requestBody) {
		List<String> roomList = (List<String>) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		SimpleGUIClient.getInstance().getRoomListModel().clear();
		SimpleGUIClient.getInstance().getRoomListModel().addAll(roomList);
	}

	// 메세지
	private void showMessage(String requestBody) {
		String messageContent = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		SimpleGUIClient.getInstance().getChattingTextArea().append("[전체] " + messageContent + "" + "\n");
	}
	
	//방 유저 업데이트 리스트
	private void updateUserList(String requestBody) {
	    List<String> usernameList = (List<String>) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
	    
	    // 방장 표시 추가
	    usernameList.set(0, usernameList.get(0) + "( 방장 )");
	    
	    // 메시지 입력 필드를 수정 가능하도록 설정하고 포커스 요청
	    SimpleGUIClient.getInstance().getMessageTextField().setEditable(true);
	    SimpleGUIClient.getInstance().getMessageTextField().requestFocus();
	    
	    // 유저 리스트 갱신
	    SimpleGUIClient.getInstance().getUserListModel().clear();
	    SimpleGUIClient.getInstance().getUserListModel().addAll(usernameList);
	    
	   
	    
	    }
	
	//채팅방 삭제
	private void chattingTextClear(String requestBody) {
		
		// 채팅창을 초기화
			SimpleGUIClient.getInstance().getChattingTextArea().setText("");
	}
}