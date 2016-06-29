package tools;

public class Statics {

	/* Application 설정 */
	public static final int COMMIT = 0;	
	public static final int ERROR = -2;
		
	/* UI 설정 */
		/* UI fxml 파일이름 목록 */
		public static final String LOGIN_WINDOW_FXML = "/clients/ui/LoginUI.fxml";		// 가장 첫화면, 로그인화면
		public static final String PASSWORD_FIND_FXML = "/clients/ui/PasswordFind.fxml";	// 비밀번호 찾기 화면
		public static final String ADMIN_MAIN_FXML = "/clients/ui/AdministratorMain.fxml";  // 관리자 메인 페이지 화면
		public static final String REGISTER_FXML = "/clients/ui/MemberJoin.fxml";  // 회원가입 화면
		public static final String CHECK_MESSAGE_FXML = "/clients/ui/CheckMessageDialog.fxml";	// 메세지확인 다이얼로그
		public static final String ALERT_DIALOG = "/clients/ui/MessageDialogUI.fxml";	// 알림창
		public static final String SCHEDULE_CREATE_DIALOG = "/clients/ui/ScheduleCreateDialog.fxml";	// 새일정
		public static final String CHECK_STUDENT_FXML = "/clients/ui/StudentInfoDialog.fxml"; //신상정보 상세내용 화면
	    public static final String CHECK_WEABAK_FXML = "/clients/ui/CheckWeabakDialog.fxml"; //외박 정보 상세내용 화면
		public static final String PLUS_MINUS_ASSIGN_FXML ="/clients/ui/PlusMinusAssignDialog.fxml"; //신상정보 부여 화면
	    public static final String OK_CANCLE_DIALOG = "/clients/ui/OK_CANCEL_DialogUI.fxml";
	    public static final String INPUT_DIALOG = "/clients/ui/InputDialogUI.fxml";
	    public static final String STUDENT_MAIN_FXML = "/clients/ui/StudentMain.fxml";
		public static final String STUDENT_REAUTH_DIALOG_FXML = "/clients/ui/ReAuthDialog.fxml";
	    public static final String LOADING_DIALOG_FXML = "/clients/ui/LoadingDialog.fxml";
		public static final String CATEGORY_DELETE_DIALOG_FXML = "/clients/ui/CategoryDeleteDialog.fxml";
	    public static final String ADD_SUBMIT_DIALOG_FXML = "/clients/ui/AddSubmitDialog.fxml";
	    public static final String EMAIL_SEND_FXML = "/clients/ui/EmailSendDialog.fxml";
	    
		/* UI 윈도우 창 이름 목록 */
		public static final String LOGIN_WINDOW_TITLE = "DIMS - 로그인";					
		public static final String PASSWORD_FIND_TITLE = "DIMS - 비밀번호 찾기";			
		public static final String ADMIN_MAIN_TITLE = "DIMS - 관리자용 클라이언트";
		public static final String REGISTER_TITLE = "DIMS - 회원가입";  // 관리자 메인 페이지 화면
		public static final String CHECK_MESSAGE_TITLE = "DIMS - 메세지 확인";
		public static final String ALERT_DIALOG_TITLE = "DIMS - 알림";
		public static final String SCHEDULE_CREATE_DIALOG_TITLE = "DIMS - 새 일정";
		public static final String STUDENT_TITLE = "DIMS - 학생 신상 정보 확인";
	    public static final String WEABAK_TITLE = "DIMS - 외박 승인 정보 확인";
	    public static final String PLUS_MINUS_ASSIGN_TITLE = "DIMS - 상벌점 부여 정보 확인";
		public static final String OK_CANCEL_DAILOG_TITLE = "DIMS - 확인";
	    
		public static final String STUDENT_MAIN_TITLE = "DIMS - 학생용 클라이언트";
		public static final String STUDENT_REAUTH_DIALOG_TITLE = "DIMS - 재인증 요청";
		public static final String LOADING_DIALOG_TITLE = "DIMS - 파일 다운로드 진행중...";
		public static final String INPUT_DIALOG_TITLE = "DIMS - 입력하세요.";
		
		public static final String CATEGORY_DELETE_DIALOG_TITLE = "DIMS - 카테고리 삭제";
		public static final String ADD_SUBMIT_DIALOG_TITLE = "DIMS - 새 제출서류 등록";
	    public static final String EMAIL_SEND_TITLE = "DIMS - 이메일 보내기 ";
		
	/* Network 설정 */
	public static String DIMS_SERVER_IP_ADDRESS = "localhost";				// 서버 IP 주소
	public static final int DIMS_SERVER_PORT_NUMBER = 8080;
	
	public static String DIMS_FILE_SERVER_IP_ADDRESS = "localhost";				// 서버 IP 주소
	public static final int DIMS_FILE_SERVER_PORT_NUMBER = 9090;
	
		/* Networking - error */
		public static final int CONNECT_ERROR = -1;							// 서버 접속 실패
		public static final int UNKNOWN_HOST_ERROR = -3;					// 호스트 주소 조회 실패
	
	/* Server */	
	public static String DEFAULT_LOG_BACKUP_PATH = "C:\\DIMS\\Backup\\";	
		
		
	/* Database */
	public static final String DASEBASE_DRIVER = "org.gjt.mm.mysql.Driver";	// 기본 데이터베이스 드라이버 패키지
	public static final String DEFAULT_USE_DATABASE = "db2";				// 기본 사용 데이터베이스
	public static final String DEFAULT_DATABASE_HOST_ID = "root";			// 기본 데이터베이스 접속 아이디
	public static final String DEFAULT_DATABASE_HOST_PASSWORD = "1234";		// 기본 데이터베이스 접속 비밀번호
	
	/* User Data */
	public static final String DEFAULT_USER_DATA_DIRECTORY = "C:\\\\DIMS\\\\Userdata\\\\";	// 프로필이미지
	public static final String DEFAULT_MOVIE_DATA_DIRECTORY = "C:\\\\DIMS\\\\Movie\\\\";	// 영상
	public static final String DEFAULT_SUBMITTED_DATA_DIRECTORY = "C:\\\\DIMS\\\\SubmittedData\\\\";	// 제출데이터
	public static final String DEFAULT_DIMS_DIRECTORY = "C:\\\\DIMS\\\\";
	public static final String DEFAULT_DOWNLOAD_DIRECTORY = "C:\\DIMS Download\\";
}
