package com.denaliai.fw.utility.socket;

import com.denaliai.fw.Application;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.io.*;
import java.net.Socket;

public class MinimalRawSocketExchange {

	public static String submit(String host, int port, String outData) throws IOException {
		Socket socket = new Socket(host, port);
		socket.setSoTimeout(15000);
		OutputStream out = socket.getOutputStream();
		out.write(outData.getBytes());
		InputStream in = socket.getInputStream();
		ByteBuf inData = Application.allocateIOBuffer();
		byte[] scratch = new byte[1024];
		while(true) {
			int numRead = in.read(scratch);
			if (numRead == -1) {
				break;
			}
			inData.writeBytes(scratch, 0, numRead);
		}
		in.close();
		out.close();
		String response = new String(ByteBufUtil.getBytes(inData));
		inData.release();
		return response;

	}
}
