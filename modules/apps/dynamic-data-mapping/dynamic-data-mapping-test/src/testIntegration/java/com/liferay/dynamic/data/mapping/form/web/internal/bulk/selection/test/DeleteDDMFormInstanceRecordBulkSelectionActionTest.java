/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.form.web.internal.bulk.selection.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.bulk.selection.BulkSelectionAction;
import com.liferay.bulk.selection.BulkSelectionFactory;
import com.liferay.dynamic.data.mapping.model.DDMFormInstance;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceReport;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceReportLocalService;
import com.liferay.dynamic.data.mapping.test.util.DDMFormInstanceRecordTestUtil;
import com.liferay.dynamic.data.mapping.test.util.DDMFormInstanceTestUtil;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.util.Collections;
import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Sai Tejesh
 */
@RunWith(Arquillian.class)
public class DeleteDDMFormInstanceRecordBulkSelectionActionTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testExecute() throws Exception {
		DDMFormInstance ddmFormInstance =
			DDMFormInstanceTestUtil.addDDMFormInstance(
				_group, TestPropsValues.getUserId());

		DDMFormInstanceRecord ddmFormInstanceRecord1 =
			DDMFormInstanceRecordTestUtil.
				addDDMFormInstanceRecordWithRandomValues(
					ddmFormInstance, _group, TestPropsValues.getUserId());

		DDMFormInstanceRecord ddmFormInstanceRecord2 =
			DDMFormInstanceRecordTestUtil.
				addDDMFormInstanceRecordWithRandomValues(
					ddmFormInstance, _group, TestPropsValues.getUserId());

		Date createDate = ddmFormInstanceRecord2.getCreateDate();

		ddmFormInstanceRecord1.setCreateDate(
			new Date(createDate.getTime() - 1000));

		ddmFormInstanceRecord1 =
			_ddmFormInstanceRecordLocalService.updateDDMFormInstanceRecord(
				ddmFormInstanceRecord1);

		_deleteDDMFormInstanceRecordBulkSelectionAction.execute(
			TestPropsValues.getUser(),
			_ddmFormInstanceRecordBulkSelectionFactory.create(
				HashMapBuilder.put(
					"createDate",
					new String[] {String.valueOf(createDate.getTime() - 1)}
				).put(
					"formInstanceId",
					new String[] {
						String.valueOf(ddmFormInstance.getFormInstanceId())
					}
				).build()),
			Collections.emptyMap());

		Assert.assertNull(
			_ddmFormInstanceRecordLocalService.fetchDDMFormInstanceRecord(
				ddmFormInstanceRecord1.getFormInstanceRecordId()));
		Assert.assertNotNull(
			_ddmFormInstanceRecordLocalService.fetchDDMFormInstanceRecord(
				ddmFormInstanceRecord2.getFormInstanceRecordId()));

		DDMFormInstanceReport ddmFormInstanceReport =
			_ddmFormInstanceReportLocalService.
				getFormInstanceReportByFormInstanceId(
					ddmFormInstance.getFormInstanceId());

		JSONObject jsonObject = _jsonFactory.createJSONObject(
			ddmFormInstanceReport.getData());

		Assert.assertEquals(1, jsonObject.getInt("totalItems"));
	}

	@Inject(
		filter = "model.class.name=com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord"
	)
	private BulkSelectionFactory<DDMFormInstanceRecord>
		_ddmFormInstanceRecordBulkSelectionFactory;

	@Inject
	private DDMFormInstanceRecordLocalService
		_ddmFormInstanceRecordLocalService;

	@Inject
	private DDMFormInstanceReportLocalService
		_ddmFormInstanceReportLocalService;

	@Inject(
		filter = "bulk.selection.action.key=delete.ddm.form.instance.record"
	)
	private BulkSelectionAction<DDMFormInstanceRecord>
		_deleteDDMFormInstanceRecordBulkSelectionAction;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private JSONFactory _jsonFactory;

}