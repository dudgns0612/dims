#◎DIMS
  기숙사 정보 관리시스템(Dormitory Information Management System)
  기숙사 내에서 사감 및 관리자와 사생들간의 행하여 지는 일들을 처리하여주는 프로그램.

#◎개발도구
   Java SE Development Kit 8, Eclipse, JavaFX Scene Builder 2.0, Xampp v.3.22
   >###Download  
    JavaFX Scene Builder - [JavaFX Scene Builder 2.0]
      (http://www.oracle.com/technetwork/java/javafxscenebuilder-1x-archive-2199384.html)
    Xampp - [Xampp v 3.22]
      (https://www.apachefriends.org/download.html) 
      
#◎실행순서
   1. database실행 : xampp실행 후 mysql start 실행
   2. mysql일 성공적으로 실행 되면 serverControlPanel.java실행 Start버튼 클릭
   3. serverControlPanel.java이 성공적으로 실행 되면 clients.Start.java실행
   4. 서버ip 입력 후 서버연결이 완료되면 로그인화면 창이 실행됨.
   5. db정보에 따라 관리자 및 학생클라이언트 실행

#◎시스템구성

###* 기본설정
>    tools.Statics.java        : Network 설정을 위한 정보, UI fxml경로, 사용할 db와 db연결정보 등으로 구성
    
###* 통신
    tools.NetworkProtocol.java  : 서버와 클라이언트간의 통신을 위한 Protocol들로 구성
    files.FileProtocol.java       : 파일서버와 클라이언트간의 통신을 위한 Protocol들로 구성

* 서버                       
    servers.ServerControlPanel.java : 서버 컨트롤 패널 실행 서버통신상태 및 연결상태 
                                      정보 수록
    servers.DIMS_Server.java : 클라이언트와 통신을 하며 NetworkProtocol의 통신규약에 따라 수행함
    files.DIMSFileServer.java  : 서버로부터 각 클라이언트들의 파일전송 및 전달등의 관리를 수행함

* 관리자 클라이언트
    clients.MainApplication.java : 메인 클라이언트 프로그램 실행(공통 db정보에 따라 다름)
    clients.controllers.AdministratorMainController.java : 관리자 메인 페이지 컨트롤러
  
* 학생 클라이언트
    clients.MainApplication.java : 메인 클라이언트 프로그램 실행(공통 db정보에 따라 다름)
    clients.controllers.StudentMain.java : 학생 메인 페이지 컨트롤러
 
* Database
    databases.DatabaseHandler : 서버와 서버로컬의 db와 연결, query실행 및 결과 전달

* UI
    cilents.SceneManager.java : 실행하는 동안에 필요에 따라 화면전환, 컨트롤러 정보 전달등 UI설정을 수행함     
    clients.CustomDialog.java : 실행하는 동안에 전달,입력,경고등 필요에 따라 사용 하는 다이얼로그
    client.ui, serverui, resources : client fxml, server fxml, UI에 필요한 이미지/아이콘을 담고 있는 것
