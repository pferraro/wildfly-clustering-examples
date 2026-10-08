/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.session.container.servlet.examples;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * A servlet that uses a mutex to make thread-safe updates to counter persisted as an immutable session attribute.
 * @author Paul Ferraro
 */
public abstract class AbstractMutexCountServlet extends AbstractCountServlet {
	@Serial
	private static final long serialVersionUID = -646309598739513459L;

	/** Reader of the mutex */
	private final Function<HttpSession, Object> mutexReader;
	/** Writer of the mutex */
	private final BiConsumer<HttpSession, Object> mutexWriter;

	AbstractMutexCountServlet(Function<HttpSession, Object> mutexReader, BiConsumer<HttpSession, Object> mutexWriter) {
		this.mutexReader = mutexReader;
		this.mutexWriter = mutexWriter;
	}

	@Override
	protected void doHead(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			response.addHeader(SESSION, session.getId());
			Object mutex = this.mutexReader.apply(session);
			if (mutex != null) {
				synchronized (mutex) {
					Integer current = (Integer) session.getAttribute(INDEX);
					if (current != null) {
						response.addIntHeader(INDEX, current);
					}
				}
			}
		}
	}

	@Override
	protected void doGet​(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(true);
		response.addHeader(SESSION, session.getId());
		if (session.isNew()) {
			this.mutexWriter.accept(session, UUID.randomUUID());
		}
		Object mutex = this.mutexReader.apply(session);
		if (mutex != null) {
			synchronized (mutex) {
				Integer previous = Optional.ofNullable(session.getAttribute(INDEX)).map(Integer.class::cast).orElse(Integer.valueOf(0));
				Integer current = previous + 1;
				response.addIntHeader(INDEX, current);
				session.setAttribute(INDEX, current);
			}
		}
	}
}
