/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.session.container.servlet.examples;

import java.io.Serial;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * A counting servlet using a mutable thread-safe attribute.
 * @author Paul Ferraro
 */
abstract class AbstractAtomicCountServlet extends AbstractCountServlet {
	@Serial
	private static final long serialVersionUID = -4991568328457400429L;
	/** Does container require pseudo-call-by-value semantics? */
	private final boolean callByValue;

	/**
	 * Default constructor
	 */
	AbstractAtomicCountServlet(boolean callByValue) {
		this.callByValue = callByValue;
	}

	@Override
	protected void doHead(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			response.addHeader(SESSION, session.getId());
			AtomicInteger current = (AtomicInteger) session.getAttribute(INDEX);
			if (current != null) {
				response.addIntHeader(INDEX, current.get());
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(true);
		response.addHeader(SESSION, session.getId());
		AtomicInteger count = session.isNew() ? new AtomicInteger(0) : (AtomicInteger) session.getAttribute(INDEX);
		if (count != null) {
			response.addIntHeader(INDEX, count.incrementAndGet());
			if (session.isNew() || this.callByValue) {
				session.setAttribute(INDEX, count);
			}
		}
	}
}
