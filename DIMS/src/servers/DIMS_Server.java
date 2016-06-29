package servers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.orsoncharts.util.json.JSONArray;
import com.orsoncharts.util.json.JSONObject;
import clients.customcontrols.CalendarObject;
import clients.customcontrols.CalendarObject.CalendarDataManager;
import databases.DatabaseHandler;
import files.DIMSFileServer;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import servers.controllers.ServerMainController;
import tools.MailProperties;
import tools.MailProperty;
import tools.MailingService;
import tools.NetworkProtocols;
import tools.Statics;
import tools.Toolbox;

public class DIMS_Server {
	
	private ServerSocket server;
	private static boolean SERVER_RUN = false;
	private boolean PRINT_LOG = true;
	private DatabaseHandler handler;
	private ArrayList<ConnectedClient> clients;
	DIMSFileServer fileServer;
	ServerMainController controlPanel;
	ListView<HBox> logs, requests, connected;
	
	public DIMS_Server(ServerMainController controlPanel)
	{
		clients = new ArrayList<ConnectedClient>();
		this.controlPanel = controlPanel;
		//new Controller().start();
		//serverOpen();
		
	}
	
	public void serverClose()
	{
		try {
			fileServer.serverClose();
			server.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		SERVER_RUN = false;
	}
	
	public void serverOpen()
	{
		try
		{
			server = new ServerSocket(8080);
			if(PRINT_LOG)
			{
				System.out.println("[Server] ���� ����");
				
				controlPanel.Log("M", "Start server."+InetAddress.getLocalHost().getHostAddress()+"::"+8080);
				
				if(new File(Statics.DEFAULT_DIMS_DIRECTORY).exists()==false)
				{
					System.out.println("[Server] ���丮�� �ڵ����� �����մϴ�.");
					controlPanel.Log("M", "Create required directorys");
					System.out.println("[Server] ���� : "+Statics.DEFAULT_DIMS_DIRECTORY);
					controlPanel.Log("M", "Path::"+Statics.DEFAULT_DIMS_DIRECTORY);
					new File(Statics.DEFAULT_DIMS_DIRECTORY).mkdir();
					System.out.println("[Server] ���� : "+Statics.DEFAULT_MOVIE_DATA_DIRECTORY);
					controlPanel.Log("M", "Path::"+Statics.DEFAULT_MOVIE_DATA_DIRECTORY);
					new File(Statics.DEFAULT_MOVIE_DATA_DIRECTORY).mkdir();
					System.out.println("[Server] ���� : "+Statics.DEFAULT_USER_DATA_DIRECTORY);
					controlPanel.Log("M", "Path::"+Statics.DEFAULT_USER_DATA_DIRECTORY);
					new File(Statics.DEFAULT_USER_DATA_DIRECTORY).mkdir();
					System.out.println("[Server] ���� : "+Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY);
					controlPanel.Log("M", "Path::"+Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY);
					new File(Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY).mkdir();
					controlPanel.Log("M", "Path::"+Statics.DEFAULT_LOG_BACKUP_PATH);
					new File(Statics.DEFAULT_LOG_BACKUP_PATH).mkdir();
				}
			}
			handler = new DatabaseHandler();
			if(PRINT_LOG) System.out.println("[Server] �����ͺ��̽��� ���� �õ�...");
			controlPanel.Log("M", "Try to connect database. Use::"+Statics.DASEBASE_DRIVER);
			int result = handler.connect();
			
			switch(result)
			{
			case DatabaseHandler.DRIVER_INIT_ERROR : if(PRINT_LOG)System.out.println("[Server] JDBC����̹� ������ �߸��ƽ��ϴ�.");
													 controlPanel.Log("E", "Fail to set up JDBC Driver.");
													 controlPanel.off();	
													 return;
			case DatabaseHandler.LOGIN_FAIL_ERROR : if(PRINT_LOG)System.out.println("[Server] �����ͺ��̽��� �α������� ���߽��ϴ�. ���̵� �Ǵ� ��й�ȣ�� Ȯ���ϼ���"); 
													controlPanel.Log("E", "Cannot login to database");
													controlPanel.off();
			    									return;
			case DatabaseHandler.COMPLETE : if(PRINT_LOG)System.out.println("[Server] ���� ����");
											SERVER_RUN = true;
											controlPanel.Log("M", "Connection commit.");
											controlPanel.on();
			}
			
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		if(PRINT_LOG)System.out.println("[Server] Waiter ������ ����");
		new Thread(()->{
			controlPanel.Log("M", "Start waiting clients.");
		}).start();
		new Waiter().start();
		fileServer = new DIMSFileServer(9090, controlPanel);
		controlPanel.Log("F", "Start file handling server.");		
	}
	
	class Waiter extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				while(SERVER_RUN)
				{
					if(PRINT_LOG)System.out.println("\t[Waiter] Ŭ���̾�Ʈ�� ��ٸ��� ���Դϴ�...");
					controlPanel.Log("W", "Waiting client...");
					Socket newClient = server.accept();
					controlPanel.Log("W", "New client has connected, start connector::"+newClient.getInetAddress().getHostAddress());
					if(PRINT_LOG)System.out.println("\t[Waiter] ���ο� Ŭ���̾�Ʈ ����, Connector ������ ����");
					new Connector(newClient).start();
				}
			}
			catch (IOException e)
			{
				//e.printStackTrace();
				controlPanel.Log("E", e.getMessage());
			}
			finally {
				SERVER_RUN = false;
			}
		}
	}
	
	class Connector extends Thread
	{
		Socket client;
		String userName = "unknown";
		String userIdentify = "";
		String userGrade = "";
		ObjectInputStream fromClient;
		ObjectOutputStream toClient;
		String clientIP = "";

		Connector(Socket client)
		{
			this.client = client;
			clientIP = client.getInetAddress().getHostAddress();
			controlPanel.Log("C", "Client IP address::"+clientIP);
			System.out.println(clientIP);
			try
			{
				fromClient = new ObjectInputStream(client.getInputStream());
				toClient = new ObjectOutputStream(client.getOutputStream());
				if(PRINT_LOG)System.out.println("\t\t[Connector-"+userName+"] ��Ʈ�� ���� �Ϸ�");
				controlPanel.Log("C", "Connect complete to "+userName+"-"+clientIP);
			}
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public void run()
		{
			try
			{
				while(SERVER_RUN)
				{
					JSONObject request = null;
					try
					{
						request = (JSONObject)fromClient.readObject();
						System.out.println("��û : "+request);
						if(request==null)
						{
							if(PRINT_LOG)System.out.println("\t\t["+userName+"] ����� ����, ������ ����");
							break;
						}
						
					}
					catch(ClassNotFoundException e)
					{
						JSONObject respond = new JSONObject();
						respond.put("type", NetworkProtocols.INVALID_REQUEST_ERROR);
						sendProtocol(respond);
						continue;
					}
					
					String type = request.get("type").toString();
					if(PRINT_LOG)System.out.println("\t\t["+userName+"] request type : "+type);
					
					if(type.equals(NetworkProtocols.LOGIN_REQUEST))
					{
						String reqID = request.get("id").toString();
						String reqPassword = request.get("password").toString();
						
						if(PRINT_LOG)System.out.println("\t\t\t[request-"+userName+"] LOGIN_REQUEST, ID : "+reqID+", Password : "+reqPassword);
						controlPanel.request("LOGIN_REQUEST", reqID+"-"+clientIP, "-----", request);
						ResultSet s = handler.excuteQuery("select * from ����� where �й�='"+reqID+"'");
						
						if(s.next())
						{
							String realPassword = s.getString("��й�ȣ");
							if(realPassword.equals(reqPassword))
							{
								userName = s.getString("�̸�");
								userIdentify = reqID;
								userGrade = s.getString("����ڵ��");
								if(PRINT_LOG)System.out.println("\t\t\t[Server] LOGIN_ACCEPT");
								JSONObject respond = new JSONObject();
								respond.put("type", NetworkProtocols.LOGIN_ACCEPT);
								respond.put("user_level", s.getString("����ڵ��"));
								sendProtocol(respond);
								controlPanel.respond("LOGIN_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
								/* �� ��������  HashMap�� �־������ */
								clients.add(new ConnectedClient(userIdentify, userName, s.getString("����ڵ��").toString(), toClient));
								controlPanel.connect(clientIP, userName, userGrade, "login");
							}
							else
							{
								if(PRINT_LOG)System.out.println("\t\t\t[Server] LOGIN_DENY, Incorrect Password, requset : "+reqPassword+", in database : "+realPassword);
								JSONObject respond = new JSONObject();
								respond.put("type", NetworkProtocols.LOGIN_DENY);
								controlPanel.respond("LOGIN_RESPOND", userIdentify+"-"+clientIP, "DENY", respond);
								sendProtocol(respond);
							}
						}
						else
						{
							if(PRINT_LOG)System.out.println("\t\t\t[Server] LOGIN_DENY, Not Exist User requset : "+reqID);
							JSONObject respond = new JSONObject();
							respond.put("type", NetworkProtocols.LOGIN_DENY);
							controlPanel.respond("LOGIN_RESPOND", userIdentify+"-"+clientIP, "DENY", respond);
							sendProtocol(respond);
						}
						s.close();
					}
					else if(type.equals(NetworkProtocols.ID_DUP_CHECK_REQUEST))
					{
						String reqID = request.get("id").toString();
						if(PRINT_LOG)System.out.println("\t\t\t[request-"+userName+"] ID_DUP_CHECK_REQUEST, "+reqID);
						controlPanel.request("ID_DUP_CHECK_REQUEST", userIdentify+"-"+clientIP, "-----",request);
						ResultSet s = handler.excuteQuery("select * from ����� where �й�='"+reqID+"'");
						
						
						if(s.next())
						{
							if(PRINT_LOG)System.out.println("\t\t\t[Server] ID_DUP_RESPOND_DENY");
							sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ID_DUP_RESPOND_DENY));
							controlPanel.respond("ID_DUP_RESPOND", userIdentify+"-"+clientIP, "DENY",Toolbox.createJSONProtocol(NetworkProtocols.ID_DUP_RESPOND_DENY));
						}
						else
						{
							if(PRINT_LOG)System.out.println("\t\t\t[Server] ID_DUP_RESPOND_OK");
							controlPanel.respond("ID_DUP_RESPOND", userIdentify+"-"+clientIP, "COMMIT",Toolbox.createJSONProtocol(NetworkProtocols.ID_DUP_RESPOND_DENY));
							sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ID_DUP_RESPOND_OK));
						}
						
						s.close();
					}
					else if(type.equals(NetworkProtocols.EXIT_REQUEST))
					{
						sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.EXIT_RESPOND));
						controlPanel.request("EXIT_REQUEST", userIdentify+"-"+clientIP, "UI",request);
						while(true)
						{
							sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.RECIEVE_READY));
							JSONObject r = null;
							try
							{
								r = (JSONObject)fromClient.readObject();
								System.out.println(r);
							}
							catch (ClassNotFoundException e)
							{
								e.printStackTrace();
							}
							
							if(r.get("type").equals(NetworkProtocols.RECIEVE_READY_OK))
							{
								if(r.get("request-view")!=null)
								{
									JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.PASSWORD_FIND_QUESTION_LIST);
									ResultSet rs = handler.excuteQuery("select �������� from ��й�ȣã��_�������");
									JSONArray data = new JSONArray();
									while(rs.next())
									{
										data.add(rs.getString("��������"));
									}
									respond.put("data", data);
									sendProtocol(respond);
								}
								else
								{
									sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.PLZ_REQUEST));
								}
								break;
							}
						}
					}
					else if(type.equals(NetworkProtocols.WINDOW_INIT_PROPERTY))
					{
						if(PRINT_LOG)System.out.println("\t\t["+userName+"] window_init_request");
						controlPanel.request("WINDOW_INIT_PROPERTY", userIdentify+"-"+clientIP, "-----",request);
						JSONObject respond = new JSONObject();
						respond.put("type", NetworkProtocols.WINDOW_INIT_PROPERTY);
						respond.put("uID", userIdentify);
						respond.put("uName", userName);
						
						if(request.get("client-type")!=null)
						{
							ResultSet rs = handler.excuteQuery("select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ�����, G.�Խñۺ���, M.ī�װ����̸� from ����� S, �Խñ� G, �Խñ�_ī�װ������ M "
			                         + "where S.�й�=G.�ۼ��� and G.ī�װ���=1 and M.ī�װ�����ȣ=G.ī�װ���;");
							
							ResultSet rs2 = handler.excuteQuery("select M.�޼�����ȣ, S.�й�, S.�̸�, M.�޼�������, M.�߽Žð�, M.�޼�������  from �޼��� M, ����� S where M.������='"+userIdentify+"' and M.�߽���=S.�й� and M.����='R' order by �߽Žð� asc");
							
							String[] keys = {"No","�̸�", "�Խñ�����", "�ۼ�����", "�Խñۺ���", "ī�װ���"};
							JSONArray data = new JSONArray();
							try
							{
								while(rs.next())
								{
									Object[] o = {rs.getInt("�Խñ۹�ȣ"),
												  rs.getString("�̸�"),
											      rs.getString("�Խñ�����"),
											      rs.getDate("�ۼ�����"),
											      rs.getString("�Խñۺ���"),
											      rs.getString("ī�װ����̸�")}; 
									
									JSONObject n = Toolbox.createJSONProtocol(keys, o);
									data.add(n);								
								}
								
								String[] keys2 = {"�޼�����ȣ","�й�","�߽���","�޼�������","�߽Žð�", "�޼�������"};
								JSONArray mArr = new JSONArray();
								while(rs2.next())
								{
									Object[] values = {
											rs2.getInt("�޼�����ȣ"),
											rs2.getString("�й�"),
											rs2.getString("�̸�"),
											rs2.getString("�޼�������"),
											rs2.getDate("�߽Žð�"),
											rs2.getString("�޼�������")
									};
									mArr.add(Toolbox.createJSONProtocol(keys2, values));
								}
								
								ResultSet rs3 = handler.excuteQuery("select * from ���⼭�����");

								JSONObject dataBundle = null;
								if(Toolbox.getResultSetSize(rs3)!=0)
								{
									try
									{
										dataBundle = new JSONObject();
										rs3.next();
										dataBundle.put("����з���", rs3.getString("����з���"));
										dataBundle.put("�����ð�", rs3.getDate("�����ð�"));
										
										ResultSet rs4 = handler.excuteQuery("select M.������ȣ,M.������,S.�̸�,M.���⼭������URL,M.����ð� from ���⼭���� M, ����� S where M.������=S.�й� and M.������ = '"+userIdentify+"';");
										rs4.next();
										dataBundle.put("������ȣ", rs4.getInt("������ȣ"));
										
										if(rs4.getString("���⼭������URL")!=null)
										{
											dataBundle.put("���⿩��", "����Ϸ�");
											dataBundle.put("����ð�", rs4.getTimestamp("����ð�"));
											dataBundle.put("������", Files.readAllBytes(new File(rs4.getString("���⼭������URL")).toPath()));
											dataBundle.put("Ȯ����", rs4.getString("���⼭������URL").split("\\.")[1]);
										}
										else
										{
											dataBundle.put("���⿩��", "������");
											dataBundle.put("����ð�", "-----------");									
										}
										
									}
									catch(SQLException e)
									{
										e.printStackTrace();
									}
									
								}
								
								respond.put("board-data", data);
								respond.put("message-data", mArr);
								respond.put("submit-data", dataBundle);
							}
							catch(SQLException e)
							{
								e.printStackTrace();
							}
							
						}
						
						sendProtocol(respond);
						controlPanel.respond("WINDOW_INIT_PROPERTY", userIdentify+"-"+clientIP, "COMMIT", respond);
						if(PRINT_LOG)System.out.println("\t\t\t[Server] send property");
					}
					else if(type.equals(NetworkProtocols.ENROLL_BOARD_REQUEST))
					{
						if(PRINT_LOG)System.out.println(request.toJSONString());
						String creator = (String)request.get("�ۼ���");
						String title = (String)request.get("�Խñ�����");
						String content = (String)request.get("�Խñۺ���");
						if(creator==null||title.length()==0||content.length()==0||request.get("ī�װ���")==null)
						{
							toClient.writeObject(Toolbox.createJSONProtocol(NetworkProtocols.ENROLL_BOARD_ERROR));
							toClient.flush();
							continue;
						}
						controlPanel.request("ENROLL_BOARD_REQUEST", userIdentify+"-"+clientIP, "-----",request);
						ResultSet rr = null;
						try
						{
							rr = handler.excuteQuery("select ī�װ�����ȣ from �Խñ�_ī�װ������ where ī�װ����̸�  = '"+request.get("ī�װ���").toString()+"';");
							System.out.println();
							System.out.println("select ī�װ�����ȣ from �Խñ�_ī�װ������ where ī�װ����̸�  = '"+request.get("ī�װ���").toString()+"';");
							rr.next();
						}
						catch(SQLException e)
						{
							e.printStackTrace();
						}
						

						int category = (int)rr.getInt("ī�װ�����ȣ");
						System.out.println(rr.getInt("ī�װ�����ȣ")+"����");
						
						String qry = "insert into �Խñ�(�ۼ���,�Խñ�����,�Խñۺ���,ī�װ���,�ۼ�����) values('"+creator+"','"+title+"','"+content+"','"+category+"',now())";
						System.out.println(qry);
						handler.excuteUpdate(qry);
						if(PRINT_LOG)System.out.println("\t\t\t["+userName+"] "+request.toJSONString());
						controlPanel.respond("ENROLL_BOARD_RESPOND", userIdentify+"-"+clientIP, "COMMIT",Toolbox.createJSONProtocol(NetworkProtocols.ENROLL_BOARD_RESPOND));
						sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ENROLL_BOARD_RESPOND));
					}
					else if(type.equals(NetworkProtocols.ADMIN_ADD_TAP_REQUEST))
					{
						String qry = "select count(*) from �Խñ�_ī�װ������;";
						ResultSet rs = handler.excuteQuery(qry);
						rs.next();
						controlPanel.request("ADMIN_ADD_TAP_REQUEST", userIdentify+"-"+clientIP, "UI",request);
						qry = "insert into �Խñ�_ī�װ������(ī�װ����̸�) values('"+request.get("name")+"')";
						
						handler.excuteUpdate(qry);
						
						sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_ADD_TAP_RESPOND));
						controlPanel.respond("ADMIN_ADD_TAP_RESPOND", userIdentify+"-"+clientIP, "UI",Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_ADD_TAP_RESPOND));
					}
					else if(type.equals(NetworkProtocols.ADMIN_BOARD_DELETE_REQUEST))
					{
						JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_BOARD_DELETE_RESPOND);
						String qry2 = "select ī�װ��� from �Խñ� where �Խñ۹�ȣ="+request.get("�Խñ۹�ȣ")+";";
						controlPanel.request("ADMIN_DELETE_TAP_REQUEST", userIdentify+"-"+clientIP, "UI",request);
						ResultSet rs = handler.excuteQuery(qry2);
						
						rs.next();
						respond.put("show-category", rs.getInt("ī�װ���"));
						
						String qry = "delete from �Խñ� where �Խñ۹�ȣ="+request.get("�Խñ۹�ȣ")+";";
						handler.excuteUpdate(qry);
						
						sendProtocol(respond);
						controlPanel.respond("ADMIN_BOARD_DELETE_RESPOND", userIdentify+"-"+clientIP, "UI",respond);
					}
					else if(type.equals(NetworkProtocols.BOARD_LIST_REQUEST))
					{
						controlPanel.request("BOARD_LIST_REQUEST", userIdentify+"-"+clientIP, "-----",request);
						ResultSet rr = handler.excuteQuery("select ī�װ�����ȣ from �Խñ�_ī�װ������ where ī�װ����̸�  = '"+request.get("category").toString()+"';");
						rr.next();
						ResultSet rs = handler.excuteQuery("select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ�����, G.�Խñۺ���, M.ī�װ����̸� from ����� S, �Խñ� G, �Խñ�_ī�װ������ M "
								                         + "where S.�й�=G.�ۼ��� and G.ī�װ���="+rr.getString("ī�װ�����ȣ")+" and M.ī�װ�����ȣ=G.ī�װ���;");
						JSONArray arr = new JSONArray();
						JSONArray arr2 = new JSONArray() ;
						
						String[] keys = {"No","�̸�", "�Խñ�����", "�ۼ�����", "�Խñۺ���", "ī�װ���"};
						try
						{
							while(rs.next())
							{
								Object[] o = {rs.getInt("�Խñ۹�ȣ"),
											  rs.getString("�̸�"),
										      rs.getString("�Խñ�����"),
										      rs.getDate("�ۼ�����"),
										      rs.getString("�Խñۺ���"),
										      rs.getString("ī�װ����̸�")}; 
								
								JSONObject n = Toolbox.createJSONProtocol(keys, o);
								arr.add(n);								
							}
						}
						catch(SQLException e)
						{
							e.printStackTrace();
						}
						
						String qry2 = "select ī�װ����̸� from �Խñ�_ī�װ������;";
						
						ResultSet rs2 = handler.excuteQuery(qry2);
						
						try
						{
							while(rs2.next())
							{
								arr2.add(rs2.getString(1));
							}
						}
						catch(SQLException e)
						{
							e.printStackTrace();
						}
						
						JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.BOARD_LIST_RESPOND);
						res.put("board_list", arr);
						res.put("category_list", arr2);
						sendProtocol(res);
						controlPanel.respond("BOARD_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT",res);
					}
					else if(type.equals(NetworkProtocols.BOARD_CONTENT_REQUEST))
					{
						controlPanel.request("BOARD_CONTENT_REQUEST", userIdentify+"-"+clientIP, "-----",request);
						int reqno = (int)request.get("No");
						String qry = "select S.�̸�, G.�Խñ�����, G.�Խñۺ���, M.ī�װ����̸�, G.�ۼ����� from ����� S, �Խñ� G, �Խñ�_ī�װ������ M"
								+    " where S.�й�=G.�ۼ��� and G.�Խñ۹�ȣ="+reqno+" and G.ī�װ���=M.ī�װ�����ȣ; ";
						ResultSet rs = handler.excuteQuery(qry);
						while(rs.next())
						{
							String[] keys = {"�̸�","�Խñ�����","�Խñۺ���","ī�װ���","�ۼ�����"};
							Object[] values = {rs.getString("�̸�"),rs.getString("�Խñ�����"),rs.getString("�Խñۺ���"),rs.getString("ī�װ����̸�"),rs.getDate("�ۼ�����")};
							
							sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.BOARD_CONTENT_RESPOND, keys, values));
							controlPanel.respond("BOARD_CONTENT_RESPOND", userIdentify+"-"+clientIP, "COMMIT",Toolbox.createJSONProtocol(NetworkProtocols.BOARD_CONTENT_RESPOND, keys, values));
						}
					}
					else if(type.equals(NetworkProtocols.BOARD_SEARCH_REQUEST))
					{
						String category = request.get("category").toString();
						controlPanel.request("BOARD_SEARCH_REQUEST", userIdentify+"-"+clientIP, "-----",request);
						ResultSet rs = null;
						if(category=="��ü")
						{
							rs = handler.excuteQuery("select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ����� from ����� S, �Խñ� G "
			                         + "where S.�й�=G.�ۼ��� and G.�Խñ����� like '%"+request.get("search_key").toString()+"%';");	
						}
						else
						{
							rs = handler.excuteQuery("select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ����� from ����� S, �Խñ� G "
			                         + "where S.�й�=G.�ۼ��� and G.�Խñ����� like '%"+request.get("search_key").toString()+"%' and G.ī�װ��� = '"+category+"';");
						}
						
						if(Toolbox.getResultSetSize(rs)==0)
						{
							sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.BOARD_NO_SEARCH_RESULT));
							continue;
						}
						
						JSONArray arr = new JSONArray();
						String[] keys = {"No","�̸�", "�Խñ�����", "�ۼ�����"};
						while(rs.next())
						{
							Object[] o = {rs.getInt("�Խñ۹�ȣ"),
										  rs.getString("�̸�"),
									      rs.getString("�Խñ�����"),
									      rs.getDate("�ۼ�����")}; 
							
							JSONObject n = Toolbox.createJSONProtocol(keys, o);
							arr.add(n);								
						}
						JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.BOARD_SEARCH_RESPOND);
						res.put("boardlist", arr);
						sendProtocol(res);
						controlPanel.respond("BOARD_SEARCH_RESPOND", userIdentify+"-"+clientIP, "COMMIT",res);
					}
					else if(type.equals(NetworkProtocols.MESSAGE_USER_LIST_REQUEST))
					{
						String qry = "select �й�, �̸� from �����;";
						ResultSet rs = handler.excuteQuery(qry);
						controlPanel.request("MESSAGE_USER_LIST_REQUEST", userIdentify+"-"+clientIP, "-----",request);
						ArrayList<String> rcList = new ArrayList<String>();
						
						while(rs.next())
						{
							rcList.add(rs.getString("�й�")+","+rs.getString("�̸�"));
						}
						
						JSONObject o = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_USER_LIST_RESPOND);
						o.put("rcList", rcList);
						
						sendProtocol(o);
						controlPanel.respond("MESSAGE_USER_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT",o);
					}
					else if(type.equals(NetworkProtocols.MESSAGE_SEND_REQUEST))
		            {
						controlPanel.request("MESSAGE_SEND_REQUEST", userIdentify+"-"+clientIP, "-----",request);
		                String sender = request.get("sender").toString();
		                ArrayList<String> reciever = (ArrayList<String>) request.get("reciever");
		                String msgTitle = request.get("msgTitle").toString();
		                String msgContent = request.get("msgContent").toString();
		                  
		                for(int i=0; reciever.size()>i ; i++)
		                {
		                	String send_qurey = "insert into �޼��� (�߽���,������,�޼�������,�޼�������,�߽Žð�,����) values("+"'"+sender+"'"+","+"'"+reciever.get(i).split(",")[0]+"'"+","+"'"+msgTitle+"'"+","+
		                           "'"+msgContent+"'"+","+"now(),'S')";
		                    handler.excuteUpdate(send_qurey);
		                   
		                    send_qurey = "insert into �޼��� (�߽���,������,�޼�������,�޼�������,�߽Žð�,����) values("+"'"+sender+"'"+","+"'"+reciever.get(i).split(",")[0]+"'"+","+"'"+msgTitle+"'"+","+
			                           "'"+msgContent+"'"+","+"now(),'R')";
		                    handler.excuteUpdate(send_qurey);
		                   
		                    for(ConnectedClient c : clients)
		                    {
		                    	if(c.getClientID().equals(reciever.get(i).split(",")[0]))
		                    	{
		                    		JSONObject obj = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_MESSAGE_DIALOG);
		                    		obj.put("msg", "���ο� �޼����� �����߽��ϴ�.");
		                    		c.send(obj);
		                    	}
		                    }
						}
		                
						sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_SEND_RESPOND));
						controlPanel.respond("MESSAGE_SEND_RESPOND", userIdentify+"-"+clientIP, "COMMIT",Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_SEND_RESPOND));
		            }
					else if(type.equals(NetworkProtocols.BOARD_MAIN_REQUEST))
					{
						controlPanel.request("BOARD_MAIN_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						ResultSet rs = handler.excuteQuery("select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ����� from ����� S, �Խñ� G "
		                         + "where S.�й�=G.�ۼ��� and G.ī�װ���=1;");
						JSONArray arr = new JSONArray();
						
						String[] keys = {"No","�̸�","�Խñ�����","�ۼ�����"};
						Object[] values = new Object[4];
						
						while(rs.next())
						{
							values[0] = rs.getString("�Խñ۹�ȣ").toString();
							values[1] = rs.getString("�̸�").toString();
							values[2] = rs.getString("�Խñ�����").toString();
							values[3] = rs.getDate("�ۼ�����");
							arr.add(Toolbox.createJSONProtocol(keys, values));
						}
						
						JSONArray arr2 = new JSONArray();
						String qry2 = "select ī�װ����̸� from �Խñ�_ī�װ������;";
						
						ResultSet rs2 = handler.excuteQuery(qry2);
						
						try
						{
							while(rs2.next())
							{
								arr2.add(rs2.getString(1));
							}
						}
						catch(SQLException e)
						{
							e.printStackTrace();
						}
						
						JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.BOARD_MAIN_RESPOND);
						res.put("board_list", arr);
						res.put("category_list", arr2);
						sendProtocol(res);
						controlPanel.respond("BOARD_MAIN_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
					}
					else if(type.equals(NetworkProtocols.MESSAGE_RECIEVE_LIST_REQUEST))
		               {
		                 try
		                 {
		                     String qry = "select M.�޼�����ȣ, S.�̸�, S.�й� , M.�޼�������, M.�޼������� ,M.�߽Žð� from �޼��� M, ����� S where M.������='"+userIdentify+"' and M.�߽���=S.�й� and M.����='R' order by �߽Žð� asc" ;
		                     
		                     ResultSet rs = handler.excuteQuery(qry);
		                     JSONArray mArr = new JSONArray();
		                     controlPanel.request("MESSAGE_RECIEVE_LIST_REQUEST", userIdentify+"-"+clientIP, "-----", request);
		                     String[] keys = {"No","�߽���","�й�","�޼�������","�޼�������","�߽Žð�"};
		                        
		                     while(rs.next())
		                     {
		                        Object[] values = {
		                              rs.getString("�޼�����ȣ"),
		                              rs.getString("�̸�"),
		                              rs.getString("�й�"),
		                              rs.getString("�޼�������"),
		                              rs.getString("�޼�������"),
		                              rs.getDate("�߽Žð�")
		                            
		                        };
		                        mArr.add(Toolbox.createJSONProtocol(keys, values));
		                      }
		                     
		                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_RECIEVE_LIST_RESPOND);
		                     res.put("message_list", mArr);
		                     sendProtocol(res);
							 controlPanel.respond("MESSAGE_RECIEVE_LISt_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
		                 }
		                 catch(Exception e)
		                 {
		                    e.printStackTrace();
		                 }
		            }
					else if(type.equals(NetworkProtocols.MESSAGE_SHOW_MESSAGE_TAP_REQUEST))
		               {
		                 try
		                 {
							 controlPanel.request("MESSAGE_SHOW_TAP_REQUEST", userIdentify+"-"+clientIP, "COMMIT", request);
		                     String qry = "select M.�޼�����ȣ, S.�̸�, S.�й� , M.�޼�������, M.�޼������� ,M.�߽Žð� from �޼��� M, ����� S where M.������='"+userIdentify+"' and M.�߽���=S.�й� and M.����='R' order by �߽Žð� asc" ;
		                     
		                     ResultSet rs = handler.excuteQuery(qry);
		                     JSONArray mArr = new JSONArray();
		                     
		                     String[] keys = {"No","�߽���","�й�","�޼�������","�޼�������","�߽Žð�"};
		                        
		                     while(rs.next())
		                     {
		                        Object[] values = {
		                              rs.getString("�޼�����ȣ"),
		                              rs.getString("�̸�"),
		                              rs.getString("�й�"),
		                              rs.getString("�޼�������"),
		                              rs.getString("�޼�������"),
		                              rs.getDate("�߽Žð�")
		                            
		                        };
		                        mArr.add(Toolbox.createJSONProtocol(keys, values));
		                      }
		                     
		                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_SHOW_MESSAGE_TAP_RESPOND);
		                     res.put("message_list", mArr);
		                     sendProtocol(res);
							 controlPanel.respond("MESSAGE_SHOW_MSG_TAP_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
		                 }
		                 catch(Exception e)
		                 {
		                    e.printStackTrace();
		                 }
		                  }
		               else if(type.equals(NetworkProtocols.MESSAGE_SEND_LIST_REQUEST))
		               {
		                  String qry = "select M.�޼�����ȣ, S.�̸�, S.�й� , M.�޼�������, M.�޼������� ,M.�߽Žð�  from �޼��� M, ����� S where M.�߽���='"+userIdentify+"' and M.������=S.�й� and M.����='S' order by �߽Žð� asc";
						  controlPanel.request("MESSAGE_SEND_LIST_REQUEST", userIdentify+"-"+clientIP, "COMMIT", request);
		                  ResultSet rs = handler.excuteQuery(qry);
		                  JSONArray mArr = new JSONArray();
		                  String[] keys = {"No","������","�й�","�޼�������","�޼�������","�߽Žð�"};
		                  
		                  while(rs.next())
		                      {
		                           Object[] values = {
		                                 rs.getString("�޼�����ȣ"),
		                                 rs.getString("�̸�"),
		                                 rs.getString("�й�"),
		                                 rs.getString("�޼�������"),
		                                 rs.getString("�޼�������"),
		                                 rs.getDate("�߽Žð�")
		       
		                 
		                            };
		                             mArr.add(Toolbox.createJSONProtocol(keys, values));
		                      }
		                  JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_SEND_LIST_RESPOND);
		                  res.put("message_list", mArr);
		                  sendProtocol(res);
						  controlPanel.respond("MESSAGE_SEND_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
		            }
					else if(type.equals(NetworkProtocols.VIDIO_REQUEST))
					{
						byte[] arr = Files.readAllBytes(Paths.get("c:\\movie\\��.mp4"));
						 
						JSONObject j = Toolbox.createJSONProtocol(NetworkProtocols.VIDIO_RESPOND);
						j.put("vdata", arr);
						sendProtocol(j);
					}
					else if(type.equals(NetworkProtocols.MESSAGE_CONTENT_REQUEST))
					{
						controlPanel.request("BOARD_MAIN_RESPOND", userIdentify+"-"+clientIP, "-----", request);
						int reqNo = (int)request.get("No");
						if(PRINT_LOG)System.out.println("�޼��� ���� ��û��ȣ : "+reqNo);
						JSONObject send_json = new JSONObject();
						String qry="";
						 
						if(request.get("content_type").toString().equals("send"))
						{
                            send_json.put("content_type", "send");
                            qry = "select M.�޼�����ȣ, S.�̸�, M.�޼�������, M.�޼�������, M.�߽Žð� from �޼��� M, ����� S where M.������=S.�й� and M.�޼�����ȣ= "+reqNo;									
						}
						else
						{
							send_json.put("content_type", "recieve");
							qry = "select M.�޼�����ȣ, S.�̸�, M.�޼�������, M.�޼�������, M.�߽Žð� from �޼��� M, ����� S where M.�߽���=S.�й� and M.�޼�����ȣ= "+reqNo;
						}
						
						ResultSet rs = handler.excuteQuery(qry);
						
						while(rs.next())
						{
							String sender_m = rs.getString("�̸�");
                            String msgTitle_m = rs.getString("�޼�������");
                            String msgContent_m = rs.getString("�޼�������");
                            String sendTime_m = rs.getString("�߽Žð�");

                            send_json.put("type",NetworkProtocols.MESSAGE_CONTENT_RESPOND);
                            send_json.put("�߽���", sender_m);
                            send_json.put("�޼�������", msgTitle_m);
                            send_json.put("�޼�������", msgContent_m);
                            send_json.put("�߽Žð�", sendTime_m);
                             
                            sendProtocol(send_json);
    						controlPanel.respond("MESSAGE_CONTENT_RESPOND", userIdentify+"-"+clientIP, "COMMIT", send_json);
						}
					 }
					 else if(type.equals(NetworkProtocols.SHOW_SCHEDULE_MANAGER_TAB_REQUEST))
					 {
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_SCHEDULE_MANAGER_TAB_RESPOND);
						 String qry = "";
						 controlPanel.request("SHOW_SCHEDULE_MANAGER_TAB_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 if(request.get("viewtype").equals("�޷¸��"))
						 {
							 CalendarDataManager c = new CalendarObject().getDataInstance();
							 qry = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(c.getStartDateOfMonth(), c.getEndDateOfMonth())+" order by �Ͻ� asc;";
						 }
						 else
						 {
							 java.util.Date today = new java.util.Date(System.currentTimeMillis());
							 String todayString = new SimpleDateFormat("yyyy-MM-dd").format(today);
							 qry = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(todayString, todayString)+" order by �Ͻ� asc;";							 
						 }
						 System.out.println(qry);
						 JSONArray sList = new JSONArray();
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry);
							 String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","����"};
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"),
										 			rs.getString("�����̸�"), 
										 			rs.getTimestamp("�Ͻ�"),
										 			rs.getString("�з�"),
										 			rs.getString("����")};
								 sList.add(Toolbox.createJSONProtocol(keys, values));
							 }
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
						 respond.put("todays", sList);
						 sendProtocol(respond);
						 controlPanel.respond("SHOW_SCHEDULE_MANAGER_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
					 }
					 else if(type.equals(NetworkProtocols.MODIFY_SCHEDULE_REQUEST))
					 {
						 controlPanel.request("MODIFY_SCHEDULE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry = "update ���� set �����̸�='"+request.get("�����̸�")+"', "
						 		+ "�Ͻ�=date_format('"+request.get("�Ͻ�")+"','%Y-%c-%d %H:%i:%s'), "
						 		+ "�з�='"+request.get("�з�")+"', "
						 		+ "����='"+request.get("����")+"'"
						 		+ "where ������ȣ="+request.get("������ȣ");
						 handler.excuteUpdate(qry);
						 
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_SCHEDULE_MANAGER_TAB_RESPOND);
						 String startDate = request.get("������").toString();
						 String endDate = request.get("������").toString();
						 
						 java.util.Date today = new java.util.Date(System.currentTimeMillis());
						 String todayString = new SimpleDateFormat("yyyy-MM-dd").format(today);
						 
						 if(startDate.length()==0)
						 {
							 startDate = todayString;
						 }
						 
						 if(endDate.length()==0)
						 {
							 endDate = todayString;							 
						 }
						 
						 String qry2 = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(startDate, endDate)+" order by �Ͻ� asc;";
						 
						 if(request.get("viewtype").equals("�޷¸��"))
						 {
							 CalendarDataManager c = new CalendarObject().getDataInstance();
							 try
							 {
								 c.getCalander().setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(request.get("�Ͻ�").toString()));
							 }
							 catch(ParseException e)
							 {
								 e.printStackTrace();
							 }
							 qry2 = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(c.getStartDateOfMonth(), c.getEndDateOfMonth())+" order by �Ͻ� asc;";
						 }

						 JSONArray sList = new JSONArray();
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry2);
							 String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","����"};
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"),
										 			rs.getString("�����̸�"), 
										 			rs.getTimestamp("�Ͻ�"),
										 			rs.getString("�з�"),
										 			rs.getString("����")};
								 sList.add(Toolbox.createJSONProtocol(keys, values));
							 }
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
						 respond.put("todays", sList);
						 sendProtocol(respond);
						 controlPanel.respond("MODIFY_SCHEDULE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
					 }
					 else if(type.equals(NetworkProtocols.ADD_SCHEDULE_REQUEST))
					 {
						 String qry = "insert into ����(�����̸�,�Ͻ�,�з�,����) values('"+request.get("����")+"', date_format('"+request.get("�Ͻ�")+"','%Y-%c-%d %H:%i:%s'), '"+request.get("�з�")+"', '"+request.get("����")+"');";
						 controlPanel.request("ADD_SCHEDULE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 handler.excuteUpdate(qry);
						 
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_SCHEDULE_MANAGER_TAB_RESPOND);
						 String startDate = request.get("������").toString();
						 String endDate = request.get("������").toString();
						 
						 java.util.Date today = new java.util.Date(System.currentTimeMillis());
						 String todayString = new SimpleDateFormat("yyyy-MM-dd").format(today);
						 
						 if(startDate.length()==0)
						 {
							 startDate = todayString;
						 }
						 
						 if(endDate.length()==0)
						 {
							 endDate = todayString;							 
						 }
						 
						 String qry2 = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(startDate, endDate)+" order by �Ͻ� asc;";
						 
						 if(request.get("viewtype").equals("�޷¸��"))
						 {
							 CalendarDataManager c = new CalendarObject().getDataInstance();
							 try
							 {
								 c.getCalander().setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(request.get("�Ͻ�").toString()));
							 }
							 catch(ParseException e)
							 {
								 e.printStackTrace();
							 }
							 qry2 = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(c.getStartDateOfMonth(), c.getEndDateOfMonth())+" order by �Ͻ� asc;";
						 }
						 
						 JSONArray sList = new JSONArray();
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry2);
							 String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","����"};
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"),
										 			rs.getString("�����̸�"), 
										 			rs.getTimestamp("�Ͻ�"),
										 			rs.getString("�з�"),
										 			rs.getString("����")};
								 sList.add(Toolbox.createJSONProtocol(keys, values));
							 }
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
						 respond.put("todays", sList);
						 sendProtocol(respond);
						 controlPanel.respond("ADD_SCHEDULE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
					 }
					 else if(type.equals(NetworkProtocols.DELETE_SCHEDULE_REQUEST))
					 {
						 String startDate = request.get("������").toString();
						 String endDate = request.get("������").toString();
						 String qry = "";
						 controlPanel.request("DELETE_SCHEDULE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 
						 qry = "delete from ���� where ������ȣ = "+request.get("������ȣ");
						 handler.excuteUpdate(qry);
						 
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_SCHEDULE_MANAGER_TAB_RESPOND);
						 
						 
						 java.util.Date today = new java.util.Date(System.currentTimeMillis());
						 String todayString = new SimpleDateFormat("yyyy-MM-dd").format(today);
						 
						 if(startDate.length()==0)
						 {
							 startDate = todayString;
						 }
						 
						 if(endDate.length()==0)
						 {
							 endDate = todayString;							 
						 }
						 
						 String qry2 = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(startDate, endDate)+" order by �Ͻ� asc;";
						 
						 if(request.get("viewtype").equals("�޷¸��"))
						 {
							 CalendarDataManager c = new CalendarObject().getDataInstance();
							 try
							 {
								 c.getCalander().setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(request.get("�Ͻ�").toString()));
							 }
							 catch(ParseException e)
							 {
								 e.printStackTrace();
							 }
							 qry2 = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(c.getStartDateOfMonth(), c.getEndDateOfMonth())+" order by �Ͻ� asc;";
						 }
						 
						 JSONArray sList = new JSONArray();
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry2);
							 String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","����"};
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"),
										 			rs.getString("�����̸�"), 
										 			rs.getTimestamp("�Ͻ�"),
										 			rs.getString("�з�"),
										 			rs.getString("����")};
								 sList.add(Toolbox.createJSONProtocol(keys, values));
							 }
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
						 respond.put("todays", sList);
						 sendProtocol(respond);
						 controlPanel.respond("DELETE_SCHEDULE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
					 }
					 else if(type.equals(NetworkProtocols.SCHEDULE_PROFESSIONAL_SEARCH_REQUEST))
					 {
						 controlPanel.request("SCHEDULE_PROFESSIONAL_SEARCH_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_SCHEDULE_MANAGER_TAB_RESPOND);
						 String sp = request.get("�з�").toString();
						 String qry;
						 if(sp.equals("��ü"))
						 {
							 qry = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(request.get("������").toString(), request.get("������").toString())+" order by �Ͻ� asc;";							 
						 }
						 else
						 {
							 qry = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(request.get("������").toString(), request.get("������").toString())+"and �з�='"+sp+"' order by �Ͻ� asc;";
						 }
						 
						 JSONArray sList = new JSONArray();
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry);
							 String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","����"};
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"),
										 			rs.getString("�����̸�"), 
										 			rs.getTimestamp("�Ͻ�"),
										 			rs.getString("�з�"),
										 			rs.getString("����")};
								 sList.add(Toolbox.createJSONProtocol(keys, values));
							 }
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
						 respond.put("todays", sList);
						 sendProtocol(respond);
						 controlPanel.respond("SCHEDULE_PROFESSIONAL_SEARCH_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
					 }
					 else if(type.equals(NetworkProtocols.MONTHLY_SCHEDULE_VIEW_REQUEST))
					 {
						 String qry = "select * from ���� where "+Toolbox.getWhereStringBetweenDate(request.get("startDate").toString(), request.get("endDate").toString())+" order by �Ͻ� asc;";

						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.MONTHLY_SCHEDULE_VIEW_RESPOND);
						 controlPanel.request("MONTHLY_SCHEDULE_VIEW_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 JSONArray sList = new JSONArray();
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry);
							 String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","����"};
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"),
										 			rs.getString("�����̸�"), 
										 			rs.getTimestamp("�Ͻ�"),
										 			rs.getString("�з�"),
										 			rs.getString("����")};
								 sList.add(Toolbox.createJSONProtocol(keys, values));
							 }
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
						 respond.put("todays", sList);
						 respond.put("viewableDate", request.get("startDate").toString());
						 sendProtocol(respond);
						 controlPanel.request("MONTHLY_SCHEDULE_VIEW_RESPOND", userIdentify+"-"+clientIP, "COMMIT", request);
					 }
					 else if(type.equals(NetworkProtocols.SHOW_USER_INFO_TAP_REQUEST))
	                {
						 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� order by S.���ȣ";
						 controlPanel.request("SHOW_USER_INFO_TAP_REQUEST", userIdentify+"-"+clientIP, "-----", request);
	                      ResultSet rs = handler.excuteQuery(qry);
	                      
	                      JSONArray mArr = new JSONArray();
	                        
	                      String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
	                         while(rs.next())
	                        {
	                           Object[] values = {
	                                 rs.getString("�й�"),
	                                 rs.getString("�̸�"),
	                                 rs.getString("�ּ�"),
	                                 rs.getString("�޴�����ȣ"),
	                                 rs.getString("������ȭ��ȣ"),
	                                 rs.getString("�ֹε�Ϲ�ȣ"),
	                                 rs.getString("����"),
	                                 rs.getString("���ȣ"),
	                                 rs.getInt("�г�"),
	                                 rs.getString("�а��̸�")
	                                                            };
	                           mArr.add(Toolbox.createJSONProtocol(keys, values));
	                        }
	                        // ���ο� ���̽��� �������� ����
	                        JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.SHOW_USER_INFO_TAP_RESPOND);
	                        // ���̽� ��� ���� 
	                        res.put("user_list", mArr);
	                        sendProtocol(res);
	                }
		               else if(type.equals(NetworkProtocols.USER_CONTENT_REQUEST))
	                    {
	                       String num = request.get("�й�").toString();
	                       
	                       System.out.println(num);
	                       String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�, S.�����ʻ���URL  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�й� = '"+num+"'";
	                       ResultSet rs = handler.excuteQuery(qry);
	                     
	                       JSONObject mArr = new JSONObject();
	                             
	                       String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�","�̹���������"};
	                       while(rs.next())
	                       {
	                          if(rs.getString("�����ʻ���URL")!=null)
	                          {
	                              Object[] values = {
	                                     rs.getString("�й�"),
	                                     rs.getString("�̸�"),
	                                     rs.getString("�ּ�"),
	                                     rs.getString("�޴�����ȣ"),
	                                     rs.getString("������ȭ��ȣ"),
	                                     rs.getString("�ֹε�Ϲ�ȣ"),
	                                     rs.getString("����"),
	                                     rs.getString("���ȣ"),
	                                     rs.getInt("�г�"),
	                                     rs.getString("�а��̸�"),
	                                     Files.readAllBytes(new File(rs.getString("�����ʻ���URL")).toPath())
	                                     };
	                              mArr = Toolbox.createJSONProtocol(NetworkProtocols.USER_CONTENT_RESPOND, keys, values);
	                          }
	                          else
	                          {
	                              Object[] values = {
	                                     rs.getString("�й�"),
	                                     rs.getString("�̸�"),
	                                     rs.getString("�ּ�"),
	                                     rs.getString("�޴�����ȣ"),
	                                     rs.getString("������ȭ��ȣ"),
	                                     rs.getString("�ֹε�Ϲ�ȣ"),
	                                     rs.getString("����"),
	                                     rs.getString("���ȣ"),
	                                     rs.getInt("�г�"),
	                                     rs.getString("�а��̸�"),
	                                     "no-image"
	                                     };
	                              mArr = Toolbox.createJSONProtocol(NetworkProtocols.USER_CONTENT_RESPOND, keys, values);
	                          }

	                       }

	                       sendProtocol(mArr);  
						   controlPanel.respond("USER_CONTENT_RESPOND", userIdentify+"-"+clientIP, "COMMIT", mArr);
	                    }
					
	                else if(type.equals(NetworkProtocols.WEABAK_INFO_TAP_REQUEST))
	                {
					   controlPanel.request("WEABAK_INFO_TAP_REQUEST", userIdentify+"-"+clientIP, "-----", request);	                   
	                   String category = request.get("category").toString();
	               
	                   String qry;
	                   ResultSet rs = null;	                   
	                  
	                   if(category == "main")
	                   {
	                      qry = "select W.�ܹڹ�ȣ,S.�й� ,S.�̸�, W.���� ,W.��û���� ,W.�ܹ����� ,W.������ ,W.���ο��� from �ܹ� W, ����� S where S.�й� = W.��û�� and W.���ο��� =0 ";
	                      rs = handler.excuteQuery(qry);
	                   }
	                   else if(category.equals("�����"))
	                   {
	                      qry = "select W.�ܹڹ�ȣ,S.�й� ,S.�̸�, W.���� ,W.��û���� ,W.�ܹ����� ,W.������ ,W.���ο��� from �ܹ� W, ����� S where S.�й� = W.��û�� and W.���ο��� =0 ";
	                      rs = handler.excuteQuery(qry);
	                   }
	                   else if(category.equals("���οܹ�"))
	                   {
	                      qry = "select W.�ܹڹ�ȣ,S.�й� ,S.�̸�, W.���� ,W.��û���� ,W.�ܹ����� ,W.������ ,W.���ο��� from �ܹ� W, ����� S where S.�й� = W.��û�� and W.���ο��� =1 ";
	                      rs = handler.excuteQuery(qry);
	                   }
	                   else
	                   {
	                      qry = "select W.�ܹڹ�ȣ,S.�й� ,S.�̸�, W.���� ,W.��û���� ,W.�ܹ����� ,W.������ ,W.���ο��� from �ܹ� W, ����� S where S.�й� = W.��û�� and W.���ο��� =2 ";
	                      rs = handler.excuteQuery(qry);
	                   }
	                   
	                   JSONArray jarr = new JSONArray();
	                   String keys[] = {"�ܹڹ�ȣ","�̸�","�й�","����","��û����","�ܹ�����","������","���ο���"};
	                   while(rs.next())
	                   {
	                      Object[] values ={
	                            rs.getString("�ܹڹ�ȣ"),
	                            rs.getString("�̸�"),
	                            rs.getString("�й�"),
	                            rs.getString("����"),
	                            rs.getString("��û����"),
	                            rs.getString("�ܹ�����"),
	                            rs.getString("������"),
	                            rs.getString("���ο���")
	                      };
	                      
	                      jarr.add(Toolbox.createJSONProtocol(keys, values)); 
	                   }
	                   JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.WEABAK_INFO_TAP_RESPOND);
	                   json.put("weabak_list", jarr);
	                   sendProtocol(json);
					   controlPanel.respond("WEABAK_INFO_TAP_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
	                }
	                else if(type.equals(NetworkProtocols.PLUS_MINUS_TAP_REQUEST))
	                {
					   controlPanel.request("PLUS_MINUS_TAP_REQUEST", userIdentify+"-"+clientIP, "-----", request);
	                   String qry = "select * from ����� where ����ڵ�� = '�л�';";
	                   String plus = null;
	                   String minus = null;
	                   int sum;
	                   ResultSet rs = handler.excuteQuery(qry);
	                   
	                   JSONArray jarr = new JSONArray();
	                   String keys[] = {"�й�","�̸�","����","����","�հ�"};
	                   while(rs.next())
	                   {
	                      String num = rs.getString("�й�");
	                      System.out.println("�й� ��� : "+num);
	                      
	                      String plusqry = "select sum(����) from ������ο���� where �й� ="+"'"+num+"'"+"and "+"�����Ÿ�� = '����'";
	                      String minusqry = "select sum(����) from ������ο���� where �й� ="+"'"+num+"'"+"and "+"�����Ÿ�� = '����'";
	                      
	                      ResultSet rs1 = handler.excuteQuery(plusqry);
	                      ResultSet rs2 = handler.excuteQuery(minusqry);
	                      
	                      while(rs1.next())
	                      {
	                         plus = rs1.getString("sum(����)");
	                      }
	                      while(rs2.next())
	                      {
	                         minus = rs2.getString("sum(����)");
	                      }
	                      
	                      if(plus == null)
	                      {
	                         plus = "0";
	                      }
	                      if(minus == null)
	                      {
	                         minus = "0";
	                      }
	                      
	                      System.out.println("���� : "+plus);
	                      System.out.println("���� : "+minus);
	                      
	                      
	                      sum = Integer.parseInt(plus)+Integer.parseInt(minus);
	                      
	                      Object[] values ={
	                            rs.getString("�й�"),
	                            rs.getString("�̸�"),
	                            plus,
	                            minus,
	                            sum
	                      };
	                      
	                      jarr.add(Toolbox.createJSONProtocol(keys, values)); 
	                   }
	                   
	                   JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.PLUS_MINUS_TAP_RESPOND);
	                   json.put("plusminus_list", jarr);
	                   sendProtocol(json);
					   controlPanel.respond("PLUS_MINUS_TAP_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
	                }
	                //�߰�
	                else if(type.equals(NetworkProtocols.WEABAK_CONTENT_REQUEST))
	                {	                   
	                   int No = (int) request.get("No");
	                   String qry = "select * from �ܹ� where �ܹڹ�ȣ = "+"'"+No+"'";
	                   String name = request.get("�̸�").toString();
					   controlPanel.request("WEABAK_CONTENT_REQUEST", userIdentify+"-"+clientIP, "-----", request);  
	                      ResultSet rs = handler.excuteQuery(qry);
	                      
	                      JSONObject arr = null;
	                      
	                      String keys[] = {"�ܹڹ�ȣ","�й�","����","��û����","�ܹ�����","������","���ο���"};
	                      while(rs.next())
	                      {
	                         Object values[] = {rs.getInt("�ܹڹ�ȣ"),rs.getString("��û��"),rs.getString("����"),rs.getDate("��û����"),rs.getDate("�ܹ�����"),rs.getString("������"),rs.getInt("���ο���")};
	                         arr = Toolbox.createJSONProtocol(keys, values); 
	                      }
	                      arr.put("�̸�", name);
	                      
	                      JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.WEQBAK_CONTENT_RESPOND);
	                      json.put("weabak_content_list", arr);
	                      sendProtocol(json);
						  controlPanel.respond("WEABAK_CONTENT_RESPOND", userIdentify+"-"+clientIP, "COMMIT", request);
	                }
	                else if(type.equals(NetworkProtocols.PLUS_MINUS_TAP_INFO_REQUEST))
	                {
	                   String qry = "select P.������ο���ȣ,P.��¥,S.�й� ,S.�̸�, P.���� ,P.���� ,P.�����Ÿ��  from ������ο���� P, ����� S where S.�й� = P.�й�";
					   controlPanel.request("PLUS_MINUS_TAP_INFO_REQUEST", userIdentify+"-"+clientIP, "-----", request);
	                   ResultSet rs = handler.excuteQuery(qry);
	                   JSONArray jarray = new JSONArray();
	                   
	                   String keys[] = {"No","��¥","�й�","�̸�","����","����","�����Ÿ��"};
	                   while(rs.next())
	                   {
	                      Object values[] = {
	                            rs.getInt("������ο���ȣ"),
	                            rs.getDate("��¥"),
	                            rs.getString("�й�"),
	                            rs.getString("�̸�"),
	                            rs.getString("����"),
	                            rs.getInt("����"),
	                            rs.getString("�����Ÿ��")       
	                      };
	                      jarray.add(Toolbox.createJSONProtocol(keys, values));
	                   }
	                   JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.PLUS_MINUS_TAP_INFO_RESPOND);
	                   json.put("plus_minus_check_list", jarray);
	                   
	                   sendProtocol(json);
					   controlPanel.respond("PLUS_MINUS_TAP_INFO_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
	                }
					 else if(type.equals(NetworkProtocols.MY_OVERNIGHT_LIST_REQUEST))
					 {
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.MY_OVERNIGHT_LIST_RESPOND);
						 controlPanel.request("MY_OVERNIGHT_LIST_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry = "select * from �ܹ� where ��û��='"+request.get("uID").toString()+"' order by ��û���� asc;";
						 
						 try
						 {
							 ResultSet rs = handler.excuteQuery(qry);

							 String[] keys = {"��û����", "�ܹ�����", "������", "����", "���ο���"};
							 JSONArray jArr = new JSONArray();
							 while(rs.next())
							 {
								 Object[] values = {rs.getTimestamp("��û����"), rs.getDate("�ܹ�����"), rs.getString("������"), rs.getString("����"), rs.getInt("���ο���")};
								 jArr.add(Toolbox.createJSONProtocol(keys, values));
							 }
							 respond.put("data", jArr);
							 sendProtocol(respond);
							 controlPanel.respond("MY_OVERNIGHT_LIST_REQUEST", userIdentify+"-"+clientIP, "COMMIT", respond);
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
					 }
					 else if(type.equals(NetworkProtocols.ENROLL_OVERNIGHT_REQUEST))
					 {
						 JSONObject obj = Toolbox.createJSONProtocol(NetworkProtocols.ENROLL_OVERNIGHT_RESPOND);
						 // �˻� ���� �� �ܹ� ��û ���ϴ¾ָ� result�� �׿��°�����
						 controlPanel.request("ENROLL_OVERNIGHT_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry = "insert into �ܹ�(��û��,����,��û����,�ܹ�����,������) values('"+userIdentify+"','"+request.get("����")+"',now(),'"+request.get("�ܹ�����")+"','"+request.get("������")+"');";
						 
						 handler.excuteUpdate(qry);
						 
						 obj.put("result", "OK");
						 
						 sendProtocol(obj);
						 
						 for(ConnectedClient c : clients)
						 {
							 if(c.getClientGrade().equals("������"))
							 {
				                   String qry2;
				                   ResultSet rs = null;
				                   
				                  
				                   qry2 = "select W.�ܹڹ�ȣ,S.�й� ,S.�̸�, W.���� ,W.��û���� ,W.�ܹ����� ,W.������ ,W.���ο��� from �ܹ� W, ����� S where S.�й� = W.��û�� and W.���ο��� = 0 ";
				                   rs = handler.excuteQuery(qry2);
				                   
				                   JSONArray jarr = new JSONArray();
				                   String keys[] = {"�ܹڹ�ȣ","�̸�","�й�","����","��û����","�ܹ�����","������","���ο���"};
				                   while(rs.next())
				                   {
				                      Object[] values ={
				                            rs.getString("�ܹڹ�ȣ"),
				                            rs.getString("�̸�"),
				                            rs.getString("�й�"),
				                            rs.getString("����"),
				                            rs.getString("��û����"),
				                            rs.getString("�ܹ�����"),
				                            rs.getString("������"),
				                            rs.getString("���ο���")
				                      };
				                      
				                      jarr.add(Toolbox.createJSONProtocol(keys, values)); 
				                   }
				                   JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.WEABAK_INFO_TAP_RESPOND);
				                   json.put("weabak_list", jarr);
				                   sendProtocol(json);
								   controlPanel.respond("WEABAK_INFO_TAP_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
							 }
						 }
					 }
					 else if(type.equals(NetworkProtocols.WEABAK_PROCESS_REQUEST))
					 {
						 
						 String action = request.get("action").toString();
						 String reqNo = request.get("reqNo").toString();
						 controlPanel.request("WEABAK_PROCESS_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry = "update �ܹ� set ���ο��� = "+action+" where �ܹڹ�ȣ = "+reqNo+";";
						 
						 handler.excuteUpdate(qry);
						 controlPanel.respond("SHOW_USER_INFO_TAP_REQUEST", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.WEABAK_PROCESS_RESPOND));
						 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.WEABAK_PROCESS_RESPOND));
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_RECIEVE_MESSAGE_REQUEST))
					 {
						 String qry = "select M.�޼�����ȣ, S.�й�, S.�̸�, M.�޼�������, M.�߽Žð�, M.�޼�������  from �޼��� M, ����� S where M.������='"+userIdentify+"' and M.�߽���=S.�й� and M.����='R' order by �߽Žð� asc";
						 controlPanel.request("STUDENT_RECIEVE_MESSAGE_REQUEST", userIdentify+"-"+clientIP, "-----", request);							
						ResultSet rs = handler.excuteQuery(qry);
						JSONArray mArr = new JSONArray();
						
						String[] keys = {"�޼�����ȣ","�й�","�߽���","�޼�������","�߽Žð�", "�޼�������"};
						
						while(rs.next())
						{
							Object[] values = {
									rs.getInt("�޼�����ȣ"),
									rs.getString("�й�"),
									rs.getString("�̸�"),
									rs.getString("�޼�������"),
									rs.getDate("�߽Žð�"),
									rs.getString("�޼�������")
							};
							mArr.add(Toolbox.createJSONProtocol(keys, values));
						}
						
						JSONObject o = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_RECIEVE_MESSAGE_RESPOND);
						o.put("data", mArr);
						sendProtocol(o);
						controlPanel.respond("STUDENT_RECIEVE_MESSAGE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", o);							
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_SEND_MESSAGE_REQUEST))
					 {
						String qry = "select M.�޼�����ȣ, S.�й�, S.�̸�, M.�޼�������, M.�߽Žð�, M.�޼������� from �޼��� M, ����� S where M.�߽���='"+userIdentify+"' and M.������=S.�й� and M.����='S' order by �߽Žð� asc";
						 controlPanel.request("STUDENT_SEND_MESSAGE_REQUEST", userIdentify+"-"+clientIP, "-----", request);							
						System.out.println("����Ǵ� ���� : "+qry);
						ResultSet rs = handler.excuteQuery(qry);
						JSONArray mArr = new JSONArray();
						
						String[] keys = {"�޼�����ȣ","�й�","������","�޼�������","�߽Žð�", "�޼�������"};
						
						try
						{
							while(rs.next())
							{
								Object[] values = {
										rs.getInt("�޼�����ȣ"),
										rs.getString("�й�"),
										rs.getString("�̸�"),
										rs.getString("�޼�������"),
										rs.getDate("�߽Žð�"),
										rs.getString("�޼�������")
								};
								mArr.add(Toolbox.createJSONProtocol(keys, values));
							}
						}
						catch(SQLException e)
						{
							e.printStackTrace();
						}
						
						JSONObject o = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_SEND_MESSAGE_RESPOND);
						o.put("data", mArr);
						sendProtocol(o);
						controlPanel.respond("STUDENT_SEND_MESSAGE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", o);
					 }
					 else if(type.equals(NetworkProtocols.PLUS_MINUS_ASSIGN_REQUEST))
					 {
						 controlPanel.request("PLUS_MINUS_ASSIGN_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.PLUS_MINUS_ASSGIN_RESPOND);
						 json.put("�й�", request.get("�й�").toString());
						 json.put("�̸�", request.get("�̸�").toString());
						 controlPanel.respond("PLUS_MINUS_ASSIGN_REQUEST_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
						 sendProtocol(json);
					 }
					 else if(type.equals(NetworkProtocols.PLUS_MINUS_OVER_REQUEST))
					 {
						 controlPanel.request("PLUS_MINUS_OVER_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String num = request.get("�й�").toString();
						 String content = request.get("����").toString();
						 String choice = request.get("�����Ÿ��").toString();
						 String score = request.get("����").toString();
						 
						 if(Toolbox.isNumber(score))
						 {
							 if(choice.equals("����"))
							 {
								 score = "-"+score;
							 }
						 }
						 String qry = "insert into ������ο����(�й�,�����Ÿ��,����,����,��¥) values('"+num+"' , '"+choice+"' ,"+score+", '"
								 +content+"',now());";
						
						 handler.excuteUpdate(qry);
						 
						 JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.PLUS_MINUS_OVER_RESPOND);
						 sendProtocol(json);
						 controlPanel.respond("PLUS_MINUS_OVER_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_USER_INFO_REQUEST))
					 {
						 controlPanel.request("STUDENT_USER_INFO_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String query = "select S.�й�, "
						 		+ "U.�̸�, "
						 		+ "S.�ּ�, "
						 		+ "S.�޴�����ȣ, "
						 		+ "S.������ȭ��ȣ, "
						 		+ "S.�ֹε�Ϲ�ȣ, "
						 		+ "S.����, "
						 		+ "S.���ȣ, "
						 		+ "S.�����ʻ���URL, "
						 		+ "S.�г�, "
						 		+ "S.�Ҽ��а�,"
						 		+ "Q.��������,"
						 		+ "S.��й�ȣã��_�亯 "
						 		+ "from �л� S, ����� U, ��й�ȣã��_������� Q where S.�й�=U.�й� and U.�й�='"+userIdentify+"' and Q.������ȣ=S.��й�ȣã��_����;";
						 // ������ URL�� ���� ���� �ǽÿ� ����� �̹��� ������ ������ JSON�� ���� �����
						 
		    			ResultSet rs = handler.excuteQuery(query);
						 
		    			try
		    			{
		    				String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�̹���������","�г�","�Ҽ��а�","��������","��й�ȣã��_�亯"};
		    				JSONObject respond = null;
		    				while(rs.next())
		    				{
		    					if(rs.getString("�����ʻ���URL")!=null)
		    					{
		    						byte[] iData = Files.readAllBytes(new File(rs.getString("�����ʻ���URL")).toPath());
		    						Object[] values = {
			    							rs.getString("�й�"),
			    							rs.getString("�̸�"),
			    							rs.getString("�ּ�"),
			    							rs.getString("�޴�����ȣ"),
			    							rs.getString("������ȭ��ȣ"),
			    							rs.getString("�ֹε�Ϲ�ȣ"),
			    							rs.getString("����"),
			    							rs.getString("���ȣ"),
			    							iData,
			    							rs.getInt("�г�"),
			    							rs.getString("�Ҽ��а�"),
			    							rs.getString("��������"),
			    							rs.getString("��й�ȣã��_�亯")
			    					};
			    					respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_USER_INFO_RESPOND, keys, values);
		    					}
		    					else
		    					{
		    						Object[] values = {
			    							rs.getString("�й�"),
			    							rs.getString("�̸�"),
			    							rs.getString("�ּ�"),
			    							rs.getString("�޴�����ȣ"),
			    							rs.getString("������ȭ��ȣ"),
			    							rs.getString("�ֹε�Ϲ�ȣ"),
			    							rs.getString("����"),
			    							rs.getString("���ȣ"),
			    							"imageX",
			    							rs.getInt("�г�"),
			    							rs.getString("�Ҽ��а�"),
			    							rs.getString("��������"),
			    							rs.getString("��й�ȣã��_�亯")
			    					};
			    					respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_USER_INFO_RESPOND, keys, values);
		    					}
		    					

		    				}
		    				
		    				String qry2 = "select sum(����) AS ����� from ������ο���� where �й�='"+userIdentify+"';";
		    				
		    				ResultSet rs2 = handler.excuteQuery(qry2);
		    				
		    				while(rs2.next())
		    				{
		    					respond.put("�����", rs2.getInt("�����"));
		    				}
		    				
		    				String qry3 = "select �������� from ��й�ȣã��_�������;";
		    				ResultSet rs3 = handler.excuteQuery(qry3);
		    				JSONArray qList = new JSONArray();
		    				while(rs3.next())
		    				{
		    					qList.add(rs3.getString("��������"));
		    				}
	    					respond.put("�������", qList);	
	    					System.out.println(respond.toJSONString());
		    				sendProtocol(respond);
		    				controlPanel.respond("STUDENT_USER_INFO_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
		    			}
		    			catch(SQLException e)
		    			{
		    				e.printStackTrace();
		    			}
		    			
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_UPLOAD_PROFILE_IMAGE_REQUEST))
					 {
						 controlPanel.request("STUDENT_UPLOAD_PROFILE_IMAGE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String requestFileFormat = request.get("fileName").toString().split("\\.")[1];
						 byte[] requestImageData = (byte[]) request.get("content");
						 
						 // ���� ���ÿ� �����۾�
						 String savePath = Statics.DEFAULT_USER_DATA_DIRECTORY+userIdentify+"_profilePhoto."+requestFileFormat;
						 String qry = "update �л� set �����ʻ���URL = '"+savePath+"' where �й�='"+userIdentify+"';";
						 
						 handler.excuteUpdate(qry);
						 System.out.println("�������� : "+qry);
						 Files.write(new File(savePath).toPath(), requestImageData);
						 
						 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_UPLOAD_PROFILE_IMAGE_RESPOND);
						 respond.put("Image-data", Files.readAllBytes(new File(savePath).toPath()));
						 sendProtocol(respond);
						 controlPanel.respond("STUDENT_UPLOAD_PROFILE_IMAGE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_MODIFY_USER_INFO_REQUEST))
					 {
						 controlPanel.request("STUDENT_MODIFY_USER_INFO_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry = "update ����� set �̸� = '"+request.get("�̸�")+"' where �й� = '"+userIdentify+"';";
						 handler.excuteUpdate(qry);
						 
						 String qry2 = "update �л� set ���� = '"+request.get("����")+"', �޴�����ȣ = '"+request.get("�޴�����ȣ")+"', �ֹε�Ϲ�ȣ = '"+request.get("�ֹε�Ϲ�ȣ")+"', ������ȭ��ȣ = '"+request.get("������ȭ��ȣ")+"', �ּ� = '"+request.get("�ּ�")+"', �г� = '"+request.get("�г�")+"', �Ҽ��а� = '"+request.get("�Ҽ��а�")+"' where �й� = '"+userIdentify+"';";
						 handler.excuteUpdate(qry2);
						 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_MODIFY_USER_INFO_RESPOND));
						 controlPanel.respond("STUDENT_MODIFY_USER_INFO_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_MODIFY_USER_INFO_RESPOND));
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_REAUTH_REQUEST))
					 {
						 String qry = "select * from ����� where �й� = '"+request.get("reqID")+"';";
						 ResultSet rs = handler.excuteQuery(qry);
						 controlPanel.request("STUDENT_REAUTH_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 try
						 {
							if(rs.next())
							{
								JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_REAUTH_RESPOND);
								if(rs.getString("��й�ȣ").equals(request.get("reqPW")))
								{
									respond.put("accept", "Y");
								}
								else
								{
									respond.put("accept", "N");									
								}
								sendProtocol(respond);
								controlPanel.respond("STUDENT_REAUTH_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
							}
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_PASSWORD_SETUP_REQUEST))
					 {
						 String qry = "update �л� set ��й�ȣã��_���� = "+request.get("����")+", ��й�ȣã��_�亯 = '"+request.get("�亯")+"' where �й� = '"+userIdentify+"';";
						 handler.excuteUpdate(qry);
						 controlPanel.request("STUDENT_PASSWORD_SETUP_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 if(request.get("����й�ȣ").toString().length()!=0)
						 {
							 qry = "update ����� set ��й�ȣ = '"+request.get("����й�ȣ")+"' where �й� = '"+userIdentify+"';";
							 handler.excuteUpdate(qry);
						 }
						 controlPanel.respond("STUDENT_PASSWORD_SETUP_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_PASSWORD_SETUP_RESPOND));
						 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_PASSWORD_SETUP_RESPOND));
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_MEDIA_LIST_REQUEST))
					 {
						 controlPanel.request("STUDENT_MEDIA_LIST_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry = "select ������ȣ, �����̸� from �������";
						 ResultSet rs = handler.excuteQuery(qry);
						 JSONArray data = new JSONArray();
						 String[] keys = {"������ȣ", "�����̸�"}; 
						 try
						 {
							 while(rs.next())
							 {
								 Object[] values = {rs.getInt("������ȣ"), rs.getString("�����̸�")};
								 data.add(Toolbox.createJSONProtocol(keys, values));
							 }
							 
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_MEDIA_LIST_RESPOND);
							 respond.put("data", data);
							 sendProtocol(respond);
							 controlPanel.respond("STUDENT_MEDIA_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
						 }
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
						 
					 }
					 else if(type.equals(NetworkProtocols.STUDENT_MEDIA_CONTENT_REQUEST))
					 {
						 controlPanel.request("STUDENT_MEDIA_CONTENT_REQUEST", userIdentify+"-"+clientIP, "-----", request);
						 String qry2 = "select �����̸�, ����ũ�� from ������� where ������ȣ = "+request.get("������ȣ")+";";
						 
						 ResultSet rs2 = handler.excuteQuery(qry2);
						 
						 rs2.next();
						 
						 JSONObject notification = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_SEND_MEDIA_NOTIFICATION);
						 notification.put("name", rs2.getString("�����̸�"));
						 notification.put("size", rs2.getInt("����ũ��"));
						 sendProtocol(notification);
						 String qry = "select �������, ����ũ�� from ������� where ������ȣ = "+request.get("������ȣ")+";";
						 
						 ResultSet rs = handler.excuteQuery(qry);
						 
						 try
						 {
							 rs.next();
							 String openPath = rs.getString("�������");
							 
							 byte[] data = Files.readAllBytes(new File(openPath).toPath());
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_MEDIA_CONTENT_RESPOND);
							 respond.put("data", data);
							 respond.put("fileName", openPath.split("\\\\")[3]);
							 respond.put("format", openPath.split("\\.")[1]);
							 
							 fileServer.handleFileClient(respond, userName);
							 controlPanel.Log("F", "File server handling");
						 }	
						 catch(SQLException e)
						 {
							 e.printStackTrace();
						 }
					 }
					 else if(type.equals(NetworkProtocols.MESSAGE_RICIEVER_SELECT_DELETE_REQUEST))
                     {
                    JSONObject json ;
                        controlPanel.request("MESSAGE_RICIEVER_SELECT_DELETE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
                    if(request.get("reqType").equals("R"))
                    {
                       json = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_RICIEVER_SELECT_DELETE_RESPOND);
                       json.put("resType", "R");
                    }
                    else
                    {
                       json = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_SEND_SELECT_DELETE_RESPOND);
                       json.put("resType", "S");                        
                    }
                    
                    JSONArray jarray = (JSONArray)request.get("delete");
                        
                        for(Object a : jarray)
                        {
                           JSONObject aa = (JSONObject)a;
                           
                           String typemessage = aa.get("No").toString();
                           
                           String qry = "delete from �޼��� where �޼�����ȣ = '"+typemessage+"'";
                           
                           handler.excuteUpdate(qry);
                        }
                        
                        sendProtocol(json);
                        controlPanel.respond("MESSAGE_RICIEVER_SELECT_DELETE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);                       
                     }
					 else if(type.equals(NetworkProtocols.MESSAGE_RICIEVER_ALL_DELETE_REQUEST))
                     {
                        JSONObject json ;
                        controlPanel.request("MESSAGE_RICIEVER_ALL_DELETE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
                        String qry = "";
                        if(request.get("reqType").equals("R"))
                        {
                           json = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_RICIEVER_ALL_DELETE_RESPOND);
                           qry = "delete from �޼��� where ������ = '"+userIdentify+"' and ����='R';";
                           json.put("resType", "R");
                        }
                        else
                        {
                           json = Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_SEND_ALL_DELETE_RESPOND);
                           qry = "delete from �޼��� where �߽��� = '"+userIdentify+"' and ����='S';";                            
                           json.put("resType", "S");
                        }
                        handler.excuteUpdate(qry);
                        controlPanel.respond("MESSAGE_RICIEVER_ALL_DELETE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
                        sendProtocol(json);
                     }
		                else if(type.equals(NetworkProtocols.LOGOUT_REQUEST))
		                {
		                	System.out.println("�α׾ƿ�");
	                        controlPanel.request("LOGOUT_REQUEST", userIdentify+"-"+clientIP, "COMMIT", request);
	                        controlPanel.Log("M", clientIP+"has logged out");
		                	break;
		                }
		                else if(type.equals(NetworkProtocols.MESSAGE_REPLY_REQUEST))
		                {
		                	String qry = "select �й�, �̸� from ����� where �й�='"+request.get("reqID")+"';";
	                        controlPanel.request("MESSAGE_REPLY_REQUEST", userIdentify+"-"+clientIP, "-----", request);
		                	try
		                	{
			                	ResultSet rs = handler.excuteQuery(qry);
			                	rs.next();
			                	
			                	String[] keys = {"�й�","�̸�"};
			                	Object[] values = {rs.getString("�й�"),rs.getString("�̸�")};
			                	
			                	sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_REPLY_RESPOND, keys, values));
		                        controlPanel.respond("MESSAGE_REPLY_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.MESSAGE_REPLY_RESPOND, keys, values));			                	
		                	}
		                	catch(SQLException e)
		                	{
		                		e.printStackTrace();
		                	}
		                }
		                else if(type.equals(NetworkProtocols.STUDENT_SORT_OVERLAP_REQUEST))
		                  {
		                	  controlPanel.request("STUDENT_SORT_OVERLAP_REQUEST", userIdentify+"-"+clientIP, "-----", request);
		                      int classnum = (int)request.get("class")-1;
		                      int levelnum = (int)request.get("level")-1;
		                      String qry ="";
		                      if(classnum == 0 && levelnum == 0)
		                      {
		                         qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� order by S.���ȣ";
		                      }
		                      else if(classnum == 0)
		                      {
		                         qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.��="+"'"+levelnum+"'order by S.���ȣ";
		                      }
		                      else if(levelnum == 0)
		                      {
		                         qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�="+"'"+classnum+"'order by S.���ȣ";
		                      }
		                      else
		                      {
		                         qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.��="+"'"+levelnum+"' and"+" S.�Ҽ��а� = '"+classnum+"' order by S.���ȣ";
		                      }
		                      ResultSet rs = handler.excuteQuery(qry);
		                      
		                      JSONArray mArr = new JSONArray();
		                      
		                          String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
		                          while(rs.next())
		                          {
		                             Object[] values = {
		                                  rs.getString("�й�"),
		                                  rs.getString("�̸�"),
		                                  rs.getString("�ּ�"),
		                                  rs.getString("�޴�����ȣ"),
		                                  rs.getString("������ȭ��ȣ"),
		                                  rs.getString("�ֹε�Ϲ�ȣ"),
		                                  rs.getString("����"),
		                                  rs.getString("���ȣ"),
		                                  rs.getInt("�г�"),
		                                  rs.getString("�а��̸�")
		                                  };
		                                 mArr.add(Toolbox.createJSONProtocol(keys, values));
		                              }
		                         
		                              // ���ο� ���̽��� �������� ����
		                              JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_SORT_OVERLAP_RESPOND);
		                              // ���̽� ��� ���� 
		                              res.put("user_list", mArr);
		                              sendProtocol(res);   
		                              controlPanel.respond("STUDENT_SORT_OVERLAP_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
		                  }
		                else if(type.equals(NetworkProtocols.MEMBER_JOIN_REQUEST))
		                   {
	                        controlPanel.request("MEMBER_JOIN_REQUEST", userIdentify+"-"+clientIP, "-----", request);
		                      System.out.println("Member Join Info : "+request.toJSONString());
		                      
		                      String qry1 = "insert into ����� values('"+request.get("�й�").toString()+"','"+request.get("�̸�").toString()+"','"
			                            +request.get("��й�ȣ").toString()+"', '�л�');";
			                      
			                  handler.excuteUpdate(qry1);
		                      
		                      String qry = "insert into �л� values('"+request.get("�й�").toString()+"',"+(int)request.get("����")+",'"+request.get("�亯").toString()+"','"+request.get("�ּ�").toString()+"','"
		                            +request.get("�ڵ�����ȣ").toString()+"','"+request.get("������ȭ��ȣ").toString()+"','"+request.get("�ֹε�Ϲ�ȣ").toString()+"','"+request.get("����").toString()+"','"
		                            +request.get("���ȣ").toString()+"',NULL,"+(int)request.get("�г�")+","+(int)request.get("�Ҽ��а�")+",'"+request.get("���ȣ").toString()+"');";
		                      
		                      System.out.println(qry);
		                      handler.excuteUpdate(qry);
		                      
		                      JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.MEMBER_JOIN_RESPOND);
		                        controlPanel.respond("MEMBER_JOIN_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
		                      sendProtocol(json);
		                   }
		                else if(type.equals(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_REQUEST))
						 {
	                        controlPanel.request("STUDENT_CLASS_SELECT_COMBOBOX_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 String check = request.get("comboCheck").toString();
							 if(check.equals("1"))
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 try
				                 {
				                	 while(rs.next())
					                 {
					                    Object[] values = {
					                         rs.getString("�й�"),
					                         rs.getString("�̸�"),
					                         rs.getString("�ּ�"),
					                         rs.getString("�޴�����ȣ"),
					                         rs.getString("������ȭ��ȣ"),
					                         rs.getString("�ֹε�Ϲ�ȣ"),
					                         rs.getString("����"),
					                         rs.getString("���ȣ"),
					                         rs.getInt("�г�"),
					                         rs.getString("�а��̸�")
					                         };
					                        mArr.add(Toolbox.createJSONProtocol(keys, values));
					                     }
				                 }
				                 catch(SQLException e)
				                 {
				                	 e.printStackTrace();
				                 }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);	
				                     controlPanel.respond("STUDENT_CLASS_SELECT_COMBOBOX_REQUEST", userIdentify+"-"+clientIP, "COMMIT", res);
							 }
							 else if(check.equals("2"))
							 {	
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�=1 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
							 else if(check.equals("3"))
							 {	
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�=2 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
							 else if(check.equals("4"))
							 {	
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�=3 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
							 else if(check.equals("5"))
							 {	

								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�=4 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
							 else if(check.equals("6"))
							 {	
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�=5 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
							 else
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.�Ҽ��а�=6 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
		                        controlPanel.respond("STUDENT_CLASS_SELECT_COMBOBOX_REQUEST", userIdentify+"-"+clientIP, "COMMIT", request);
						 }
						 else if(type.equals(NetworkProtocols.STUDENT_LEVEL_SELECT_COMBOBOX_REQUEST))
						 {
							 String check = request.get("comboCheck").toString();
		                     controlPanel.request("STUDENT_LEVEL_SELECT_COMBOBOX_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 
							 if(check.equals("1"))
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);	
							 }
							 else if(check.equals("2"))
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.��=1 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);			 
							 }
							 else if(check.equals("3"))
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.��=2 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);	
							 }
							 else if(check.equals("4"))
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.��=3 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);	
							 }
							 else
							 {
								 String qry = "select S.�й�, A.�̸� ,S.�ּ�,S.�޴�����ȣ, S.������ȭ��ȣ,S.�ֹε�Ϲ�ȣ ,S.���� ,S.���ȣ ,S.�г� ,M.�а��̸�  from �л� S, ����� A, �а�_��� M  where S.�Ҽ��а� = M.�а���ȣ and S.�й� = A.�й� and S.��=4 order by S.���ȣ";
								 
								 ResultSet rs = handler.excuteQuery(qry);
								 
				                 JSONArray mArr = new JSONArray();
				                     
				                 String[] keys = {"�й�","�̸�","�ּ�","�޴�����ȣ","������ȭ��ȣ","�ֹε�Ϲ�ȣ","����","���ȣ","�г�","�Ҽ��а�"};
				                 while(rs.next())
				                 {
				                    Object[] values = {
				                         rs.getString("�й�"),
				                         rs.getString("�̸�"),
				                         rs.getString("�ּ�"),
				                         rs.getString("�޴�����ȣ"),
				                         rs.getString("������ȭ��ȣ"),
				                         rs.getString("�ֹε�Ϲ�ȣ"),
				                         rs.getString("����"),
				                         rs.getString("���ȣ"),
				                         rs.getInt("�г�"),
				                         rs.getString("�а��̸�")
				                         };
				                        mArr.add(Toolbox.createJSONProtocol(keys, values));
				                     }
				                
				                     // ���ο� ���̽��� �������� ����
				                     JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CLASS_SELECT_COMBOBOX_RESPOND);
				                     // ���̽� ��� ���� 
				                     res.put("user_list", mArr);
				                     sendProtocol(res);	
							 }
		                        controlPanel.respond("STUDENT_CLASS_SELECT_COMBOBOX_RESPOND", userIdentify+"-"+clientIP, "COMMIT", request);
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_CATEGORY_DELETE_REQUEST))
						 {
		                        controlPanel.request("ADMIN_CATEGORY_DELETE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 String qry = "select ī�װ����̸� from �Խñ�_ī�װ������;";
							 ResultSet rs = handler.excuteQuery(qry);
							 JSONArray data = new JSONArray();
							 while(rs.next())
							 {
								 data.add(rs.getString("ī�װ����̸�"));
							 }
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_CATEGORY_DELETE_RESPOND);
							 respond.put("category-list", data);
							 sendProtocol(respond);
		                     controlPanel.respond("ADMIN_CATEGORY_DELETE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_DELETE_CATEGORY_COUNT_REQUEST))
						 {
		                     controlPanel.request("ADMIN_DELETE_CATEGORY_COUNT_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 ResultSet rsss = handler.excuteQuery("select ī�װ�����ȣ from �Խñ�_ī�װ������ where ī�װ����̸� = '"+request.get("category")+"';");
							 rsss.next();
							 String qry = "select count(*) from �Խñ� where ī�װ���="+rsss.getInt("ī�װ�����ȣ")+";";
							 ResultSet rs = handler.excuteQuery(qry);
							 try
							 {
								 rs.next();
								 
								 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_DELETE_CATEGORY_COUNT_RESPOND);
								 respond.put("count", rs.getInt("count(*)"));
								 respond.put("category", request.get("category"));
								 sendProtocol(respond);
			                        controlPanel.respond("ADMIN_DELETE_CATEGORY_COUNT_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
							 }
							 catch(SQLException e)
							 {
								 e.printStackTrace();
							 }
							 
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_DELETE_FINAL_REQUEST))
						 {
		                     controlPanel.request("ADMIN_DELETE_FINAL_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 ResultSet rsss = handler.excuteQuery("select ī�װ�����ȣ from �Խñ�_ī�װ������ where ī�װ����̸� = '"+request.get("category")+"';");
							 rsss.next();
							 String qry = "delete from �Խñ� where ī�װ��� = "+rsss.getInt("ī�װ�����ȣ")+";";
							 System.out.println();
							 handler.excuteUpdate(qry);
							 
							 qry = "delete from �Խñ�_ī�װ������ where ī�װ����̸� = '"+request.get("category")+"';";
							 handler.excuteUpdate(qry);
							 
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_DELETE_FINAL_RESPOND));
		                     controlPanel.respond("ADMIN_DELETE_FINAL_RESPOND", userIdentify+"-"+clientIP, "COMMIT", request);
						 }
						 else if(type.equals(NetworkProtocols.STUDENT_CATEGORY_LIST_REQUEST))
						 {
							 try
							 {
								 controlPanel.request("STUDENT_CATEGORY_LIST_REQUEST", userIdentify+"-"+clientIP, "-----", request);
								 ResultSet rs = handler.excuteQuery("select ī�װ����̸� from �Խñ�_ī�װ������");
								 JSONArray data = new JSONArray();
								 
								 while(rs.next())
								 {
									 data.add(rs.getString("ī�װ����̸�"));
								 }
								 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_CATEGORY_LIST_RESPOND);
								 respond.put("category-list", data);
								 sendProtocol(respond);
								 controlPanel.respond("STUDENT_CATEGORY_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
							 }
							 catch(SQLException e)
							 {
								 e.printStackTrace();
							 }
						 }
						 else if(type.equals(NetworkProtocols.STUDENT_BOARD_SEARCH_REQUEST))
						 {
							 String qType = request.get("�˻�����").toString();
							 String qWord = request.get("�˻���").toString();
							 controlPanel.request("STUDENT_BOARD_SEARCH_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 String qry = "";
							 
							 if(qType.equals("�ۼ���"))
							 {
								  qry = "select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ�����, G.�Խñۺ���, M.ī�װ����̸� from ����� S, �Խñ� G, �Խñ�_ī�װ������ M "
				                         + "where S.�й�=G.�ۼ��� and M.ī�װ�����ȣ=G.ī�װ��� and G.�ۼ��� = (select �й� from ����� where �̸� = '"+qWord+"');";
							 }
							 else if(qType.equals("����"))
							 {
								 qry = "select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ�����, G.�Խñۺ���, M.ī�װ����̸� from ����� S, �Խñ� G, �Խñ�_ī�װ������ M "
				                         + "where S.�й�=G.�ۼ��� and M.ī�װ�����ȣ=G.ī�װ��� and G.�Խñ����� like '%"+qWord+"%';";
							 }
							 else if(qType.equals("����"))
							 {
								 qry = "select G.�Խñ۹�ȣ, S.�̸�, G.�Խñ�����, G.�ۼ�����, G.�Խñۺ���, M.ī�װ����̸� from ����� S, �Խñ� G, �Խñ�_ī�װ������ M "
				                         + "where S.�й�=G.�ۼ��� and M.ī�װ�����ȣ=G.ī�װ��� and G.�Խñۺ��� like '%"+qWord+"%';";
							 }
							 System.out.println(qry);
							ResultSet rs = handler.excuteQuery(qry);
							
							if(Toolbox.getResultSetSize(rs)==0)
							{
								sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.BOARD_NO_SEARCH_RESULT));
							}
							else
							{
								JSONArray arr = new JSONArray();
								JSONArray arr2 = new JSONArray();
								
								String[] keys = {"No","�̸�", "�Խñ�����", "�ۼ�����", "�Խñۺ���", "ī�װ���"};
								try
								{
									while(rs.next())
									{
										Object[] o = {rs.getInt("�Խñ۹�ȣ"),
													  rs.getString("�̸�"),
												      rs.getString("�Խñ�����"),
												      rs.getDate("�ۼ�����"),
												      rs.getString("�Խñۺ���"),
												      rs.getString("ī�װ����̸�")}; 
										
										JSONObject n = Toolbox.createJSONProtocol(keys, o);
										arr.add(n);								
									}
								}
								catch(SQLException e)
								{
									e.printStackTrace();
								}
								
								String qry2 = "select ī�װ����̸� from �Խñ�_ī�װ������;";
								
								ResultSet rs2 = handler.excuteQuery(qry2);
								
								try
								{
									while(rs2.next())
									{
										arr2.add(rs2.getString(1));
									}
								}
								catch(SQLException e)
								{
									e.printStackTrace();
								}
								
								JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.BOARD_LIST_RESPOND);
								res.put("board_list", arr);
								res.put("category_list", arr2);
								sendProtocol(res);
								controlPanel.respond("BOARD_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
							}
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_SUBMIN_MAIN_REQUEST))
						 {
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_SUBMIN_MAIN_RESPOND);
							 controlPanel.request("ADMIN_SUBMIN_MAIN_REQUEST", userIdentify+"-"+clientIP, "-----", request);							 
							 // �������� ������ ���� ���
							 if(Toolbox.getResultSetSize(handler.excuteQuery("select * from ���⼭�����"))==0)
							 {
								 respond.put("submit-process", "no-data");
							 }
							 // �ִ°��
							 else
							 {
								 respond.put("submit-process", "exist-data");
								 
								 ResultSet rs = handler.excuteQuery("select ����з���, �����ð� from ���⼭�����;");
								 rs.next();
								 respond.put("����з���", rs.getString("����з���"));
								 respond.put("�����ð�", rs.getDate("�����ð�"));
								 
								 ResultSet rs2 = handler.excuteQuery("select M.������ȣ,M.������,S.�̸�,M.���⼭������URL,M.����ð� from ���⼭���� M, ����� S where M.������=S.�й�;");					 
								 JSONArray data = new JSONArray();
								 try
								 {
									 while(rs2.next())
									 {
										 JSONObject rawData = null;
										 String[] keys = {"������ȣ","�й�","�̸�","���⿩��","����ð�"};
										 
										 if(rs2.getString("���⼭������URL")==null)
										 {
											 Object[] values = {rs2.getInt("������ȣ"),
													 			rs2.getString("������"),
													 			rs2.getString("�̸�"),
													 			"������",
													 			"-------"};
											 rawData = Toolbox.createJSONProtocol(keys,values);
										 }
										 else
										 {
											 Object[] values = {rs2.getInt("������ȣ"),
													 			rs2.getString("������"),
											 					rs2.getString("�̸�"),
											 					"����",
											 					rs2.getTimestamp("����ð�")};
											 rawData = Toolbox.createJSONProtocol(keys,values);
										 }
										 data.add(rawData);
									 }
								 }
								 catch(SQLException e)
								 {
									 e.printStackTrace();
								 }
								 respond.put("data-bundle", data);
							 }
							 controlPanel.respond("ADMIN_SUBMIT_MAIN_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
							 sendProtocol(respond);
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_SUBMIT_ENROLL_REQUEST))
						 {
							 JSONObject data = (JSONObject)request.get("data");
							 String title = data.get("title").toString();
							 Date date = (Date)data.get("date");
							 controlPanel.request("ADMIN_SUBMIT_ENROLL_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd HH:mm");
							 String dateString = d.format(date);
							 
							 String qry = "insert into ���⼭�����(�з���ȣ,����з���,�����ð�) values(1,'"+title+"','"+dateString+"');";
							 handler.excuteUpdate(qry);
							 
							 ResultSet rs = handler.excuteQuery("select �й� from �л�;");
							 int count = 0;
							 while(rs.next())
							 {
								 handler.excuteUpdate("insert into ���⼭����(������ȣ,������,�з�) values("+count+",'"+rs.getString("�й�")+"',1);");
								 count++;
							 }
							 controlPanel.respond("ADMIN_SUBMIT_ENROLL_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_SUBMIT_ENROLL_RESPOND));
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_SUBMIT_ENROLL_RESPOND));
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_SUBMIT_DISPOSE_ASK_REQEUST))
						 {
							 controlPanel.request("ADMIN_SUBMIT_DISPOSE_ASK_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_SUBMIT_DISPOSE_ASK_RESPOND);
							 if(Toolbox.getResultSetSize(handler.excuteQuery("select * from ���⼭���� where ���⼭������URL is NULL;"))!=0)
							 {
								 respond.put("result", "re-ask");
							 }
							 else
							 {
								 respond.put("result", "ok");
							 }
							 sendProtocol(respond);
							 controlPanel.respond("ADMIN_SUBMIT_DISPONSE_ASK_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_SUBMIT_DISPOSE_REQEUST))
						 {
							 handler.excuteUpdate("delete from ���⼭����;");
							 handler.excuteUpdate("delete from ���⼭�����;");
							 // C:\DIMS\SubmittedData ���� ���ϵ� �� ������
							 controlPanel.request("ADMIN_SUBMIT_DISPOSE_REQUEST", userIdentify+"-"+clientIP, "-----", request);							 
							 for(File target : new File(Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY).listFiles())
							 {
								 target.delete();
							 }
							 
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_SUBMIT_DISPOSE_RESPOND));
							 controlPanel.respond("ADMIN_SUBMIT_DISPOSE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_SUBMIT_DISPOSE_RESPOND));
						 }
						 else if(type.equals(NetworkProtocols.STUDENT_SUBMIT_REQUEST))
						 {
							 controlPanel.request("STUDENT_SUBMIT_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 String filename = Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY+userIdentify+"_submitted."+request.get("Ȯ����");
							 byte[] data = (byte[])request.get("������");
							 ResultSet rs = handler.excuteQuery("select ���⼭������URL from ���⼭���� where ������ = '"+userIdentify+"';");
							 rs.next();
							 if(rs.getString("���⼭������URL")!=null)
							 {
								 File deleteTarget = new File(rs.getString("���⼭������URL"));
								 deleteTarget.delete();
							 }
							 
							 Files.write(new File(filename).toPath(), data);
							 controlPanel.Log("C", "Save file at "+filename);
							 handler.excuteUpdate("update ���⼭���� set ���⼭������URL = '"+filename+"' where ������ = '"+userIdentify+"';");
							 handler.excuteUpdate("update ���⼭���� set ����ð� = now() where ������ = '"+userIdentify+"';");							 
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_SUBMIT_RESPOND));
							 controlPanel.respond("STUDENT_SUBMIT_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_SUBMIT_RESPOND));
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_LOCAL_SAVE_REQUEST))
						 {
							 controlPanel.request("ADMIN_LOCAL_SAVE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 new Thread(()->{
								 try
								 {
									 ByteArrayOutputStream bos = new ByteArrayOutputStream();
									 String zipFileName = Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY+"submitted_data.zip";
									 ZipOutputStream zos = new ZipOutputStream(bos);
									 File[] fList = new File(Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY).listFiles();
									 for(int i=0;i<fList.length;i++)
									 {
										 ZipEntry zipHandle = new ZipEntry(fList[i].toPath().getFileName().toString());
										 zos.putNextEntry(zipHandle);
										 controlPanel.Log("C","Zipping...."+(i+1)+"/"+(fList.length));
										 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_LOCAL_SAVE_RESPOND);
										 respond.put("progress-current", (i+1));
										 respond.put("progress-max", fList.length);
										 sendProtocol(respond);										 
										 zos.write(Files.readAllBytes(fList[i].toPath()));
									 }
									 bos.close();
									 zos.close();
									 
									 byte[] sendable = bos.toByteArray();
									 
									 JSONObject notification = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_LOCAL_SAVE_NOTIFICATION);
									 notification.put("name", zipFileName);
									 notification.put("size", sendable.length);
									 sendProtocol(notification);
									 
									 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.STUDENT_MEDIA_CONTENT_RESPOND);
									 respond.put("data", sendable);
									 respond.put("fileName", zipFileName.split("\\\\")[3]);
									 respond.put("format", zipFileName.split("\\.")[1]);
									 
									 fileServer.handleFileClient(respond, userName);
								 }
								 catch(IOException e)
								 {
									 e.printStackTrace();
								 }
							 }).start();
						 }
						 else if(type.equals(NetworkProtocols.EMAIL_SEND_REQUEST))
						 {
							 JSONObject mailData = request;
							 controlPanel.request("EMAIL_SEND_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 boolean constraint = true;
							 
							 ArrayList<File> attached = new ArrayList<File>();
							 for(File target : new File(Statics.DEFAULT_SUBMITTED_DATA_DIRECTORY).listFiles())
							 {
								 if(Files.readAllBytes(target.toPath()).length>1024*1024*10)
								 {
									 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.EMAIL_FILE_SIZE_LIMIT));
									 controlPanel.respond("EMAIL_FILE_SIZE_LIMIT", userIdentify+"-"+clientIP, "DENY", Toolbox.createJSONProtocol(NetworkProtocols.EMAIL_FILE_SIZE_LIMIT));
									 constraint = false;
									 break;
								 }
								 attached.add(target);
							 }
							 
							 if(attached.size()>10)
							 {
								 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.EMAIL_MAX_COUNT_EXCEED));
								 controlPanel.respond("EMAIL_MAX_COUNT_EXCEED", userIdentify+"-"+clientIP, "DENY", Toolbox.createJSONProtocol(NetworkProtocols.EMAIL_MAX_COUNT_EXCEED));
								 constraint = false;
							 }
							 
							 if(constraint)
							 {
								 new Thread(()->{
									 
									 try
									 {
										 File[] files = new File[attached.size()];
										 files = attached.toArray(files);
										 
										 MailProperties m = MailProperties.createNaverMailProperty()
							  					 .addProperty(MailProperty.PORT, 465)
												 .addProperty(MailProperty.TITLE, mailData.get("��������"))
												 .addProperty(MailProperty.CONTENT, mailData.get("���Ϻ���"))
												 .addProperty(MailProperty.ATTACHED_FILE, files);
										 controlPanel.Log("C", "Start send email..."+mailData.get("�������"));
										 MailingService.sendMail(m, mailData.get("�������").toString());
										 controlPanel.Log("C", "Finish sending email.");
										 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.EMAIL_SEND_RESPOND));
										 controlPanel.respond("EMAIL_SEND_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.EMAIL_SEND_RESPOND));
									 }
									 catch(IOException e)
									 {
										 e.printStackTrace();
									 }
									 
								 }).start();
							 }
						 }
						 else if(type.equals(NetworkProtocols.PASSWORD_FIND_IDENTIFY_REQUEST))
						 {
							 ResultSet rs = handler.excuteQuery("select ��й�ȣã��_���� as ��, ��й�ȣã��_�亯 as �� from �л� where �й� = '"+request.get("�й�")+"';");
							 controlPanel.request("PASSWORD_FIND_IDENTIFY_REQUEST", userIdentify+"-"+clientIP, "-----", request);				
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.PASSWORD_FIND_IDENTIFY_RESPOND); 
							 
							 if(Toolbox.getResultSetSize(rs)==0)
							 {
								 // �й��� �߸��� ���
								 respond.put("identify-result", "fault");
								 controlPanel.respond("PASSWORD_FIND_IDENTIFY_RESPOND", userIdentify+"-"+clientIP, "DENY", respond);
							 }
							 else
							 {
								 // ��, ��  = ����, �亯
								 rs.next();
								 if(rs.getInt("��")==(int)request.get("����"))
								 {
									 if(rs.getString("��").equals(request.get("�亯").toString()))
									 {
										 respond.put("identify-result", "commit");
										 controlPanel.respond("PASSWORD_FIND_IDENTIFY_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
									 }
									 else
									 {
										 respond.put("identify-result", "fault");
										 controlPanel.respond("PASSWORD_FIND_IDENTIFY_RESPOND", userIdentify+"-"+clientIP, "DENY", respond);
									 }
								 }
								 else
								 {
									 respond.put("identify-result", "fault");
									 controlPanel.respond("PASSWORD_FIND_IDENTIFY_RESPOND", userIdentify+"-"+clientIP, "DENY", respond);
								 }
							 }
							 sendProtocol(respond);
						 }
						 else if(type.equals(NetworkProtocols.PASSWORD_FIND_MODIFY_REQUEST))
						 {
							 controlPanel.request("PASSWORD_FIND_MODIFY_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 handler.excuteUpdate("update ����� set ��й�ȣ = '"+request.get("request-pw")+"' where �й� = '"+request.get("request-id")+"';");
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.PASSWORD_FIND_MODIFY_RESPOND));
							 controlPanel.respond("PASSWORD_FIND_MODIFY_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.PASSWORD_FIND_MODIFY_RESPOND));
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_VIDEO_UPLOAD_REQUEST))
						 {
							 controlPanel.request("ADMIN_VIDEO_UPLOAD_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 controlPanel.Log("C", "File upload handling...");
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.VIDEO_DATA_SEND_NOTIFICATION));
							 fileServer.handleUploadClient(request, handler,()->{
								 try {
									 controlPanel.respond("ADMIN_VIDEO_UPLOAD_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_VIDEO_UPLOAD_RESPOND));
									sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_VIDEO_UPLOAD_RESPOND));
								} catch (Exception e) {
									e.printStackTrace();
								}
							 });
						 }
						 else if(type.equals(NetworkProtocols.ROOM_NUMBER_MODIFY_REQUEST))
						 {
							 controlPanel.request("ROOM_NUMBER_MODIFY_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 handler.excuteUpdate("update �л� set ���ȣ = '"+request.get("reqRoomNum")+"' where �й� = '"+request.get("reqId")+"';");
							 handler.excuteUpdate("update �л� set �� = '"+request.get("reqRoomNum").toString().charAt(0)+"' where �й� = '"+request.get("reqId")+"';");
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ROOM_NUMBER_MODIFY_RESPOND));
							 controlPanel.respond("ROOM_NUMBER_MODIFY_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.ROOM_NUMBER_MODIFY_RESPOND));
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_VIDEO_LIST_REQUEST))
						 {
							 controlPanel.request("ADMIN_VIDEO_LIST_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 ResultSet rs = handler.excuteQuery("select * from �������");
							 
							 JSONObject respond = Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_VIDEO_LIST_RESPOND);
							 
							 JSONArray data = new JSONArray();
							 
							 while(rs.next())
							 {
								 String[] keys = {"������ȣ","�����̸�","����ũ��"};
								 Object[] values = {rs.getInt("������ȣ"),rs.getString("�����̸�"),rs.getInt("����ũ��")};
								 
								 data.add(Toolbox.createJSONProtocol(keys, values));
							 }
							 respond.put("data", data);
							 sendProtocol(respond);
							 controlPanel.respond("ADMIN_VIDEO_LIST_RESPOND", userIdentify+"-"+clientIP, "COMMIT", respond);
						 }
						 else if(type.equals(NetworkProtocols.ADMIN_VIDEO_DELETE_REQUEST))
						 {
							 int deleteIdx = (int)request.get("reqId");
							 controlPanel.request("ADMIN_VIDEO_DELETE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
							 handler.excuteUpdate("delete from ������� where ������ȣ = "+deleteIdx);
							 sendProtocol(Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_VIDEO_DELETE_RESPOND));
							 controlPanel.respond("ADMIN_VIDEO_DELETE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", Toolbox.createJSONProtocol(NetworkProtocols.ADMIN_VIDEO_DELETE_RESPOND));
						 }
						 else if(type.equals(NetworkProtocols.MAIN_MESSAGE_REQUEST))
		                   {
		                      try
		                          {
		                    	  controlPanel.request("MAIN_MESSAGE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
		                              String qry = "select M.�޼�����ȣ, S.�̸�, S.�й� , M.�޼�������, M.�޼������� ,M.�߽Žð� from �޼��� M, ����� S where M.������='"+userIdentify+"' and M.�߽���=S.�й� and M.����='R' order by �߽Žð� asc" ;
		                              
		                              ResultSet rs = handler.excuteQuery(qry);
		                              JSONArray mArr = new JSONArray();
		                              
		                              String[] keys = {"No","�߽���","�й�","�޼�������","�޼�������","�߽Žð�"};
		                                 
		                              while(rs.next())
		                              {
		                                 Object[] values = {
		                                       rs.getString("�޼�����ȣ"),
		                                       rs.getString("�̸�"),
		                                       rs.getString("�й�"),
		                                       rs.getString("�޼�������"),
		                                       rs.getString("�޼�������"),
		                                       rs.getDate("�߽Žð�")
		                                     
		                                 };
		                                 mArr.add(Toolbox.createJSONProtocol(keys, values));
		                               }
		                              
		                              JSONObject res = Toolbox.createJSONProtocol(NetworkProtocols.MAIN_MESSAGE_RESPOND);
		                              res.put("message_list", mArr);
		                              sendProtocol(res);
		                              controlPanel.respond("MAIN_MESSAGE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", res);
		                          }
		                          catch(Exception e)
		                          {
		                             e.printStackTrace();
		                          }
		                   }
		                  //����
		                   else if(type.equals(NetworkProtocols.MAIN_SCHEDULE_REQUEST))
		                   {
		                	  controlPanel.request("MAIN_SCHEDULE_REQUEST", userIdentify+"-"+clientIP, "-----", request);
		                      String qry = "select * from ����";
		                      JSONArray jsonArray = new JSONArray();
		                      
		                      ResultSet rs = handler.excuteQuery(qry);
		                      String[] keys = {"������ȣ","�����̸�","�Ͻ�","�з�","�Ͻ�"};
		                      while(rs.next())
		                      {
		                         Object[] values = {rs.getInt("������ȣ"),
		                                        rs.getString("�����̸�"), 
		                                        rs.getTimestamp("�Ͻ�"),
		                                        rs.getString("�з�"),
		                                        rs.getTimestamp("�Ͻ�")};
		                         jsonArray.add(Toolbox.createJSONProtocol(keys, values));
		                      }
		                      
		                      JSONObject json = Toolbox.createJSONProtocol(NetworkProtocols.MAIN_SCHEDULE_RESPOND);
		                      json.put("schedule", jsonArray);
		                      controlPanel.respond("MAIN_SCHEDULE_RESPOND", userIdentify+"-"+clientIP, "COMMIT", json);
		                      sendProtocol(json);
		                   }
		                else
						{
		                	if(PRINT_LOG)System.out.println("\t\t\t�߸��� ��û");
						}
				}
			}
			catch (IOException|SQLException e)
			{
				/* ����ڰ� �����Ѱ��� */
				e.printStackTrace();
				if(PRINT_LOG)System.out.println("\t\t[Connector-"+userName+"] ����� ���� ����, ������ ����");
				controlPanel.Log("C", "Disconnect a client::"+clientIP);
			}
			finally
			{
				try
				{
					fromClient.close();
					toClient.close();

				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				controlPanel.disconnect(clientIP);
				System.out.println("Ŭ���̾�Ʈ ������ ����");
			}
			
		}
		
		public void sendProtocol(JSONObject protocol) throws IOException
		{
			synchronized (toClient) {
				toClient.writeObject(protocol);
				toClient.flush();	
			}
		}
		
	}

	class Controller extends Thread
	{
		@Override
		public void run() {			
			@SuppressWarnings("resource")
			java.util.Scanner sc = new java.util.Scanner(System.in);
			String command = "";
			
			System.out.println("DIMS Server Controller");
			System.out.println("/? or help - show command list, start server - Run server, exit - exit program");
			while(true)
			{
				System.out.print("Input >> ");
				command = sc.nextLine();
				
				if(command.equals("exit"))
				{
					System.exit(0);
				}
				else if(command.equals("/?")||command.equals("help"))
				{
					System.out.println("print help list");
				}
				else if(command.equals("set PRINT_LOG = true"))
				{
					PRINT_LOG = true;
				}
				else if(command.equals("set PRINT_LOG = false"))
				{
					PRINT_LOG = false;
				}
				else if(command.equals("show clients"))
				{
					System.out.println("Current connected clients");
					System.out.println("Clients count : "+ clients.size());
					for(ConnectedClient c : clients)
					{
						System.out.println("ClientID   : "+c.getClientID());
						System.out.println("ClientName : "+c.getClientName());
					}
				}
				else if(command.equals("start server"))
				{
					serverOpen();
				}
				else
				{
					System.out.println("Invalid Command");
				}
			}
		}
	}
	
	public static void main(String[] args)
	{
		
		//DIMS_Server s = new DIMS_Server();
		
		//s.serverOpen();
		
	}
	
}