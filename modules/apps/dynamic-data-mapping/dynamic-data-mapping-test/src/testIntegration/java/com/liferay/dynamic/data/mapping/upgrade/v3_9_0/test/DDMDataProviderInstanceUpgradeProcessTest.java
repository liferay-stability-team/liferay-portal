/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.upgrade.v3_9_0.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.dynamic.data.mapping.model.DDMDataProviderInstance;
import com.liferay.dynamic.data.mapping.service.DDMDataProviderInstanceLocalService;
import com.liferay.dynamic.data.mapping.test.util.DDMFormTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMFormValuesTestUtil;
import com.liferay.portal.kernel.dao.orm.EntityCacheUtil;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.upgrade.registry.UpgradeStepRegistrator;
import com.liferay.portal.upgrade.test.util.UpgradeTestUtil;

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
public class DDMDataProviderInstanceUpgradeProcessTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testUpgradeLabelValueOutputParameterPath() throws Exception {
		_ddmDataProviderInstance = _addDDMDataProviderInstance(
			"$..label;$..value");

		_runUpgrade();

		_assertOutputParameterPath("$..label;$..value");
	}

	@Test
	public void testUpgradeLegacyListOutputParameterPath() throws Exception {
		_ddmDataProviderInstance = _addDDMDataProviderInstance(
			"nameCurrentValue;List");

		_runUpgrade();

		_assertOutputParameterPath("$..nameCurrentValue");
	}

	private DDMDataProviderInstance _addDDMDataProviderInstance(
			String outputParameterPath)
		throws Exception {

		DDMDataProviderInstance ddmDataProviderInstance =
			_ddmDataProviderInstanceLocalService.addDataProviderInstance(
				TestPropsValues.getUserId(), _group.getGroupId(),
				RandomTestUtil.randomLocaleStringMap(), null,
				DDMFormValuesTestUtil.createDDMFormValues(
					DDMFormTestUtil.createDDMForm()),
				"rest",
				ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		ddmDataProviderInstance.setDefinition(
			JSONUtil.put(
				"availableLanguageIds", JSONUtil.put("en_US")
			).put(
				"defaultLanguageId", "en_US"
			).put(
				"fieldValues",
				JSONUtil.put(
					JSONUtil.put(
						"instanceId", RandomTestUtil.randomString()
					).put(
						"name", "outputParameters"
					).put(
						"nestedFieldValues",
						JSONUtil.put(
							JSONUtil.put(
								"instanceId", RandomTestUtil.randomString()
							).put(
								"name", "outputParameterPath"
							).put(
								"value", outputParameterPath
							))
					))
			).toString());

		return _ddmDataProviderInstanceLocalService.
			updateDDMDataProviderInstance(ddmDataProviderInstance);
	}

	private void _assertOutputParameterPath(String expectedOutputParameterPath)
		throws Exception {

		EntityCacheUtil.clearCache();

		DDMDataProviderInstance ddmDataProviderInstance =
			_ddmDataProviderInstanceLocalService.getDataProviderInstance(
				_ddmDataProviderInstance.getDataProviderInstanceId());

		JSONObject definitionJSONObject = JSONFactoryUtil.createJSONObject(
			ddmDataProviderInstance.getDefinition());

		JSONArray fieldValuesJSONArray = definitionJSONObject.getJSONArray(
			"fieldValues");

		JSONObject fieldValueJSONObject = fieldValuesJSONArray.getJSONObject(0);

		JSONArray nestedFieldValuesJSONArray =
			fieldValueJSONObject.getJSONArray("nestedFieldValues");

		JSONObject nestedFieldValueJSONObject =
			nestedFieldValuesJSONArray.getJSONObject(0);

		Assert.assertEquals(
			expectedOutputParameterPath,
			nestedFieldValueJSONObject.getString("value"));
	}

	private void _runUpgrade() throws Exception {
		UpgradeProcess upgradeProcess = UpgradeTestUtil.getUpgradeStep(
			_upgradeStepRegistrator,
			"com.liferay.dynamic.data.mapping.internal.upgrade.v3_9_0." +
				"DDMDataProviderInstanceUpgradeProcess");

		upgradeProcess.upgrade();
	}

	@DeleteAfterTestRun
	private DDMDataProviderInstance _ddmDataProviderInstance;

	@Inject
	private DDMDataProviderInstanceLocalService
		_ddmDataProviderInstanceLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject(
		filter = "(&(component.name=com.liferay.dynamic.data.mapping.internal.upgrade.registry.DDMServiceUpgradeStepRegistrator))"
	)
	private UpgradeStepRegistrator _upgradeStepRegistrator;

}