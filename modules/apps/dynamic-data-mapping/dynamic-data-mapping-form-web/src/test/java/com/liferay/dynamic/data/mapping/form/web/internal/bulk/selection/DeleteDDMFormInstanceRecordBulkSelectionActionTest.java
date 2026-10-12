/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.form.web.internal.bulk.selection;

import com.liferay.bulk.selection.BulkSelection;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceReport;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceReportLocalService;
import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Sai Tejesh
 */
public class DeleteDDMFormInstanceRecordBulkSelectionActionTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		ReflectionTestUtil.setFieldValue(
			_deleteDDMFormInstanceRecordBulkSelectionAction,
			"_ddmFormInstanceRecordLocalService",
			_ddmFormInstanceRecordLocalService);
		ReflectionTestUtil.setFieldValue(
			_deleteDDMFormInstanceRecordBulkSelectionAction,
			"_ddmFormInstanceRecordModelResourcePermission",
			_ddmFormInstanceRecordModelResourcePermission);
		ReflectionTestUtil.setFieldValue(
			_deleteDDMFormInstanceRecordBulkSelectionAction,
			"_ddmFormInstanceReportLocalService",
			_ddmFormInstanceReportLocalService);

		Mockito.when(
			_ddmFormInstanceRecordLocalService.getActionableDynamicQuery()
		).thenReturn(
			_actionableDynamicQuery
		);

		Mockito.when(
			_ddmFormInstanceReportLocalService.
				getFormInstanceReportByFormInstanceId(_FORM_INSTANCE_ID)
		).thenReturn(
			_ddmFormInstanceReport
		);

		Mockito.when(
			_ddmFormInstanceReportLocalService.updateDDMFormInstanceReport(
				_ddmFormInstanceReport)
		).thenReturn(
			_ddmFormInstanceReport
		);

		_permissionCheckerFactoryUtilMockedStatic.when(
			() -> PermissionCheckerFactoryUtil.create(Mockito.any())
		).thenReturn(
			_permissionChecker
		);
	}

	@After
	public void tearDown() {
		_permissionCheckerFactoryUtilMockedStatic.close();
	}

	@Test
	public void testExecute() throws Exception {
		DDMFormInstanceRecord ddmFormInstanceRecord1 = Mockito.mock(
			DDMFormInstanceRecord.class);
		DDMFormInstanceRecord ddmFormInstanceRecord2 = Mockito.mock(
			DDMFormInstanceRecord.class);

		Mockito.when(
			_ddmFormInstanceRecordModelResourcePermission.contains(
				_permissionChecker, ddmFormInstanceRecord1, ActionKeys.DELETE)
		).thenReturn(
			true
		);

		_deleteDDMFormInstanceRecordBulkSelectionAction.execute(
			Mockito.mock(User.class),
			_getBulkSelection(ddmFormInstanceRecord1, ddmFormInstanceRecord2),
			Collections.emptyMap());

		Mockito.verify(
			_ddmFormInstanceRecordLocalService
		).deleteFormInstanceRecord(
			ddmFormInstanceRecord1, false
		);

		Mockito.verify(
			_ddmFormInstanceRecordLocalService, Mockito.never()
		).deleteFormInstanceRecord(
			Mockito.eq(ddmFormInstanceRecord2), Mockito.anyBoolean()
		);

		_assertFormInstanceReportRebuilt();
	}

	@Test
	public void testExecuteWhenDeletionFails() throws Exception {
		BulkSelection<DDMFormInstanceRecord> bulkSelection =
			_getBulkSelection();

		PortalException portalException1 = new PortalException();

		Mockito.doThrow(
			portalException1
		).when(
			bulkSelection
		).forEach(
			Mockito.any()
		);

		try {
			_deleteDDMFormInstanceRecordBulkSelectionAction.execute(
				Mockito.mock(User.class), bulkSelection,
				Collections.emptyMap());

			Assert.fail();
		}
		catch (PortalException portalException2) {
			Assert.assertSame(portalException1, portalException2);
		}

		_assertFormInstanceReportRebuilt();
	}

	private void _assertFormInstanceReportRebuilt() throws Exception {
		Mockito.verify(
			_ddmFormInstanceReport
		).setData(
			StringPool.BLANK
		);

		Mockito.verify(
			_ddmFormInstanceReportLocalService
		).updateDDMFormInstanceReport(
			_ddmFormInstanceReport
		);

		Mockito.verify(
			_actionableDynamicQuery
		).performActions();
	}

	private BulkSelection<DDMFormInstanceRecord> _getBulkSelection(
			DDMFormInstanceRecord... ddmFormInstanceRecords)
		throws Exception {

		BulkSelection<DDMFormInstanceRecord> bulkSelection = Mockito.mock(
			BulkSelection.class);

		Mockito.doAnswer(
			invocation -> {
				UnsafeConsumer<DDMFormInstanceRecord, Exception>
					unsafeConsumer = invocation.getArgument(0);

				for (DDMFormInstanceRecord ddmFormInstanceRecord :
						ddmFormInstanceRecords) {

					unsafeConsumer.accept(ddmFormInstanceRecord);
				}

				return null;
			}
		).when(
			bulkSelection
		).forEach(
			Mockito.any()
		);

		Mockito.when(
			bulkSelection.getParameterMap()
		).thenReturn(
			HashMapBuilder.put(
				"formInstanceId",
				new String[] {String.valueOf(_FORM_INSTANCE_ID)}
			).build()
		);

		return bulkSelection;
	}

	private static final long _FORM_INSTANCE_ID = RandomTestUtil.randomLong();

	private final ActionableDynamicQuery _actionableDynamicQuery = Mockito.mock(
		ActionableDynamicQuery.class);
	private final DDMFormInstanceRecordLocalService
		_ddmFormInstanceRecordLocalService = Mockito.mock(
			DDMFormInstanceRecordLocalService.class);
	private final ModelResourcePermission<DDMFormInstanceRecord>
		_ddmFormInstanceRecordModelResourcePermission = Mockito.mock(
			ModelResourcePermission.class);
	private final DDMFormInstanceReport _ddmFormInstanceReport = Mockito.mock(
		DDMFormInstanceReport.class);
	private final DDMFormInstanceReportLocalService
		_ddmFormInstanceReportLocalService = Mockito.mock(
			DDMFormInstanceReportLocalService.class);
	private final DeleteDDMFormInstanceRecordBulkSelectionAction
		_deleteDDMFormInstanceRecordBulkSelectionAction =
			new DeleteDDMFormInstanceRecordBulkSelectionAction();
	private final PermissionChecker _permissionChecker = Mockito.mock(
		PermissionChecker.class);
	private final MockedStatic<PermissionCheckerFactoryUtil>
		_permissionCheckerFactoryUtilMockedStatic = Mockito.mockStatic(
			PermissionCheckerFactoryUtil.class);

}