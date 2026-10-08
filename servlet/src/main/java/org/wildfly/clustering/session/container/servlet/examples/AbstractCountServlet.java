/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.session.container.servlet.examples;

import java.io.Serial;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * A counting servlet.
 * @author Paul Ferraro
 */
abstract class AbstractCountServlet extends HttpServlet {
	static final String SERVLET_NAME = "count";
	static final String SERVLET_PATH = "/" + SERVLET_NAME;
	/** The name of the header/attribute storing the request index */
	static final String INDEX = "index";
	static final String SESSION = "session";

	@Serial
	private static final long serialVersionUID = -5189993114817119720L;

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}
}
