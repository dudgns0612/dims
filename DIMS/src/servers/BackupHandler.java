package servers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Date;

import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import tools.Statics;
import tools.Toolbox;

public class BackupHandler {

	private BufferedWriter bos;
	
	public BackupHandler()
	{
		if(!new File(Statics.DEFAULT_LOG_BACKUP_PATH+"logs.txt").exists())
		{
			try
			{
				new File(Statics.DEFAULT_LOG_BACKUP_PATH+"logs.txt").createNewFile();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		
		try {
			bos = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Statics.DEFAULT_LOG_BACKUP_PATH+"logs.txt")));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void backup(ObservableList<HBox> log, ObservableList<HBox> requests, ObservableList<HBox> connected) throws IOException
	{
		bos = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Statics.DEFAULT_LOG_BACKUP_PATH+"logs.txt")));
		bos.write("latest backup date : "+Toolbox.getCurrentTimeFormat(new Date(System.currentTimeMillis()), "yyyy-MM-dd HH:mm:ss"));
		bos.newLine();
		bos.write("====================================");
		bos.newLine();
		bos.write("Logs");
		bos.newLine();
		bos.write("====================================");
		bos.newLine();
		bos.write("Time\t\t\tType\t\t\tDescription");
		bos.newLine();
		log.forEach((item)->{
			
			try {
				bos.write(getLogsWritableFromListColumn(item));
				bos.newLine();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		});
		
		bos.write("====================================");
		bos.newLine();
		bos.write("requests and responds");
		bos.newLine();
		bos.write("====================================");
		bos.newLine();
		bos.write("Time\t\t\tType\t\t\tClientID\t\t\tStatus");
		bos.newLine();
		requests.forEach((item)->{
			
			try
			{
				bos.write(getWorksWritableFromListColumn(item));
				bos.newLine();
			} 
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
		
		bos.write("====================================");
		bos.newLine();
		bos.write("Connected clients");
		bos.newLine();
		bos.write("====================================");
		bos.newLine();
		bos.write("IP Address\t\t\tName\t\t\tGrade\t\t\tStatus");
		bos.newLine();
		connected.forEach((item)->{
			
			try
			{
				bos.write(getConWritableFromListColumn(item));
				bos.newLine();
			} 
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
		bos.flush();
	}
	
	public String getLogsWritableFromListColumn(HBox item)
	{
		String time = ((Label)item.getChildren().get(6)).getText();
		String type = ((Label)item.getChildren().get(2)).getText();
		String desc = ((Label)item.getChildren().get(4)).getText();
		return time+"\t\t\t"+type+"\t\t\t"+desc;
	}
	
	public String getWorksWritableFromListColumn(HBox item)
	{
		String time = Toolbox.getCurrentTimeFormat(new Date(System.currentTimeMillis()), "yyyy-MM-dd HH:mm:ss");
		String type = ((Label)item.getChildren().get(2)).getText();
		String desc = ((Label)item.getChildren().get(4)).getText();
		String status = ((Label)item.getChildren().get(6)).getText();
		
		if(type.length()>13&&type.length()<=20)
		{
			return time+"\t\t\t"+type+"\t\t"+desc+"\t\t\t"+status;
		}
		else if(type.length()>20)
		{
			return time+"\t\t\t"+type+"\t"+desc+"\t\t\t"+status;
		}
		else
		{
			return time+"\t\t\t"+type+"\t\t\t"+desc+"\t\t\t"+status;
		}
		
		//return time+"\t\t\t"+type+"\t\t\t"+desc+"\t\t\t"+status;
	}
	
	public String getConWritableFromListColumn(HBox item)
	{
		String type = ((Label)item.getChildren().get(0)).getText();
		String desc = ((Label)item.getChildren().get(2)).getText();
		String status = ((Label)item.getChildren().get(4)).getText();
		String status2 = ((Label)item.getChildren().get(6)).getText();
		return type+"\t\t\t"+desc+"\t\t\t"+status+"\t\t\t"+status2;
	}
	
}
