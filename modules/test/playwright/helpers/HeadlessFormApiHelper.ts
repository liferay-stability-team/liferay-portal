/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {ApiHelpers, DataApiHelpers} from './ApiHelpers';

type TForm = {
	id: number;
	name: string;
};

export class HeadlessFormApiHelper {
	readonly apiHelpers: ApiHelpers | DataApiHelpers;
	readonly basePath: string;

	constructor(apiHelpers: ApiHelpers) {
		this.apiHelpers = apiHelpers;
		this.basePath = 'headless-form/v1.0';
	}

	async getSiteFormsPage(siteId: number | string): Promise<{items: TForm[]}> {
		return this.apiHelpers.get(
			`${this.apiHelpers.baseUrl}${this.basePath}/sites/${siteId}/forms`
		);
	}

	async postFormFormRecord(formId: number) {
		return this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/forms/${formId}/form-records`,
			{
				data: {
					draft: false,
					formFieldValues: [],
				},
				failOnStatusCode: true,
				headers: {
					...(await this.apiHelpers.getCSRFTokenHeader()),
				},
			}
		);
	}
}
