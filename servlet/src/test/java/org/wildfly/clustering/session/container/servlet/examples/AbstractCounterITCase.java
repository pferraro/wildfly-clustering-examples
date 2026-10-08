/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.session.container.servlet.examples;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jakarta.servlet.http.HttpServlet;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wildfly.clustering.arquillian.AbstractITCase;
import org.wildfly.clustering.container.ContainerLifecycle;

/**
 * @author Paul Ferraro
 */
public abstract class AbstractCounterITCase extends AbstractITCase<Class<? extends HttpServlet>, WebArchive> {

	static {
		// OpenLiberty's remote arquillian container is hard-coded to use HTTPS
		// To avoid certificate importation, we will workaround by trusting everything
		if (Boolean.getBoolean("test.trust-all-certs")) {
			try {
				SSLContext context = SSLContext.getInstance("TLS");
				context.init(null, new TrustManager[] { new X509TrustManager() {
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType) {
					}

					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType) {
					}
				}}, null);
				SSLContext.setDefault(context);
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@RegisterExtension
	static final ArquillianExtension ARQUILLIAN = new ArquillianExtension();

	private static final IntUnaryOperator ROUTER = Router.valueOf(System.getProperty("test.router", Router.STICKY.name()));
	private static final Duration INTERVAL = Optional.ofNullable(System.getProperty("test.interval")).map(Duration::parse).orElse(Duration.ZERO);
	private static final Consumer<CompletableFuture<Void>> HANDLER = Handler.valueOf(System.getProperty("test.invoker", Handler.SEQUENTIAL.name()));
	private static final int CLIENTS = Integer.getInteger("test.clients", 1);
	private static final int REQUESTS = Integer.getInteger("test.requests", 1000);
	private static final int THREADS = Integer.getInteger("test.threads", 10);

	enum Router implements IntUnaryOperator {
		STICKY() {
			@Override
			public int applyAsInt(int index) {
				return index;
			}
		},
		ROUND_ROBIN() {
			@Override
			public int applyAsInt(int index) {
				return index + 1;
			}
		},
		RANDOM() {
			private final Random random = new Random();

			@Override
			public int applyAsInt(int index) {
				return this.random.nextInt(Integer.MAX_VALUE);
			}
		},
	}

	enum Handler implements Consumer<CompletableFuture<Void>> {
		SEQUENTIAL() {
			@Override
			public void accept(CompletableFuture<Void> response) {
				response.join();
				sleep(INTERVAL);
			}
		},
		CONCURRENT() {
			@Override
			public void accept(CompletableFuture<Void> response) {
				sleep(INTERVAL);
			}
		}
		;

		void sleep(Duration duration) {
			if (!duration.isNegative() && !duration.isZero()) {
				try {
					TimeUnit.NANOSECONDS.sleep(duration.toNanos());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}


	private final Class<? extends HttpServlet> servletClass;

	AbstractCounterITCase(Class<? extends HttpServlet> servletClass) {
		super(() -> new CounterTester(servletClass, AbstractCountServlet.SERVLET_NAME, ROUTER, HANDLER, CLIENTS, REQUESTS, (HANDLER == Handler.CONCURRENT) ? THREADS : 1));
		this.servletClass = servletClass;
	}

	@Override
	public WebArchive createArchive(Class<? extends HttpServlet> servletClass) {
		WebArchive archive = ShrinkWrap.create(WebArchive.class, servletClass.getSimpleName() + ".war");
		// Add servlet class (and its superclasses)
		Class<?> applicationClass = servletClass;
		while (applicationClass != HttpServlet.class) {
			archive.addClass(applicationClass);
			applicationClass = applicationClass.getSuperclass();
		}
		// Add a container-specific deployment descriptor, if necessary
		String containerResourceName = String.format("%s-web.xml", System.getProperty("test.container"));
		URL containerResource = servletClass.getClassLoader().getResource(containerResourceName);
		if (containerResource != null) {
			archive.addAsWebInfResource(containerResource, containerResourceName);
		}
		// Add any auxiliary resources
		File webResources = Path.of(System.getProperty("test.resources")).toFile();
		if (webResources.exists()) {
			for (File webResource : webResources.listFiles()) {
				if (webResource.isFile()) {
					archive.addAsWebInfResource(webResource);
				}
			}
		}
		// Add any auxiliary libraries
		File libDirectory = Path.of(System.getProperty("test.lib")).toFile();
		if (libDirectory.exists()) {
			for (File lib : libDirectory.listFiles((dir, name) -> name.endsWith(".jar"))) {
				archive.addAsLibrary(lib);
			}
		}
		// Output contents of WAR, for verification
		System.out.println(archive.toString(true));
		return archive;
	}

	@RunAsClient
	@Test
	void test() {
		try (ContainerLifecycle container = ContainerLifecycle.from(System.getProperties())) {
			container.start();
			this.accept(this.servletClass);
			container.stop();
		}
	}
}
