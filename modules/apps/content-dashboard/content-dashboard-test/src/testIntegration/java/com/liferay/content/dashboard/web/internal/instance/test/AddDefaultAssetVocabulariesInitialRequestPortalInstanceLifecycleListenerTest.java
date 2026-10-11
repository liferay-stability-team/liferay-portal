/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.content.dashboard.web.internal.instance.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.model.AssetCategoryConstants;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.portal.instance.lifecycle.PortalInstanceLifecycleListener;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.portlet.asset.util.AssetVocabularySettingsHelper;

import java.util.Arrays;
import java.util.Set;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Attila Bakay
 */
@RunWith(Arquillian.class)
public class
	AddDefaultAssetVocabulariesInitialRequestPortalInstanceLifecycleListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Test
	public void testPortalInstanceRegistered() throws Exception {
		Company company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());

		AssetVocabulary assetVocabulary =
			_assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

		long[] selectedClassNameIds = assetVocabulary.getSelectedClassNameIds();

		long classNameId = selectedClassNameIds[0];

		Set<Long> classNameIds = SetUtil.fromArray(selectedClassNameIds);

		classNameIds.remove(classNameId);

		_updateVocabulary(assetVocabulary, ArrayUtil.toLongArray(classNameIds));

		try {
			_portalInstanceLifecycleListener.portalInstanceRegistered(company);

			assetVocabulary = _assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

			Assert.assertFalse(
				ArrayUtil.contains(
					assetVocabulary.getSelectedClassNameIds(), classNameId));
		}
		finally {
			_updateVocabulary(assetVocabulary, selectedClassNameIds);
		}
	}

	@Test
	public void testPortalInstanceRegisteredPreservesClassTypePKs()
		throws Exception {

		Company company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());

		AssetVocabulary assetVocabulary =
			_assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

		String settings = assetVocabulary.getSettings();

		try {
			long classNameId = assetVocabulary.getSelectedClassNameIds()[0];
			long classTypePK = RandomTestUtil.randomLong();

			_updateVocabulary(
				assetVocabulary, new long[] {classNameId},
				new long[] {classTypePK}, new boolean[] {false},
				new boolean[] {true});

			_portalInstanceLifecycleListener.portalInstanceRegistered(company);

			assetVocabulary = _assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

			Assert.assertTrue(
				ArrayUtil.contains(
					assetVocabulary.getSelectedClassTypePKs(), classTypePK));

			AssetVocabularySettingsHelper assetVocabularySettingsHelper =
				new AssetVocabularySettingsHelper(
					assetVocabulary.getSettings());

			Assert.assertTrue(
				assetVocabularySettingsHelper.
					isClassNameIdAndClassTypePKRequired(
						classNameId, classTypePK));
		}
		finally {
			_restoreVocabulary(assetVocabulary, settings);
		}
	}

	@Test
	public void testPortalInstanceRegisteredPreservesDepotRequiredClassNameIds()
		throws Exception {

		Company company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());

		AssetVocabulary assetVocabulary =
			_assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

		String settings = assetVocabulary.getSettings();

		try {
			long classNameId = assetVocabulary.getSelectedClassNameIds()[0];

			_updateVocabulary(
				assetVocabulary, new long[] {classNameId},
				new long[] {AssetCategoryConstants.ALL_CLASS_TYPE_PK},
				new boolean[] {true}, new boolean[] {false});

			_portalInstanceLifecycleListener.portalInstanceRegistered(company);

			assetVocabulary = _assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

			AssetVocabularySettingsHelper assetVocabularySettingsHelper =
				new AssetVocabularySettingsHelper(
					assetVocabulary.getSettings());

			Assert.assertTrue(
				assetVocabularySettingsHelper.
					isClassNameIdAndClassTypePKDepotRequired(
						classNameId, AssetCategoryConstants.ALL_CLASS_TYPE_PK));
		}
		finally {
			_restoreVocabulary(assetVocabulary, settings);
		}
	}

	@Test
	public void testPortalInstanceRegisteredPreservesRequiredClassNameIds()
		throws Exception {

		Company company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());

		AssetVocabulary assetVocabulary =
			_assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

		String settings = assetVocabulary.getSettings();

		try {
			long classNameId = assetVocabulary.getSelectedClassNameIds()[0];

			_updateVocabulary(
				assetVocabulary, new long[] {classNameId},
				new boolean[] {true});

			_portalInstanceLifecycleListener.portalInstanceRegistered(company);

			assetVocabulary = _assetVocabularyLocalService.fetchGroupVocabulary(
				company.getGroupId(), "audience");

			AssetVocabularySettingsHelper assetVocabularySettingsHelper =
				new AssetVocabularySettingsHelper(
					assetVocabulary.getSettings());

			Assert.assertTrue(
				assetVocabularySettingsHelper.
					isClassNameIdAndClassTypePKRequired(
						classNameId, AssetCategoryConstants.ALL_CLASS_TYPE_PK));
		}
		finally {
			_restoreVocabulary(assetVocabulary, settings);
		}
	}

	private void _restoreVocabulary(
			AssetVocabulary assetVocabulary, String settings)
		throws Exception {

		_assetVocabularyLocalService.updateVocabulary(
			assetVocabulary.getExternalReferenceCode(),
			assetVocabulary.getVocabularyId(), assetVocabulary.getTitleMap(),
			assetVocabulary.getDescriptionMap(), settings,
			assetVocabulary.getVisibilityType());
	}

	private void _updateVocabulary(
			AssetVocabulary assetVocabulary, long[] classNameIds)
		throws Exception {

		boolean[] requireds = new boolean[classNameIds.length];

		Arrays.fill(requireds, false);

		_updateVocabulary(assetVocabulary, classNameIds, requireds);
	}

	private void _updateVocabulary(
			AssetVocabulary assetVocabulary, long[] classNameIds,
			boolean[] requireds)
		throws Exception {

		long[] classTypePKs = new long[classNameIds.length];

		Arrays.fill(classTypePKs, AssetCategoryConstants.ALL_CLASS_TYPE_PK);

		boolean[] depotRequireds = new boolean[classNameIds.length];

		Arrays.fill(depotRequireds, false);

		_updateVocabulary(
			assetVocabulary, classNameIds, classTypePKs, depotRequireds,
			requireds);
	}

	private void _updateVocabulary(
			AssetVocabulary assetVocabulary, long[] classNameIds,
			long[] classTypePKs, boolean[] depotRequireds, boolean[] requireds)
		throws Exception {

		AssetVocabularySettingsHelper assetVocabularySettingsHelper =
			new AssetVocabularySettingsHelper(assetVocabulary.getSettings());

		assetVocabularySettingsHelper.setClassNameIdsAndClassTypePKs(
			classNameIds, classTypePKs, depotRequireds, requireds);

		_assetVocabularyLocalService.updateVocabulary(
			assetVocabulary.getExternalReferenceCode(),
			assetVocabulary.getVocabularyId(), assetVocabulary.getTitleMap(),
			assetVocabulary.getDescriptionMap(),
			assetVocabularySettingsHelper.toString(),
			assetVocabulary.getVisibilityType());
	}

	@Inject
	private AssetVocabularyLocalService _assetVocabularyLocalService;

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject(
		filter = "component.name=com.liferay.content.dashboard.web.internal.instance.lifecycle.AddDefaultAssetVocabulariesInitialRequestPortalInstanceLifecycleListener"
	)
	private PortalInstanceLifecycleListener _portalInstanceLifecycleListener;

}