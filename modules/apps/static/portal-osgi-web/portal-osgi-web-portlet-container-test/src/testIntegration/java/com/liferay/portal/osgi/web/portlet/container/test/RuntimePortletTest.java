/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.osgi.web.portlet.container.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.portlet.PortletURLFactoryUtil;
import com.liferay.portal.kernel.portlet.url.builder.PortletURLBuilder;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.HashMapDictionary;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.osgi.web.portlet.container.test.util.PortletContainerTestUtil;
import com.liferay.portal.test.rule.Inject;

import jakarta.portlet.PortletContext;
import jakarta.portlet.PortletException;
import jakarta.portlet.PortletRequest;
import jakarta.portlet.PortletRequestDispatcher;
import jakarta.portlet.PortletURL;
import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;
import jakarta.portlet.ResourceRequest;
import jakarta.portlet.ResourceResponse;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.PrintWriter;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Saurasish Basak
 */
@RunWith(Arquillian.class)
public class RuntimePortletTest extends BasePortletContainerTestCase {

	@Test
	public void testCreateVocabularyOnRuntimePortlet() throws Exception {
		AssetVocabulary assetVocabulary =
			_assetVocabularyLocalService.addVocabulary(
				TestPropsValues.getUserId(), layout.getGroupId(),
				RandomTestUtil.randomString(), new ServiceContext());

		testPortlet = new TestPortlet() {

			@Override
			public void render(
					RenderRequest renderRequest, RenderResponse renderResponse)
				throws IOException, PortletException {

				PortletContext portletContext = getPortletContext();

				PortletRequestDispatcher portletRequestDispatcher =
					portletContext.getRequestDispatcher(
						"/runtime_asset_categories_admin_portlet.jsp");

				portletRequestDispatcher.include(
					renderRequest, renderResponse);
			}

			@Override
			public void serveResource(
					ResourceRequest resourceRequest,
					ResourceResponse resourceResponse)
				throws IOException {

				PrintWriter printWriter = resourceResponse.getWriter();

				PortletURL portletURL = resourceResponse.createActionURL();

				printWriter.write(
					MapUtil.getString(
						HttpComponentsUtil.getParameterMap(
							HttpComponentsUtil.getQueryString(
								portletURL.toString())),
						"p_auth"));
			}

		};

		setUpPortlet(
			testPortlet, new HashMapDictionary<String, Object>(),
			TEST_PORTLET_ID);

		HttpServletRequest httpServletRequest =
			PortletContainerTestUtil.getHttpServletRequest(group, layout);

		PortletContainerTestUtil.Response renderResponse =
			PortletContainerTestUtil.request(
				layout.getRegularURL(httpServletRequest));

		Assert.assertEquals(200, renderResponse.getCode());

		PortletContainerTestUtil.Response authResponse =
			PortletContainerTestUtil.getPortalAuthentication(
				httpServletRequest, layout, TEST_PORTLET_ID);

		String embeddedPortletInstanceKey = PortletIdCodec.encode(
			_ASSET_CATEGORIES_ADMIN_PORTLET_NAME, _INSTANCE_ID);

		String actionURL = PortletURLBuilder.create(
			PortletURLFactoryUtil.create(
				httpServletRequest, embeddedPortletInstanceKey,
				layout.getPlid(), PortletRequest.ACTION_PHASE)
		).setActionName(
			"/asset_categories_admin/edit_asset_vocabulary"
		).setParameter(
			"title", "Test Vocabulary"
		).setParameter(
			"title_" + LocaleUtil.toLanguageId(LocaleUtil.getDefault()),
			"Test Vocabulary"
		).setParameter(
			"vocabularyId", assetVocabulary.getVocabularyId()
		).buildString();

		actionURL = HttpComponentsUtil.setParameter(
			actionURL, "p_auth", authResponse.getBody());

		String location = _getRedirectLocation(
			actionURL, authResponse.getCookies());

		Assert.assertNotNull(
			"Expected the action to issue a redirect after creating the " +
				"vocabulary, but no Location header was returned",
			location);

		String embeddedPortletNamespace = StringUtil.toLowerCase(
			"_" + embeddedPortletInstanceKey + "_");

		Assert.assertTrue(
			"Expected the redirect Location to target the embedded " +
				"vocabulary view (containing the embedded portlet namespace " +
					embeddedPortletNamespace + " and vocabularyId=" +
						assetVocabulary.getVocabularyId() + "), but was: " +
							location,
			location.contains(embeddedPortletNamespace) &&
			location.contains(
				"vocabularyId=" + assetVocabulary.getVocabularyId()));
	}

	private String _getRedirectLocation(String url, List<String> cookies)
		throws IOException {

		HttpURLConnection httpURLConnection =
			(HttpURLConnection)new URL(url).openConnection();

		httpURLConnection.setInstanceFollowRedirects(false);

		if (cookies != null) {
			for (String cookie : cookies) {
				httpURLConnection.addRequestProperty(
					"Cookie", cookie.split(";", 2)[0]);
			}
		}

		try {
			httpURLConnection.connect();

			return httpURLConnection.getHeaderField("Location");
		}
		finally {
			httpURLConnection.disconnect();
		}
	}

	private static final String _ASSET_CATEGORIES_ADMIN_PORTLET_NAME =
		"com_liferay_asset_categories_admin_web_portlet_" +
			"AssetCategoriesAdminPortlet";

	private static final String _INSTANCE_ID = "categoriesadmin001";

	@Inject
	private AssetVocabularyLocalService _assetVocabularyLocalService;

}
