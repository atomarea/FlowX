package net.atomarea.flowx.utils;

import net.atomarea.flowx.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SocksSocketFactory {

	public static void createSocksConnection(Socket socket, String destination, int port) throws IOException {
		InputStream proxyIs = socket.getInputStream();
		OutputStream proxyOs = socket.getOutputStream();
		proxyOs.write(new byte[]{0x05, 0x01, 0x00});
		byte[] response = new byte[2];
		proxyIs.read(response);
		byte[] dest = destination.getBytes();
		ByteBuffer request = ByteBuffer.allocate(7 + dest.length);
		request.put(new byte[]{0x05, 0x01, 0x00, 0x03});
		request.put((byte) dest.length);
		request.put(dest);
		request.putShort((short) port);
		proxyOs.write(request.array());
		response = new byte[7 + dest.length];
		proxyIs.read(response);
		if (response[1] != 0x00) {
			throw new SocksConnectionException();
		}
	}

	public static Socket createSocket(InetSocketAddress address, String destination, int port) throws IOException {
		Socket socket = new Socket();
		try {
			socket.connect(address, Config.CONNECT_TIMEOUT * 1000);
		} catch (IOException e) {
			throw new SocksProxyNotFoundException();
		}
		createSocksConnection(socket, destination, port);
		return socket;
	}

	public static Socket createSocketOverTor(String destination, int port) throws IOException {
		return createSocket(new InetSocketAddress(InetAddress.getLocalHost(), 9050), destination, port);
	}

	static class SocksConnectionException extends IOException {

	}

	public static class SocksProxyNotFoundException extends IOException {

	}
}
