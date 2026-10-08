/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.session.container.servlet.examples;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import jakarta.servlet.http.HttpServlet;

import org.assertj.core.api.Assertions;
import org.wildfly.clustering.arquillian.Deployment;
import org.wildfly.clustering.arquillian.Tester;

/**
 * A test of a container consistency of a counter tracked by a distributed HttpSession attribute.
 * @author Paul Ferraro
 */
public class CounterTester implements Tester {
	private static final Consumer<HttpResponse<Void>> VALIDATOR = response -> Assertions.assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	private static final Function<HttpResponse<Void>, Optional<String>> SESSION = response -> response.headers().firstValue(AbstractCountServlet.SESSION);
	private static final Function<HttpResponse<Void>, OptionalLong> RESULT = response -> response.headers().firstValueAsLong(AbstractCountServlet.INDEX);
	private static final Consumer<HttpResponse<Void>> REPORTER = response -> System.out.println("Cookies: " + response.headers().allValues("Set-Cookie"));

	private final Class<? extends HttpServlet> servletClass;
	private final String servletPath;
	private final IntUnaryOperator router;
	private final Consumer<CompletableFuture<Void>> handler;
	private final int requests;
	private final Deque<Runnable> closeTasks = new LinkedList<>();
	private final List<HttpClient> clients;
	private final Executor executor;

