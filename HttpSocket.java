import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpSocket {
	Pattern methodPattern = Pattern.compile("(GET|POST|PUT|HEAD|DELETE|CONNECT|OPTIONS)(\\s+?)(.*?)(\\s+)");

	Socket socket;
	HashMap<String, String> requestHeaders;
	byte[] requestBody;

	public HttpSocket(Socket s) {
		this.socket = s;
	}

	void close() {
		if (this.socket != null)
			try {
				this.socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	public HashMap<String, String> parseHeader(String input) {
		HashMap<String, String> ret = new HashMap<String, String>();
		try (Scanner scan = new Scanner(input)) {
			while (scan.hasNextLine()) {
				try {
					String line = scan.nextLine();
					Matcher m = this.methodPattern.matcher(line);
					if (m.find()) {
						ret.put("method", m.group(1));
						ret.put("url", m.group(3));
					} else {
						int index = line.indexOf(':');
						if (index > -1) {
							ret.put(line.substring(0, index).trim().toLowerCase(), line.substring(index + 1).trim());
						}
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.requestHeaders = ret;
		return this.requestHeaders;
	}

	void parseInputStream() {
		try {
			InputStream in = socket.getInputStream();
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			this.requestBody = new byte[] {};
			byte[] ends = new byte[] { '\r', '\n', '\r', '\n' };
			byte[] buffer = new byte[(ends.length + 1)];
			boolean end;
			do {
				int input = in.read();
				if (input == -1) {
					break;
				}
				bo.write(input);
				buffer[buffer.length - 1] = (byte) input;
				end = true;
				for (int i = 0; i < buffer.length - 1; i++) {
					buffer[i] = buffer[i + 1];
					if (buffer[i] != ends[i]) {
						end = false;
					}
				}
			} while (!end);

			parseHeader(new String(bo.toByteArray()));

			System.out.println(this.requestHeaders);

			int length = 0;
			String contentLength = this.requestHeaders.get("content-length");
			if (contentLength != null) {
				try {
					length = Integer.parseInt(contentLength);
				} catch (Exception e) {
				}
			}
			if (length > 0) {
				this.requestBody = new byte[length];
				in.read(this.requestBody);
			}

		} catch (Throwable th) {
			th.printStackTrace();
		}
	}

	public byte[] response(String input) {
		byte[] r = new byte[0];
		try {
			StringBuilder ret = new StringBuilder(
					"HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Length: "
							+ input.getBytes("UTF-8").length + "\r\n\r\n");
			ret.append(input);
			r = ret.toString().getBytes("UTF-8");
			socket.getOutputStream().write(r);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return r;
	}

	public static void main(String[] args) throws IOException {
		Logger log = Logger.getLogger("HttpSocket");

		int port = 8080;
		ServerSocket ss = new ServerSocket(port);
		log.info("server started at port: " + port);
		ExecutorService service = Executors.newFixedThreadPool(10);
		int count = 0;
		while (true) {
			try {

				Socket s = ss.accept();
				HttpSocket hs = new HttpSocket(s);

				log.info("#" + (++count) + ": " + s.getInetAddress());
				service.execute(new Runnable() {
					@Override
					public void run() {
						hs.parseInputStream();
						String url = hs.requestHeaders.get("url");
						if (url.startsWith("/echo")) {
							hs.response(hs.requestHeaders + "\n\n" + new String(hs.requestBody) + "\n");
						} else if (url.startsWith("/time")) {
							hs.response(String.valueOf(System.currentTimeMillis()));
						} else {
							hs.response("Hello, World");
						}
						hs.close();

					}
				});

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

}
