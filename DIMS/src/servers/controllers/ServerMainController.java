package servers.controllers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.ResourceBundle;
import com.orsoncharts.util.json.JSONObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import servers.BackupHandler;
import servers.DIMS_Server;
import tools.Statics;
import tools.Toolbox;

public class ServerMainController implements Initializable {

	private static DIMS_Server server;
	private static boolean IS_RUNNING = false;
	
	@FXML Label ipField, portField, runningField, desField, versionField;
	@FXML ListView<HBox> logList, requestList, connectedList;
	@FXML ToggleButton controlBtn;
	
	ObservableList<HBox> logs, requests, connecteds;
	
	BackupHandler backuper;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		
		desField.setText("Create_at - 2016-06-20");
		versionField.setText("Version - 1.0");
		
		try
		{
			ipField.setText(InetAddress.getLocalHost().getHostAddress());
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
		portField.setText(Statics.DIMS_SERVER_PORT_NUMBER+"");
	
		logs = FXCollections.observableArrayList();
		requests = FXCollections.observableArrayList();
		connecteds = FXCollections.observableArrayList();
		
		logList.setItems(logs);
		requestList.setItems(requests);
		connectedList.setItems(connecteds);
		
		backuper = new BackupHandler();
		
		new Thread(()->{
			
			while(true)
			{
				try {
					backuper.backup(logs, requests, connecteds);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				synchronized (this) {
					try
					{
						this.wait(5000);
					}
					catch(InterruptedException e)
					{
						e.printStackTrace();
					}
				}
				
			}
			
		}).start();
		
	}
	
	public void on()
	{
		Platform.runLater(()->{
			runningField.setStyle("-fx-background-color : green;");
			controlBtn.setSelected(true);
			IS_RUNNING = true;
		});
	}
	
	public void off()
	{
		Platform.runLater(()->{
			runningField.setStyle("-fx-background-color : red;");
			controlBtn.setSelected(false);
			IS_RUNNING = false;
		});	
	}
	
	@FXML private void onRunning(ActionEvent e)
	{
		
		if(IS_RUNNING==false)
		{
			server = new DIMS_Server(this);
			server.serverOpen();
		}
		else
		{
			server.serverClose();
			off();
		}
	}
	
	@FXML private void onStart()
	{
		server = new DIMS_Server(this);
		server.serverOpen();
		on();
	}
	
	@FXML private void onStop()
	{
		off();
	}
	
	public void Log(String logType, String descript)
	{
		// logList에 추가
		// ID, Type, Description, Time
		// 50, 50, 289, 164
		// 572
		// 
		Platform.runLater(()->{
			int add = logs.size()+1;
			String dateString = Toolbox.getCurrentTimeFormat(new Date(System.currentTimeMillis()), "HH:mm:ss yyMMdd");
			
			HBox item = new HBox();
			item.setPrefWidth(572);
			
			Label id = getFeaturedLabel(add+"", 28, Font.font("consolas",15), Pos.CENTER, true);
			Label type = getFeaturedLabel(logType, 40, Font.font("consolas",15), Pos.CENTER, true);
			Label desc = getFeaturedLabel(descript, 352, Font.font("consolas",15), Pos.BASELINE_LEFT, true);
			Label date = getFeaturedLabel(dateString, 127, Font.font("consolas",15), Pos.CENTER, true);
			
			switch(logType)
			{
			case "E" :  desc.setTextFill(Paint.valueOf("red")); break;
			case "W" : desc.setTextFill(Paint.valueOf("green")); break;
			case "F" : desc.setTextFill(Paint.valueOf("blue")); break;
			case "C" : desc.setTextFill(Paint.valueOf("purple")); break;
			}
			
			Separator s1 = new Separator(Orientation.VERTICAL);
			Separator s2 = new Separator(Orientation.VERTICAL);
			Separator s3 = new Separator(Orientation.VERTICAL);
			item.getChildren().addAll(id, s1, type, s2, desc, s3, date);
			logs.add(item);
			logList.refresh();
		});
		
	}
	
	public void request(String type1, String clientID, String status, JSONObject request)
	{
		Platform.runLater(()->{
			
			int add = requests.size()+1;

			HBox item = new HBox();
			
			item.setPrefWidth(572);
			
			Label id = getFeaturedLabel(add+"", 28, Font.font("consolas",15), Pos.CENTER, true);
			Label type = getFeaturedLabel(type1.toString(), 272, Font.font("consolas",15), Pos.CENTER, true);
			Label desc = getFeaturedLabel(clientID, 172, Font.font("consolas",15), Pos.CENTER, true);
			Label date = getFeaturedLabel(status, 75, Font.font("consolas",15), Pos.CENTER, true);
			
			if(status.equals("COMMIT"))
			{
				date.setTextFill(Paint.valueOf("green"));
			}
			else if(status.equals("DENY"))
			{
				date.setTextFill(Paint.valueOf("red"));				
			}
			else if(status.equals("UI"))
			{
				type.setTextFill(Paint.valueOf("blue"));
				date.setTextFill(Paint.valueOf("blue"));
			}
			
			Separator s1 = new Separator(Orientation.VERTICAL);
			Separator s2 = new Separator(Orientation.VERTICAL);
			Separator s3 = new Separator(Orientation.VERTICAL);
			item.getChildren().addAll(id, s1, type, s2, desc, s3, date);
			requests.add(item);
			requestList.refresh();
		});
	}
	
	public void respond(String type1, String clientID, String status, JSONObject request)
	{
		Platform.runLater(()->{
			
			int add = requests.size()+1;

			HBox item = new HBox();
			
			item.setPrefWidth(572);
			
			Label id = getFeaturedLabel(add+"", 28, Font.font("consolas",15), Pos.CENTER, true);
			Label type = getFeaturedLabel(type1.toString(), 272, Font.font("consolas",15), Pos.CENTER, true);
			Label desc = getFeaturedLabel(clientID, 172, Font.font("consolas",15), Pos.CENTER, true);
			Label date = getFeaturedLabel(status, 75, Font.font("consolas",15), Pos.CENTER, true);
			
			if(status.equals("COMMIT"))
			{
				date.setTextFill(Paint.valueOf("green"));
			}
			else if(status.equals("DENY"))
			{
				date.setTextFill(Paint.valueOf("red"));				
			}
			else if(status.equals("UI"))
			{
				type.setTextFill(Paint.valueOf("blue"));
				date.setTextFill(Paint.valueOf("blue"));
			}
			
			Separator s1 = new Separator(Orientation.VERTICAL);
			Separator s2 = new Separator(Orientation.VERTICAL);
			Separator s3 = new Separator(Orientation.VERTICAL);
			item.getChildren().addAll(id, s1, type, s2, desc, s3, date);
			requests.add(item);
			requestList.refresh();
		});
	}
	
	public void connect(String ipAddress, String clientName, String grade, String status)
	{
		Platform.runLater(()->{
			
			HBox item = new HBox();
			
			item.setPrefWidth(572);
			
			Label id = getFeaturedLabel(ipAddress, 172, Font.font("consolas",15), Pos.CENTER, true);
			Label type = getFeaturedLabel(clientName, 177, Font.font("consolas",15), Pos.CENTER, true);
			Label desc = getFeaturedLabel(grade, 100, Font.font("consolas",15), Pos.CENTER, true);
			Label date = getFeaturedLabel(status, 100, Font.font("consolas",15), Pos.CENTER, true);
			
			if(grade.equals("관리자"))
			{
				type.setTextFill(Paint.valueOf("blue"));
			}
			
			Separator s1 = new Separator(Orientation.VERTICAL);
			Separator s2 = new Separator(Orientation.VERTICAL);
			Separator s3 = new Separator(Orientation.VERTICAL);
			item.getChildren().addAll(id, s1, type, s2, desc, s3, date);
			connecteds.add(item);
			connectedList.refresh();
		});
	}
	
	public void disconnect(String ip)
	{
		Platform.runLater(()->{
			connecteds.forEach((item)->{
				if(((Label)item.getChildren().get(0)).getText().equals(ip))
				{
					((Label)item.getChildren().get(6)).setText("disconnect");
					connectedList.refresh();
					return;
				}
			});
		});
	}
	
	private Label getFeaturedLabel(String lab, int width, Font f, Pos aliment, boolean visible)
	{
		Label label = new Label(lab);
		label.setPrefWidth(width);
		label.setFont(f);
		label.setAlignment(aliment);
		label.setVisible(visible);
		return label;
	}
	
}
