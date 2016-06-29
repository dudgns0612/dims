package servers;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import servers.controllers.ServerMainController;

public class ServerControlPanel extends Application{

	@Override
	public void start(Stage primaryStage) throws Exception {
		
		FXMLLoader loader = new FXMLLoader(getClass().getResource("./ui/ServerMainGUI.fxml"));
		Parent root = loader.load();
		
		ServerMainController con = (ServerMainController)loader.getController();
		
		primaryStage.setTitle("DIMS Server Control Panel");
		primaryStage.setScene(new Scene(root));
		primaryStage.show();
	}
	
	public static void main(String[] args) {
		Application.launch(ServerControlPanel.class);
	}
	
}
