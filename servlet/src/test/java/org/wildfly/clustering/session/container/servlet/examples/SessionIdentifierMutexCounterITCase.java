/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.session.container.servlet.examples;

/**
 * @author Paul Ferraro
 */
public class SessionIdentifierMutexCounterITCase extends AbstractCounterITCase {

	public SessionIdentifierMutexCounterITCase() {
		super(SessionIdMutexCountServlet.class);
	}
}
