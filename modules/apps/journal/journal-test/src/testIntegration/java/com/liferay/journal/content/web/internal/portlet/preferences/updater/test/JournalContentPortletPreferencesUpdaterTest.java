/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.journal.content.web.internal.portlet.preferences.updater.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.journal.constants.JournalContentPortletKeys;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.layout.portlet.preferences.updater.PortletPreferencesUpdater;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import jakarta.portlet.PortletPreferences;

import org.junit.After;
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
public class JournalContentPortletPreferencesUpdaterTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		ServiceContextThreadLocal.pushServiceContext(
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	@After
	public void tearDown() throws Exception {
		ServiceContextThreadLocal.popServiceContext();
	}

	@Test
	public void testUpdatePortletPreferences() throws Exception {
		Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

		String portletId = LayoutTestUtil.addPortletToLayout(
			layout, JournalContentPortletKeys.JOURNAL_CONTENT);

		PortletPreferences portletPreferences =
			PortletPreferencesFactoryUtil.getStrictPortletSetup(
				layout, portletId);

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setLayout(layout);
		themeDisplay.setScopeGroupId(_group.getGroupId());

		Company company = _companyLocalService.getCompany(
			_group.getCompanyId());

		JournalArticle journalArticle1 = JournalTestUtil.addArticle(
			company.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_portletPreferencesUpdater.updatePortletPreferences(
			JournalArticle.class.getName(),
			journalArticle1.getResourcePrimKey(), portletId, portletPreferences,
			themeDisplay);

		Group companyGroup = company.getGroup();

		Assert.assertEquals(
			journalArticle1.getExternalReferenceCode(),
			portletPreferences.getValue("articleExternalReferenceCode", null));
		Assert.assertEquals(
			companyGroup.getExternalReferenceCode(),
			portletPreferences.getValue("groupExternalReferenceCode", null));

		JournalArticle journalArticle2 = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_portletPreferencesUpdater.updatePortletPreferences(
			JournalArticle.class.getName(),
			journalArticle2.getResourcePrimKey(), portletId, portletPreferences,
			themeDisplay);

		Assert.assertEquals(
			journalArticle2.getExternalReferenceCode(),
			portletPreferences.getValue("articleExternalReferenceCode", null));

		Assert.assertNull(
			portletPreferences.getValue("groupExternalReferenceCode", null));
	}

	@Inject
	private CompanyLocalService _companyLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject(
		filter = "model.class.name=com.liferay.journal.model.JournalArticle"
	)
	private PortletPreferencesUpdater _portletPreferencesUpdater;

}