	public CounterTester(Class<? extends HttpServlet> servletClass, String servletPath, IntUnaryOperator router, Consumer<CompletableFuture<Void>> handler, int clients, int requests, int threads) {
		System.out.println(String.format("Running distributed Counter test using clients = %d, router = %s, invoker = %s, requests-per-client = %d, threads-per-client = %d", clients, router, handler, requests, threads));
		this.servletClass = servletClass;
		this.servletPath = servletPath;
		this.router = router;
		this.handler = handler;
		this.requests = requests;
		ExecutorService executor = (clients > 1) ? Executors.newFixedThreadPool(clients) : null;
		if (executor != null) {
			this.closeTasks.add(executor::shutdown);
		}
		this.executor = Optional.<Executor>ofNullable(executor).orElse(Runnable::run);
		this.clients = new ArrayList<>(clients);
		for (int i = 0; i < clients; ++i) {
			ExecutorService clientExecutor = Executors.newFixedThreadPool(threads);
			this.closeTasks.add(clientExecutor::shutdown);
			this.clients.add(HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).executor(clientExecutor).build());
		}
	}

	@Override
	public void accept(List<Deployment> deployments) {

		List<URI> uris = new ArrayList<>(deployments.size());
		for (Deployment deployment : deployments) {
			URI baseURI = Objects.requireNonNull(deployment.locate(this.servletClass));
			uris.add(baseURI.resolve(this.servletPath));
		}

		List<Map.Entry<Map<String, Queue<Long>>, CompletableFuture<Duration>>> futureResults = new ArrayList<>(this.clients.size());
		for (int i = 0; i < this.clients.size(); ++i) {
			HttpClient client = this.clients.get(i);
			// Simulate load balancing of session establishing request
			int startTargetIndex = i % uris.size();
			Iterator<URI> router = IntStream.iterate(startTargetIndex, this.router).mapToObj(index -> uris.get(index % uris.size())).iterator();
			Map<String, Queue<Long>> responses = new ConcurrentHashMap<>();
			Function<String, Queue<Long>> queueFactory = session -> new ArrayBlockingQueue<>(this.requests);
			Consumer<HttpResponse<Void>> recorder = response -> SESSION.apply(response).ifPresent(session -> {
				Queue<Long> queue = responses.computeIfAbsent(session, queueFactory);
				RESULT.apply(response).ifPresent(queue::add);
			});
			Consumer<HttpResponse<Void>> processor = VALIDATOR.andThen(recorder);
			CompletableFuture<Duration> future = new CompletableFuture<>();
			futureResults.add(Map.entry(responses, future));
			URI uri = router.next();
			System.out.println(String.format("Client %d establishing session on %s via %s", i + 1, deployments.get(startTargetIndex).getContainer().getName(), uri));
			this.executor.execute(() -> {
				Instant start = Instant.now();
				try {
					processor.andThen(REPORTER).accept(client.send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.discarding()));
					AtomicInteger completed = new AtomicInteger(1);
					for (int request = 1; request < this.requests; ++request) {
						this.handler.accept(client.sendAsync(HttpRequest.newBuilder(router.next()).GET().build(), BodyHandlers.discarding())
								.orTimeout(30, TimeUnit.SECONDS)
								.thenAccept(processor)
								.whenComplete((ignore, exception) -> {
									if (exception != null) {
										future.completeExceptionally(exception);
									} else if (completed.incrementAndGet() == this.requests) {
										future.complete(Duration.between(start, Instant.now()));
									}
								}));
					}
				} catch (IOException e) {
					future.completeExceptionally(e);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					future.cancel(true);
				}
			});
		}

		long expectedDistinct = this.requests;
		List<Integer> lostSessions = new ArrayList<>(this.clients.size());
		List<Integer> lostAttributes = new ArrayList<>(this.clients.size());
		List<Long> dirtyReads = new ArrayList<>(this.clients.size());
		List<Duration> durations = new ArrayList<>(this.clients.size());
		for (Map.Entry<Map<String, Queue<Long>>, CompletableFuture<Duration>> futureResult : futureResults) {
			durations.add(futureResult.getValue().join());
			Map<String, Queue<Long>> result = futureResult.getKey();
			lostSessions.add(result.size() - 1);
			lostAttributes.add(this.requests - result.values().stream().mapToInt(Collection::size).sum());
			dirtyReads.add(expectedDistinct - result.values().stream().mapToLong(queue -> queue.stream().distinct().count()).sum());
		}
		Collections.sort(dirtyReads);
		Collections.sort(durations);

		List<List<CompletableFuture<Long>>> futureCounts = new ArrayList<>(uris.size());
		for (URI uri : uris) {
			List<CompletableFuture<Long>> clientFutureCounts = new ArrayList<>(this.clients.size());
			futureCounts.add(clientFutureCounts);
			for (HttpClient client : this.clients) {
				CompletableFuture<Long> clientFutureCount = new CompletableFuture<>();
				Consumer<HttpResponse<Void>> recorder = response -> clientFutureCount.complete(RESULT.apply(response).orElse(0L));
				Consumer<HttpResponse<Void>> processor = VALIDATOR.andThen(recorder);
				clientFutureCounts.add(clientFutureCount);
				client.sendAsync(HttpRequest.newBuilder(uri).method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding())
						.orTimeout(30, TimeUnit.SECONDS)
						.thenAccept(processor)
						.whenComplete((ignore, exception) -> {
							if (exception != null) {
								clientFutureCount.completeExceptionally(exception);
							}
						});
			}
		}

		long expectedCount = this.requests;
		List<List<Long>> lostWrites = new ArrayList<>(uris.size());
		for (List<CompletableFuture<Long>> clientFutureCounts : futureCounts) {
			List<Long> clientLostWrites = new ArrayList<>(clientFutureCounts.size());
			lostWrites.add(clientLostWrites);
			for (CompletableFuture<Long> futureCount : clientFutureCounts) {
				clientLostWrites.add(expectedCount - futureCount.join());
			}
			Collections.sort(clientLostWrites);
		}

		System.out.println("****************************************************************");
		System.out.println("Test duration:");
		if (this.clients.size() > 1) {
			System.out.println("\t   Mean: " + (durations.stream().reduce(Duration::plus).orElse(Duration.ZERO).dividedBy(this.clients.size())));
			System.out.println("\t Median: " + durations.get(this.clients.size() / 2));
			System.out.println("\tMinimum: " + durations.get(0));
			System.out.println("\tMaximum: " + durations.get(this.clients.size() - 1));
		} else {
			System.out.println("\t" + durations.get(0));
		}
		System.out.println("Lost sessions:");
		if (this.clients.size() > 1) {
			System.out.println("\t   Mean: " + (lostSessions.stream().mapToInt(Integer::intValue).sum() / this.clients.size()));
			System.out.println("\t Median: " + lostSessions.get(this.clients.size() / 2));
			System.out.println("\tMinimum: " + lostSessions.get(0));
			System.out.println("\tMaximum: " + lostSessions.get(this.clients.size() - 1));
		} else {
			System.out.println("\t" + lostSessions.get(0));
		}
		System.out.println("Lost attributes:");
		if (this.clients.size() > 1) {
			System.out.println("\t   Mean: " + (lostAttributes.stream().mapToInt(Integer::intValue).sum() / this.clients.size()));
			System.out.println("\t Median: " + lostAttributes.get(this.clients.size() / 2));
			System.out.println("\tMinimum: " + lostAttributes.get(0));
			System.out.println("\tMaximum: " + lostAttributes.get(this.clients.size() - 1));
		} else {
			System.out.println("\t" + lostAttributes.get(0));
		}
		System.out.println(String.format("Dirty reads (out of %d):", this.requests));
		if (this.clients.size() > 1) {
			System.out.println("\t   Mean: " + (dirtyReads.stream().mapToLong(Long::longValue).sum() / this.clients.size()));
			System.out.println("\t Median: " + dirtyReads.get(this.clients.size() / 2));
			System.out.println("\tMinimum: " + dirtyReads.get(0));
			System.out.println("\tMaximum: " + dirtyReads.get(this.clients.size() - 1));
		} else {
			System.out.println("\t" + dirtyReads.get(0));
		}
		for (int i = 0; i < lostWrites.size(); ++i) {
			List<Long> containerLostWrites = lostWrites.get(i);
			System.out.println(String.format("Lost writes (out of %d): %s", this.requests, deployments.get(i).getContainer().getName()));
			if (this.clients.size() > 1) {
				System.out.println("\t   Mean: " + (containerLostWrites.stream().mapToLong(Long::longValue).sum() / this.clients.size()));
				System.out.println("\t Median: " + containerLostWrites.get(this.clients.size() / 2));
				System.out.println("\tMinimum: " + containerLostWrites.get(0));
				System.out.println("\tMaximum: " + containerLostWrites.get(this.clients.size() - 1));
			} else {
				System.out.println("\t" + containerLostWrites.get(0));
			}
		}
		System.out.println("****************************************************************");
	}

	@Override
	public void close() {
		this.closeTasks.descendingIterator().forEachRemaining(Runnable::run);
	}
}
