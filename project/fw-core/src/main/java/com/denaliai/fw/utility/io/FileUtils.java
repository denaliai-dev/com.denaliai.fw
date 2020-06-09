package com.denaliai.fw.utility.io;

import com.denaliai.fw.Application;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileUtils {

	public static final String readFile(String fileName) throws IOException {
		return readFile(fileName, StandardCharsets.US_ASCII);
	}

	public static final String readFile(String fileName, Charset cs) throws IOException {
		final StringBuilder b = new StringBuilder();
		final Reader r = new InputStreamReader(new FileInputStream(fileName), cs);
		try {
			final char[] buffer = new char[256 * 1024];
			while (true) {
				int numChars = r.read(buffer);
				if (numChars == -1) {
					break;
				}
				b.append(buffer, 0, numChars);
			}
		} finally {
			r.close();
		}
		return b.toString();
	}

	public static final ByteBuf readFileBB(File file, ByteBufAllocator allocator) throws IOException {
		final ByteBuf data = allocator.buffer();
		FileChannel channel = FileChannel.open(file.toPath(),StandardOpenOption.READ);
		try {
			data.writeBytes(channel, 0l, (int)channel.size());
		} finally {
			channel.close();
		}
		return data;
	}

	public static final void appendFile(String fileName, String fileContents) throws IOException {
		final FileWriter out = new FileWriter(fileName, true);
		try {
			out.write(fileContents);
		} finally {
			out.close();
		}
	}

	public static final void writeFile(String fileName, String fileContents) throws IOException {
		final FileWriter out = new FileWriter(fileName);
		try {
			out.write(fileContents);
		} finally {
			out.close();
		}
	}

	public static final void writeFile(String fileName, String fileContents, Charset cs) throws IOException {
		final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(fileName), cs);
		try {
			out.write(fileContents);
		} finally {
			out.close();
		}
	}

	public static final void writeFile(File file, String fileContents, Charset cs) throws IOException {
		final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), cs);
		try {
			out.write(fileContents);
		} finally {
			out.close();
		}
	}

	public static final String readAll(InputStream in) throws IOException {
		final StringBuilder b = new StringBuilder();
		final Reader r = new InputStreamReader(in, StandardCharsets.US_ASCII);
		final char[] buffer = new char[256 * 1024];
		while (true) {
			int numChars = r.read(buffer);
			if (numChars == -1) {
				break;
			}
			b.append(buffer, 0, numChars);
		}
		return b.toString();

	}

	public static final void deleteDir(File dir) {
		for(File f : dir.listFiles()) {
//			if (f.getName().equals(".") || f.getName().equals("..")) {
//				System.out.println("Found " + f.getName());
//				continue;
//			}
			if (f.isDirectory()) {
				deleteDir(f);
			}
			if (!f.delete()) {
				throw new RuntimeException("Could not delete " + f.getAbsolutePath());
			}
		}
		if (!dir.delete()) {
			throw new RuntimeException("Could not delete " + dir.getAbsolutePath());
		}
	}
}
