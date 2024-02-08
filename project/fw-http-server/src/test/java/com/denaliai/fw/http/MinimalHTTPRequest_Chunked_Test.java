package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.utility.http.MinimalHTTPRequest;
import com.denaliai.fw.utility.http.MinimalHTTPResponse;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MinimalHTTPRequest_Chunked_Test extends TestBase {
	@Test
	public void test() {
		final CharSequence seq = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Vitae elementum curabitur vitae nunc sed velit dignissim. Lectus urna duis convallis convallis tellus id. Arcu non sodales neque sodales ut etiam sit. Aliquam vestibulum morbi blandit cursus. Posuere lorem ipsum dolor sit amet. Malesuada fames ac turpis egestas maecenas pharetra convallis. Aliquet bibendum enim facilisis gravida neque. Interdum velit euismod in pellentesque massa. Aenean et tortor at risus viverra adipiscing. Mauris sit amet massa vitae tortor condimentum. Malesuada fames ac turpis egestas. Eget duis at tellus at.\n" +
						"\n" +
						"Sapien nec sagittis aliquam malesuada. Quis blandit turpis cursus in. Justo laoreet sit amet cursus sit amet. Vitae aliquet nec ullamcorper sit amet risus nullam eget. Suspendisse potenti nullam ac tortor vitae purus. Non arcu risus quis varius quam. Odio tempor orci dapibus ultrices. Non consectetur a erat nam at lectus urna. Dictum at tempor commodo ullamcorper a lacus vestibulum sed arcu. Eu non diam phasellus vestibulum lorem sed. Accumsan tortor posuere ac ut consequat semper viverra. At tellus at urna condimentum mattis pellentesque id. Risus commodo viverra maecenas accumsan lacus vel facilisis volutpat est. In nibh mauris cursus mattis. Nulla facilisi cras fermentum odio eu feugiat pretium nibh. Egestas purus viverra accumsan in nisl nisi scelerisque eu.\n" +
						"\n" +
						"Ullamcorper malesuada proin libero nunc consequat interdum varius sit amet. Nisi vitae suscipit tellus mauris. Amet dictum sit amet justo. Justo donec enim diam vulputate. Pretium fusce id velit ut tortor pretium viverra suspendisse potenti. Nisl tincidunt eget nullam non nisi est. Enim nec dui nunc mattis enim ut tellus elementum. Lectus arcu bibendum at varius vel. Condimentum id venenatis a condimentum vitae sapien pellentesque habitant. Non sodales neque sodales ut etiam sit amet. Nulla facilisi etiam dignissim diam quis enim lobortis scelerisque. Varius vel pharetra vel turpis nunc. Ultrices dui sapien eget mi proin sed. Vitae aliquet nec ullamcorper sit amet risus nullam eget. Quis blandit turpis cursus in hac habitasse platea dictumst quisque. Bibendum ut tristique et egestas quis ipsum suspendisse. Sit amet porttitor eget dolor morbi non arcu risus quis. Faucibus a pellentesque sit amet porttitor eget. Lacinia at quis risus sed vulputate.\n" +
						"\n" +
						"Sed nisi lacus sed viverra. Vel fringilla est ullamcorper eget nulla facilisi etiam dignissim diam. A cras semper auctor neque. Pretium aenean pharetra magna ac placerat. Ultrices in iaculis nunc sed augue lacus. Habitasse platea dictumst quisque sagittis purus sit amet volutpat consequat. Dui vivamus arcu felis bibendum ut tristique et egestas. Urna porttitor rhoncus dolor purus non enim. Donec ultrices tincidunt arcu non sodales neque sodales ut etiam. Amet tellus cras adipiscing enim eu turpis egestas pretium aenean. Congue nisi vitae suscipit tellus mauris a diam. Diam in arcu cursus euismod quis viverra nibh. Ultricies mi quis hendrerit dolor magna eget est. Eu tincidunt tortor aliquam nulla facilisi cras fermentum odio eu. Vulputate odio ut enim blandit volutpat maecenas. Scelerisque varius morbi enim nunc faucibus.\n" +
						"\n" +
						"Quis varius quam quisque id diam. Bibendum enim facilisis gravida neque convallis a cras semper auctor. Sed risus pretium quam vulputate dignissim suspendisse in est ante. Sodales ut eu sem integer. Maecenas accumsan lacus vel facilisis volutpat est. Ipsum consequat nisl vel pretium lectus. Viverra maecenas accumsan lacus vel facilisis. Tortor consequat id porta nibh venenatis cras. Fames ac turpis egestas sed tempus. Laoreet suspendisse interdum consectetur libero id faucibus nisl tincidunt. Quis risus sed vulputate odio ut enim blandit volutpat maecenas.";
		HttpServer httpServer = HttpServer.builder()
			.listenPort(10000)
			.onRequest((HttpServer.IHttpRequest request, HttpServer.IHttpResponse response)->{
				if (!request.requestMethod().equals("GET")) {
					response.respondOk(ByteBufUtil.writeAscii(Application.ioBufferAllocator(), "Request method is " + request.requestMethod() + " instead of GET"));
					return;
				}
				if (!request.requestURI().equals("/chunked_response.html")) {
					response.respondOk(ByteBufUtil.writeAscii(Application.ioBufferAllocator(), "Request URI is '" + request.requestURI() + "' instead of '/chunked_response.html'"));
					return;
				}
				response.addHeader("Transfer-Encoding","chunked");
				response.respondOk(ByteBufUtil.writeAscii(Application.ioBufferAllocator(), seq));
			})
			.build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		MinimalHTTPResponse r = MinimalHTTPRequest.get("localhost", 10000, "/chunked_response.html");
		Assertions.assertEquals(200, r.code);
		Assertions.assertEquals(seq, r.body);
		Assertions.assertEquals(Integer.toString(seq.length()), r.getHeader(HttpHeaderNames.CONTENT_LENGTH.toString()));
		Assertions.assertEquals("chunked", r.getHeader(HttpHeaderNames.TRANSFER_ENCODING.toString()));
		Assertions.assertEquals("close", r.getHeader(HttpHeaderNames.CONNECTION.toString()));
		Assertions.assertNotNull(r.getHeader(HttpServer.X_REQUEST_ID.toString()));

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}
}
