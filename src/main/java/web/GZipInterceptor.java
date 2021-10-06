package web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

class GZipInterceptor implements ClientHttpRequestInterceptor {

	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		  HttpHeaders headers=request.getHeaders();
		  headers.set("Content-Encoding","gzip");
		  return execution.execute(request,compress(body));
	}

	
	/** 
	 * Internal helper method to compress given flow execution data using GZIP compression. Override if custom compression is desired.
	 */
	@SuppressWarnings("Since15")
	private byte[] compress(byte[] dataToCompress) throws IOException {
	  ByteArrayOutputStream baos=new ByteArrayOutputStream();
	  GZIPOutputStream gzipos=new GZIPOutputStream(baos);
	  try {
	    gzipos.write(dataToCompress);
	    gzipos.flush();
	  }
	  finally {
	    gzipos.close();
	  }
	  return baos.toByteArray();
	}

}
