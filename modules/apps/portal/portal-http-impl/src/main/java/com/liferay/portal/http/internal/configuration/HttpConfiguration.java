/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.http.internal.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * @author Eric Yan
 */
@ExtendedObjectClassDefinition(category = "infrastructure")
@Meta.OCD(
	id = "com.liferay.portal.http.internal.configuration.HttpConfiguration",
	localization = "content/Language", name = "http-configuration-name"
)
public interface HttpConfiguration {

	@Meta.AD(
		deflt = "Mozilla/5.0 (Windows NT 6.3; Trident/7.0; rv 11.0) like Gecko",
		description = "default-user-agent-help", name = "default-user-agent",
		required = false
	)
	public String defaultUserAgent();

	@Meta.AD(
		deflt = "0", description = "keep-alive-timeout-help",
		name = "keep-alive-timeout", required = false
	)
	public int keepAliveTimeout();

	@Meta.AD(
		deflt = "false", description = "tcp-keep-alive-enabled-help",
		name = "tcp-keep-alive-enabled", required = false
	)
	public boolean tcpKeepAliveEnabled();

}