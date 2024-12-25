This app is a prototype for creating a chat application using websockets.

To Run the application, clone to your local.
build the application : mvn clean package
Run the websocket server using this : mvn exec:java -Dexec.mainClass="com.chat.server.Main"
And Open the index.html in differnt webbrowser tabs, each tab will act as a user, give your name. 
the allows to send direct messages, and allows to create groups and sending group messages
