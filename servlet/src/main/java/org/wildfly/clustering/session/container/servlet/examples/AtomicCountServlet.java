/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.session.container.servlet.examples;

import jakarta.servlet.annotation.WebServlet;

/**
 * A counting servlet using a mutable thread-safe attribute.
 * @author Paul Ferraro
 */
@WebServlet(urlPatterns = AbstractCountServlet.SERVLET_PATH)
public class AtomicCountServlet extends AbstractAtomicCountServlet {
	private static final long serialVersionUID = 3539632020147554197L;

	/**
	 * Default constructor.
	 */
	public AtomicCountServlet() {
		super(false);
	}
}
