import java.net.InetAddress;
import java.net.UnknownHostException;

public class Server {
	public static volatile FileSystem Fs = new FileSystem(findHostName());
	public static String serverName = findHostName();

	static String findHostName() {
		String hostname = "Unknown";

		try {
			InetAddress addr;
			addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (UnknownHostException ex) {
			log("Hostname can not be resolved");
		}
		return hostname;
	}

	synchronized static Message requestHandler(Message msg) {
		String fname = serverName + "/" + msg.fileName + msg.chunkNo;

		Message reply = new Message(msg.type, Message.STATUS_FAIL,
				msg.fileName, msg.chunkNo, msg.offSet, msg.len, null);

		switch (msg.type) {
		case Message.APPEND:

			int fileSize = FileOperations.countCharsBuffer(fname, "US-ASCII");
			StringBuilder sBuf = new StringBuilder();
			String data = null;

			if (msg.data == null) {
				for (int i = 0; i < 8192 - fileSize; i++) {
					sBuf.append("\0");
				}
				data = sBuf.toString();
			} else {
				data = msg.data;
			}

			if (FileOperations.writeFile(fname, fileSize, data)) {
				Fs.addFile(msg.fileName, msg.chunkNo, fileSize + data.length());
				reply.status = Message.STATUS_SUCCESS;
				reply.data = "Successfully appended";
			} else {
				reply.data = "Failed to append file on disk";
			}
			break;
		case Message.WRITE:
			if (FileOperations.writeFile(fname, 0, msg.data)) {
				Fs.addFile(msg.fileName, msg.chunkNo, msg.data.length());
				reply.status = Message.STATUS_SUCCESS;
				reply.data = "Successfully written";
			} else {
				reply.data = "Failed to write file on disk";
			}
			break;

		case Message.CREATE:
			if (FileOperations.createFile(fname)) {
				Fs.addFile(msg.fileName, msg.chunkNo, 0);
				reply.status = Message.STATUS_SUCCESS;
				reply.data = "Successfully created";
			} else {
				reply.data = "Failed to create file on disk";
			}
			break;

		case Message.READ:
			reply.data = new String(FileOperations.readFile(fname, msg.offSet,
					msg.len));
			if (reply.data != null) {
				reply.status = Message.STATUS_SUCCESS;
			} else {
				reply.data = "Failed to read from the disk";
			}
			log("Read:" + reply.data);
			break;
			
		case Message.HELLO:
			reply.data = "Alive";
			break;
		}
		System.out.println(reply.toString());
		return reply;
	}

	private static void log(String message) {
		// System.out.println(message);
	}

	public static void main(String[] args) throws Exception {
		int reqPort = Integer.valueOf(Config.getValue(serverName));

		//FileOperations.deleteDirectory(serverName);

		FileOperations.createDirectory(serverName);

		String Filenames[] = FileOperations.listOfFiles(serverName);
		int end = -1;
		for (String s : Filenames) {
			for (int i = s.length() - 1; i >= 0; i--) {
				if (Character.isDigit(s.charAt(i)))
					continue;
				else {
					end = i + 1;
					break;
				}
			}
			Fs.addFile(s.substring(0, end), Integer.valueOf(s.substring(end)),
					FileOperations.countCharsBuffer(serverName + "/" + s,
							"US-ASCII"));
		}

		Thread RequestListener = new Thread(new ListenerWrapper(reqPort,
				ListenerWrapper.REQ));
		RequestListener.start();

		// Server server = new Server();
		Thread hbThread = new Thread(new ServerSenderWrapper(
				Config.getValue("metaserver"), Config.getValue("metaport"), Fs));
		hbThread.start();
		hbThread.join();
		
		//edited by Bharath for Server to server communication.
		Thread ServerRequestListener = new Thread(new ListenerWrapper(reqPort, ListenerWrapper.SER_REQ));
		ServerRequestListener.start();
		// while (true) {
		// // if (Fs.addFile("Test" + RandomNumber.randomInt(1, 3),
		// // RandomNumber.randomInt(1, 10), 512))
		// // ;
		// // if (Fs.addFile("Foo" + RandomNumber.randomInt(1, 3),
		// // RandomNumber.randomInt(1, 10), 512))
		// // ;
		// // log("FS is:" + Fs.toString());
		//
		// Thread.sleep(3000);
		// }
	}

	public static Message serverRequestHandler(Message msg) {
		String fname = serverName + "/" + msg.fileName + msg.chunkNo;

		Message reply = new Message(msg.type, Message.STATUS_FAIL,
				msg.fileName, msg.chunkNo, msg.offSet, msg.len, null);

		switch (msg.type) {
		case Message.APPEND:

			int fileSize = FileOperations.countCharsBuffer(fname, "US-ASCII");
			StringBuilder sBuf = new StringBuilder();
			String data = null;

			if (msg.data == null) {
				for (int i = 0; i < 8192 - fileSize; i++) {
					sBuf.append("\0");
				}
				data = sBuf.toString();
			} else {
				data = msg.data;
			}

			if (FileOperations.writeFile(fname, fileSize, data)) {
				Fs.addFile(msg.fileName, msg.chunkNo, fileSize + data.length());
				reply.status = Message.STATUS_SUCCESS;
				reply.data = "Successfully appended";
			} else {
				reply.data = "Failed to append file on disk";
			}
			break;
		case Message.WRITE:
			if (FileOperations.writeFile(fname, 0, msg.data)) {
				Fs.addFile(msg.fileName, msg.chunkNo, msg.data.length());
				reply.status = Message.STATUS_SUCCESS;
				reply.data = "Successfully written";
			} else {
				reply.data = "Failed to write file on disk";
			}
			break;

		case Message.CREATE:
			if (FileOperations.createFile(fname)) {
				Fs.addFile(msg.fileName, msg.chunkNo, 0);
				reply.status = Message.STATUS_SUCCESS;
				reply.data = "Successfully created";
			} else {
				reply.data = "Failed to create file on disk";
			}
			break;
		
		}
		return reply;
	}
	
	/**
	 * Create by Bharath : Method called for creating files and replicas.
	 * @param s1
	 * @param s2
	 * @param fName
	 * @return
	 */
	public boolean Create(String s1, String s2, Message msg)
	{
		String fname = serverName + "/" + msg.fileName + msg.chunkNo;	
		boolean isS1Active = IsServerActive(s1);
		boolean isS2Active = IsServerActive(s2);
		if(!isS1Active || !isS2Active)
		{
			return false;
		}
		else
		{
			if (FileOperations.writeFile(fname, 0, msg.data)) 
			{
				Fs.addFile(msg.fileName, msg.chunkNo, msg.data.length());
				return(Create(s1, msg) && Create(s2, msg));
			}
			else
			{
				return false;
			}
		}		
	}
	
	/**
	 * Created by Bharath : Method called for creating files is replica server.
	 * @param s2
	 * @param fName
	 * @return
	 */
	private boolean Create(String serverName, Message msg) {
		String portNo = Config.getValue(serverName);
		Message reply = Sender.messageToFileServer(serverName, portNo, msg);
		if(reply.status == Message.STATUS_SUCCESS)
			return true;
		else
			return false;
	}

	/**
	 * Create by Bharath : Method called for Appending files and replicas.
	 * @param s1
	 * @param s2
	 * @param msg
	 * @return
	 */
	public boolean Append(String s1, String s2, Message msg)
	{
		boolean isS1Active = IsServerActive(s1);
		boolean isS2Active = IsServerActive(s2);
		if(!isS1Active || !isS2Active)
		{
			return false;
		}
		else
		{
			String fname = serverName + "/" + msg.fileName + msg.chunkNo;
			int fileSize = FileOperations.countCharsBuffer(fname, "US-ASCII");
			StringBuilder sBuf = new StringBuilder();
			String data = null;

			if (msg.data == null) {
				for (int i = 0; i < 8192 - fileSize; i++) {
					sBuf.append("\0");
				}
				data = sBuf.toString();
			} else {
				data = msg.data;
			}

			if (FileOperations.writeFile(fname, fileSize, data)) {
				Fs.addFile(msg.fileName, msg.chunkNo, fileSize + data.length());
				return( Append(s1,msg) && Append(s2, msg));
			} else {
				return false;
			}
		}
	}
	
	/**
	 * Created by Bharath : Method called for Appending files is replica server.
	 * @param serverName
	 * @param msg
	 * @return
	 */
	private boolean Append(String serverName, Message msg) {
		String portNo = Config.getValue(serverName);
		Message reply = Sender.messageToFileServer(serverName, portNo, msg);
		if(reply.status == Message.STATUS_SUCCESS)
			return true;
		else
			return false;
	}
	
	/**
	 * Create by Bharath : Method called for Writing files and replicas.
	 * @param s1
	 * @param s2
	 * @param msg
	 * @return
	 */
	public boolean Write(String s1, String s2, Message msg)
	{
		String fname = serverName + "/" + msg.fileName + msg.chunkNo;	
		boolean isS1Active = IsServerActive(s1);
		boolean isS2Active = IsServerActive(s2);
		if(!isS1Active || !isS2Active)
		{
			return false;
		}
		else
		{
			if (FileOperations.writeFile(fname, 0, msg.data)) {
				Fs.addFile(msg.fileName, msg.chunkNo, msg.data.length());
				return(Write(s1, msg) && Write(s2, msg));
			}
			else
			{
				return false;
			}
		}
	}

	
	/**
	 * Created by Bharath : Method called for Writing files is replica server.
	 * @param serverName
	 * @param msg
	 * @return
	 */
	private boolean Write(String serverName, Message msg) {
		String portNo = Config.getValue(serverName);
		Message reply = Sender.messageToFileServer(serverName, portNo, msg);
		if(reply.status == Message.STATUS_SUCCESS)
			return true;
		else
			return false;
	}

	/**
	 * Created by Bharath for checking whether the server is active or not
	 * @param serverName
	 * @return
	 */
	private boolean IsServerActive(String serverName) {
		String portNo = Config.getValue(serverName);
		Message hellowMsg = new Message(Message.HELLO, Message.STATUS_FAIL, null, 0, 0, 0, null);
		
		Message reply = Sender.messageToFileServer(serverName, portNo, hellowMsg);
		
		if(reply.data.equals("Alive"))
		{
			return true;
		}
		else
			return false;
	}
}