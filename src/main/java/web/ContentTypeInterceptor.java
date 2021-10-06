package web;

import java.io.IOException;
import java.nio.charset.Charset;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

class ContentTypeInterceptor implements ClientHttpRequestInterceptor {

	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		  HttpHeaders headers=request.getHeaders();
		  headers.setContentType(new MediaType("application","json", Charset.forName("UTF-8")));
//		  headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
		  return execution.execute(request,body);
	}


}
