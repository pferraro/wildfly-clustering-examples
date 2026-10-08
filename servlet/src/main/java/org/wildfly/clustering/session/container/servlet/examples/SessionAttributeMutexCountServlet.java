/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.session.container.servlet.examples;

import java.io.Serial;

import jakarta.servlet.annotation.WebServlet;

/**
 * A counting servlet using a mutex attribute for thread synchronization.
 * @author Paul Ferraro
 */
@WebServlet(urlPatterns = AbstractCountServlet.SERVLET_PATH)
public class SessionAttributeMutexCountServlet extends AbstractMutexCountServlet {
	@Serial
	private static final long serialVersionUID = -4991568328457400429L;

	private static final String MUTEX = "mutex";

	/**
	 * Default constructor
	 */
	public SessionAttributeMutexCountServlet() {
		super(session -> session.getAttribute(MUTEX), (session, mutex) -> session.setAttribute(MUTEX, mutex));
	}
}
