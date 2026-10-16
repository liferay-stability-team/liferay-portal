/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.form.web.internal.display.context;

import com.liferay.dynamic.data.mapping.form.builder.context.DDMFormBuilderContextFactory;
import com.liferay.dynamic.data.mapping.form.builder.context.DDMFormContextDeserializer;
import com.liferay.dynamic.data.mapping.form.builder.settings.DDMFormBuilderSettingsRetriever;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldTypeServicesRegistry;
import com.liferay.dynamic.data.mapping.form.renderer.DDMFormRenderer;
import com.liferay.dynamic.data.mapping.form.renderer.DDMFormTemplateContextFactory;
import com.liferay.dynamic.data.mapping.form.values.factory.DDMFormValuesFactory;
import com.liferay.dynamic.data.mapping.form.web.internal.configuration.DDMFormWebConfiguration;
import com.liferay.dynamic.data.mapping.io.DDMFormFieldTypesSerializer;
import com.liferay.dynamic.data.mapping.io.exporter.DDMFormInstanceRecordWriterRegistry;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceVersionLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureService;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.dynamic.data.mapping.storage.DDMStorageAdapterRegistry;
import com.liferay.dynamic.data.mapping.util.DDMFormValuesMerger;
import com.liferay.frontend.js.loader.modules.extender.npm.NPMResolver;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.portlet.PortalPreferences;
import com.liferay.portal.kernel.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderResponse;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletURL;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import jakarta.portlet.PortletRequest;
import jakarta.portlet.RenderRequest;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Aswin Narayanadas
 */
public class DDMFormAdminFieldSetDisplayContextTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		_setUpPortalUtil();
		_setUpDDMFormAdminFieldSetDisplayContext();
	}

	@Test
	public void testGetPortletURLContainsElementSetCurrentTab() {
		PortalPreferences portalPreferences = Mockito.mock(
			PortalPreferences.class);

		try (MockedStatic<PortletPreferencesFactoryUtil> mockedStatic =
				Mockito.mockStatic(PortletPreferencesFactoryUtil.class)) {

			mockedStatic.when(
				() -> PortletPreferencesFactoryUtil.getPortalPreferences(
					Mockito.any(HttpServletRequest.class))
			).thenReturn(
				portalPreferences
			);

			mockedStatic.when(
				() -> PortletPreferencesFactoryUtil.getPortalPreferences(
					Mockito.any(PortletRequest.class))
			).thenReturn(
				portalPreferences
			);

			MockLiferayPortletURL mockLiferayPortletURL =
				(MockLiferayPortletURL)
					_ddmFormAdminFieldSetDisplayContext.getPortletURL();

			Assert.assertEquals(
				"element-set",
				mockLiferayPortletURL.getParameter("currentTab"));
			Assert.assertEquals(
				"/admin/view.jsp",
				mockLiferayPortletURL.getParameter("mvcPath"));
		}
	}

	@SuppressWarnings("unchecked")
	private void _setUpDDMFormAdminFieldSetDisplayContext() throws Exception {
		_renderRequest = Mockito.mock(RenderRequest.class);

		_ddmFormAdminFieldSetDisplayContext =
			new DDMFormAdminFieldSetDisplayContext(
				_renderRequest, new MockLiferayPortletRenderResponse(),
				Mockito.mock(DDMFormBuilderContextFactory.class),
				Mockito.mock(DDMFormBuilderSettingsRetriever.class),
				(DDMFormContextDeserializer<DDMFormValues>)Mockito.mock(
					DDMFormContextDeserializer.class),
				Mockito.mock(DDMFormFieldTypeServicesRegistry.class),
				Mockito.mock(DDMFormFieldTypesSerializer.class),
				Mockito.mock(DDMFormInstanceLocalService.class),
				Mockito.mock(DDMFormInstanceRecordLocalService.class),
				Mockito.mock(DDMFormInstanceRecordService.class),
				Mockito.mock(DDMFormInstanceRecordWriterRegistry.class),
				Mockito.mock(DDMFormInstanceService.class),
				Mockito.mock(DDMFormInstanceVersionLocalService.class),
				Mockito.mock(DDMFormRenderer.class),
				Mockito.mock(DDMFormTemplateContextFactory.class),
				Mockito.mock(DDMFormValuesFactory.class),
				Mockito.mock(DDMFormValuesMerger.class),
				Mockito.mock(DDMFormWebConfiguration.class),
				Mockito.mock(DDMStorageAdapterRegistry.class),
				Mockito.mock(DDMStructureLocalService.class),
				Mockito.mock(DDMStructureService.class),
				Mockito.mock(JSONFactory.class),
				Mockito.mock(NPMResolver.class), null,
				Mockito.mock(Portal.class));
	}

	private void _setUpPortalUtil() {
		PortalUtil portalUtil = new PortalUtil();

		Portal portal = Mockito.mock(Portal.class);

		ThemeDisplay themeDisplay = Mockito.mock(ThemeDisplay.class);

		Mockito.when(
			themeDisplay.getScopeGroupId()
		).thenReturn(
			RandomTestUtil.randomLong()
		);

		HttpServletRequest httpServletRequest = Mockito.mock(
			HttpServletRequest.class);

		Mockito.when(
			httpServletRequest.getAttribute(WebKeys.THEME_DISPLAY)
		).thenReturn(
			themeDisplay
		);

		Mockito.when(
			portal.getHttpServletRequest(Mockito.any(PortletRequest.class))
		).thenReturn(
			httpServletRequest
		);

		portalUtil.setPortal(portal);
	}

	private DDMFormAdminFieldSetDisplayContext
		_ddmFormAdminFieldSetDisplayContext;
	private RenderRequest _renderRequest;

}