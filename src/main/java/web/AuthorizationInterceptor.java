package web;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

class AuthorizationInterceptor implements ClientHttpRequestInterceptor {

	private final String auth;
	
	public AuthorizationInterceptor(String auth) {
		this.auth = auth.replace(
				"Authorization:", "").trim();
	}
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		  HttpHeaders headers=request.getHeaders();
		  headers.set("Authorization",auth);
		  return execution.execute(request,body);
	}

}
