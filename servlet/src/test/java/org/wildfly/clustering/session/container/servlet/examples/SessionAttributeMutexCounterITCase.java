/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.session.container.servlet.examples;

/**
 * @author Paul Ferraro
 */
public class SessionAttributeMutexCounterITCase extends AbstractCounterITCase {

	public SessionAttributeMutexCounterITCase() {
		super(SessionAttributeMutexCountServlet.class);
	}
}
