/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.session.container.servlet.examples;

import java.io.Serial;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpSession;

/**
 * A counting servlet using a mutex attribute for thread synchronization.
 * @author Paul Ferraro
 */
@WebServlet(urlPatterns = AbstractCountServlet.SERVLET_PATH)
public class SessionIdMutexCountServlet extends AbstractMutexCountServlet {
	@Serial
	private static final long serialVersionUID = -4991568328457400429L;

	/**
	 * Default constructor
	 */
	public SessionIdMutexCountServlet() {
		super(HttpSession::getId, (session, mutex) -> {});
	}
}
