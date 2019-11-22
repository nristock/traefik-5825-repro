/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package traefik.repro;

import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

public class App {
	private static final int TRAEFIK_PORT = 5000;
	private static final int GRPC_SERVER_PORT = 5001;
	private static final int CONCURRENCY = 80;

	public static void main(String[] args) throws IOException {
		final var serverPool = Executors.newFixedThreadPool(CONCURRENCY + 10);

		final var server = NettyServerBuilder.forPort(GRPC_SERVER_PORT)
				.addService(new TestServiceImpl())
				.executor(serverPool)
				.build();

		server.start();

		final SslContext sslContext = GrpcSslContexts.forClient()
				.trustManager(new File("./certs/ca.pem"))
				.keyManager(new File("./certs/member1.pem"), new File("./certs/member1-key.pkcs8"))
				.build();

		final var clientPool = Executors.newFixedThreadPool(CONCURRENCY + 10);

		final var channel = NettyChannelBuilder.forAddress("traefik", TRAEFIK_PORT)
				.sslContext(sslContext)
				.executor(clientPool)
				.offloadExecutor(clientPool)
				.build();

		final var stub = TestServiceGrpc.newBlockingStub(channel);

		for (int i = 0; i < CONCURRENCY; i++) {
			new Thread(() -> {
				System.out.println("Worker starter");
				try {
					while (true) {
						stub.testMethod(Test.getDefaultInstance());
					}
				} catch (Throwable e) {
					System.out.println("Worker stopped");
					System.out.println(e);
				}
			}).start();
		}
	}
}
