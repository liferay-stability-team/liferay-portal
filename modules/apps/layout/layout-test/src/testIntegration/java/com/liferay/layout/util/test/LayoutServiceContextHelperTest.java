/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.layout.util.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.layout.util.LayoutServiceContextHelperUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.VirtualHostLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.TreeMapBuilder;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Chaitanya Sammetla
 */
@RunWith(Arquillian.class)
public class LayoutServiceContextHelperTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testGetServiceContextAutoCloseableUsesLayoutSetVirtualHostname()
		throws Exception {

		Layout layout = LayoutTestUtil.addTypeContentLayout(_group);

		LayoutSet layoutSet = layout.getLayoutSet();

		String virtualHostname =
			RandomTestUtil.randomString() + ".testvirtualhost.com";

		_virtualHostLocalService.updateVirtualHosts(
			layout.getCompanyId(), layoutSet.getLayoutSetId(),
			TreeMapBuilder.put(
				virtualHostname, StringPool.BLANK
			).build());

		Company company = _companyLocalService.getCompany(
			layout.getCompanyId());

		try (AutoCloseable autoCloseable =
				LayoutServiceContextHelperUtil.getServiceContextAutoCloseable(
					layout)) {

			ServiceContext serviceContext =
				ServiceContextThreadLocal.getServiceContext();

			ThemeDisplay themeDisplay = serviceContext.getThemeDisplay();

			Assert.assertNotNull(themeDisplay);

			Assert.assertEquals(virtualHostname, themeDisplay.getServerName());
			Assert.assertEquals(
				virtualHostname, themeDisplay.getPortalDomain());
			Assert.assertTrue(
				themeDisplay.getPortalURL(),
				themeDisplay.getPortalURL(
				).contains(
					virtualHostname
				));

			Assert.assertNotEquals(
				company.getVirtualHostname(), themeDisplay.getServerName());
		}
	}

	@Inject
	private CompanyLocalService _companyLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private VirtualHostLocalService _virtualHostLocalService;

}