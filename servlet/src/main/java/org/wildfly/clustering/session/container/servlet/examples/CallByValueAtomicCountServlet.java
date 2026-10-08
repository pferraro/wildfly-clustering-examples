/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.session.container.servlet.examples;

import jakarta.servlet.annotation.WebServlet;

/**
 * A counting servlet using a mutable thread-safe attribute using pseudo-call-by-value semantics.
 * @author Paul Ferraro
 */
@WebServlet(urlPatterns = AbstractCountServlet.SERVLET_PATH)
public class CallByValueAtomicCountServlet extends AbstractAtomicCountServlet {
	private static final long serialVersionUID = -2493127209208752635L;

	/**
	 * Default constructor.
	 */
	public CallByValueAtomicCountServlet() {
		super(true);
	}
}
