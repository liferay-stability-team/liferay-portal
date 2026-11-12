/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.object.internal.related.models.util;

import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.object.system.SystemObjectDefinitionManager;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.service.PersistedModelLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.service.PersistedModelLocalServiceRegistryUtil;

import java.util.List;

/**
 * @author Sai Tejesh
 */
public class ObjectRelatedModelsProviderUtil {

	public static <T extends BaseModel<T>> void deleteBaseModels(
			List<T> baseModels,
			SystemObjectDefinitionManager systemObjectDefinitionManager)
		throws PortalException {

		PersistedModelLocalService persistedModelLocalService =
			PersistedModelLocalServiceRegistryUtil.
				getPersistedModelLocalService(
					systemObjectDefinitionManager.getModelClassName());

		for (T baseModel : baseModels) {
			BaseModel<?> curBaseModel =
				(BaseModel<?>)persistedModelLocalService.fetchPersistedModel(
					baseModel.getPrimaryKeyObj());

			if (curBaseModel == null) {
				continue;
			}

			systemObjectDefinitionManager.deleteBaseModel(curBaseModel);
		}
	}

	public static void deleteObjectEntries(
			List<ObjectEntry> objectEntries,
			ObjectEntryService objectEntryService)
		throws PortalException {

		for (ObjectEntry objectEntry : objectEntries) {
			ObjectEntry curObjectEntry = objectEntryService.fetchObjectEntry(
				objectEntry.getObjectEntryId());

			if (curObjectEntry == null) {
				continue;
			}

			objectEntryService.deleteObjectEntry(
				curObjectEntry.getObjectEntryId());
		}
	}

	public static void moveObjectEntriesToTrash(
			List<ObjectEntry> objectEntries,
			ObjectEntryService objectEntryService)
		throws PortalException {

		for (ObjectEntry objectEntry : objectEntries) {
			ObjectEntry curObjectEntry = objectEntryService.fetchObjectEntry(
				objectEntry.getObjectEntryId());

			if ((curObjectEntry == null) || curObjectEntry.isInTrash()) {
				continue;
			}

			objectEntryService.moveObjectEntryToTrash(
				curObjectEntry, new ServiceContext());
		}
	}

	public static void restoreObjectEntriesFromTrash(
			List<ObjectEntry> objectEntries,
			ObjectEntryService objectEntryService)
		throws PortalException {

		for (ObjectEntry objectEntry : objectEntries) {
			ObjectEntry curObjectEntry = objectEntryService.fetchObjectEntry(
				objectEntry.getObjectEntryId());

			if ((curObjectEntry == null) || !curObjectEntry.isInTrash()) {
				continue;
			}

			objectEntryService.restoreObjectEntryFromTrash(
				curObjectEntry, new ServiceContext());
		}
	}

}