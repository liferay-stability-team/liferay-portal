/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.form.web.internal.bulk.selection;

import com.liferay.bulk.selection.BulkSelection;
import com.liferay.bulk.selection.BulkSelectionAction;
import com.liferay.dynamic.data.mapping.constants.DDMFormInstanceReportConstants;
import com.liferay.dynamic.data.mapping.exception.NoSuchFormInstanceRecordVersionException;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceReport;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordVersionLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceReportLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.Date;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Akhash Ramprakash
 */
@Component(
	property = "bulk.selection.action.key=delete.ddm.form.instance.record",
	service = BulkSelectionAction.class
)
public class DeleteDDMFormInstanceRecordBulkSelectionAction
	implements BulkSelectionAction<DDMFormInstanceRecord> {

	@Override
	public void execute(
			User user, BulkSelection<DDMFormInstanceRecord> bulkSelection,
			Map<String, Serializable> inputMap)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(user);

		try {
			bulkSelection.forEach(
				ddmFormInstanceRecord -> {
					try {
						if (!_ddmFormInstanceRecordModelResourcePermission.
								contains(
									permissionChecker, ddmFormInstanceRecord,
									ActionKeys.DELETE)) {

							return;
						}

						_ddmFormInstanceRecordLocalService.
							deleteFormInstanceRecord(
								ddmFormInstanceRecord, false);
					}
					catch (Exception exception) {
						if (_log.isWarnEnabled()) {
							_log.warn(exception);
						}
					}
				});
		}
		finally {
			long formInstanceId = MapUtil.getLong(
				bulkSelection.getParameterMap(), "formInstanceId");

			try {
				_rebuildFormInstanceReport(formInstanceId);
			}
			catch (Exception exception) {
				_log.error(
					"Unable to rebuild the report of form instance " +
						formInstanceId,
					exception);
			}
		}
	}

	private void _rebuildFormInstanceReport(long formInstanceId)
		throws PortalException {

		DDMFormInstanceReport ddmFormInstanceReport =
			_ddmFormInstanceReportLocalService.
				getFormInstanceReportByFormInstanceId(formInstanceId);

		Date modifiedDate = new Date();

		ddmFormInstanceReport.setModifiedDate(modifiedDate);

		ddmFormInstanceReport.setData(StringPool.BLANK);

		ddmFormInstanceReport =
			_ddmFormInstanceReportLocalService.updateDDMFormInstanceReport(
				ddmFormInstanceReport);

		long formInstanceReportId =
			ddmFormInstanceReport.getFormInstanceReportId();

		ActionableDynamicQuery actionableDynamicQuery =
			_ddmFormInstanceRecordLocalService.getActionableDynamicQuery();

		actionableDynamicQuery.setAddCriteriaMethod(
			dynamicQuery -> {
				Property formInstanceIdProperty = PropertyFactoryUtil.forName(
					"formInstanceId");

				dynamicQuery.add(formInstanceIdProperty.eq(formInstanceId));

				Property createDateProperty = PropertyFactoryUtil.forName(
					"createDate");

				dynamicQuery.add(createDateProperty.lt(modifiedDate));
			});
		actionableDynamicQuery.setPerformActionMethod(
			(DDMFormInstanceRecord ddmFormInstanceRecord) -> {
				try {
					DDMFormInstanceRecordVersion ddmFormInstanceRecordVersion =
						_ddmFormInstanceRecordVersionLocalService.
							getLatestFormInstanceRecordVersion(
								ddmFormInstanceRecord.getFormInstanceRecordId(),
								WorkflowConstants.STATUS_APPROVED);

					_ddmFormInstanceReportLocalService.
						processFormInstanceReportEvent(
							formInstanceReportId,
							ddmFormInstanceRecordVersion.
								getFormInstanceRecordVersionId(),
							DDMFormInstanceReportConstants.
								EVENT_ADD_RECORD_VERSION);
				}
				catch (NoSuchFormInstanceRecordVersionException
							noSuchFormInstanceRecordVersionException) {

					if (_log.isDebugEnabled()) {
						_log.debug(noSuchFormInstanceRecordVersionException);
					}
				}
				catch (Exception exception) {
					if (_log.isWarnEnabled()) {
						_log.warn(exception);
					}
				}
			});

		actionableDynamicQuery.performActions();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		DeleteDDMFormInstanceRecordBulkSelectionAction.class);

	@Reference
	private DDMFormInstanceRecordLocalService
		_ddmFormInstanceRecordLocalService;

	@Reference(
		target = "(model.class.name=com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord)"
	)
	private ModelResourcePermission<DDMFormInstanceRecord>
		_ddmFormInstanceRecordModelResourcePermission;

	@Reference
	private DDMFormInstanceRecordVersionLocalService
		_ddmFormInstanceRecordVersionLocalService;

	@Reference
	private DDMFormInstanceReportLocalService
		_ddmFormInstanceReportLocalService;

}