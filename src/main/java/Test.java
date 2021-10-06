
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.MessageQueue;
import com.ibm.as400.access.QSYSObjectPathName;

import web.Flow;

public class Test {

	public static void main2(String[] args) {
		AS400 as400 = new AS400();
		String messageData = "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";

		MessageQueue queue = new MessageQueue(as400, "/qsys.lib/qsysopr.msgq");
		try {
			queue.sendInformational("ERR7056", new QSYSObjectPathName("*LIBL", "NMSGF", "MSGF").getPath(), messageData.getBytes("Cp037"));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {

		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext("web");
		// ApplicationContext context = new
		// AnnotationConfigApplicationContext(AppConfig.class);
		// ConfigurableApplicationContext context = new
		// AnnotationConfigApplicationContext(AppConfig.class);

		Flow flow = context.getBean(Flow.class);
		context.close();
		flow.setTrustManager();
		int[] ids = { 1, 2, 3, 4 ,5 };

		String proxyUrl, proxyPort;
		// System.out.println("about to run");
		if (args[1].equals("proxy")) {
			proxyUrl = "b2b-http.dhl.com           ";
			proxyPort = "8080          ";
		} else {
			proxyUrl = "";
			proxyPort = "";
		}

		for (int i = 1; i < 2; i++) {
			flow.post(ids, 5, args[0].getBytes(),
					"{\"batchId\":\"EI160113-1-1\",\"timestamp\":\"2016-01-13T17:56:22.772Z\",\"records\":[{\"record\":\"HJ\",\"process\":\"C\",\"version\":9,\"data\":{\"HJNZND\":19458211,\"HJEVRZ\":20,\"HJJVRZ\":16,\"HJMVRZ\":1,\"HJKREF\":\"АБВГДЕЖЗAAO\",\"HJOREF\":\"839999963805143\"}}]}"
							.getBytes(),
					"Authorization: 88aad782327d478fa8710c5d84ea342a  ".getBytes(), "EI160210".getBytes(),
					proxyUrl.getBytes(), proxyPort.getBytes(), "2016-03-18-18.00.26.331000".getBytes(),
					"PDS".getBytes(), 110);

		}

		// restTemplate.postForObject("https://pds-accept.dhlparcel.nl/flow",
		// message, String.class);
		// System.out.println("done");
		// Thread.sleep(1000 * 20);
		context.close();
	}

}
