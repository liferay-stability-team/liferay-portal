/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {expect, mergeTests} from '@playwright/test';

import {formsPagesTest} from '../../../fixtures/formsPagesTest';
import {isolatedSiteTest} from '../../../fixtures/isolatedSiteTest';
import {loginTest} from '../../../fixtures/loginTest';

const test = mergeTests(formsPagesTest, isolatedSiteTest, loginTest());

test(
	'Cancel on the new element set screen returns to the element sets listing',
	{tag: '@LPD-109680'},
	async ({formsPage, page, site}) => {
		await formsPage.goToElementSets(site.friendlyUrlPath);

		await formsPage.clickManagementToolbarNewButton();

		await expect(
			page.getByRole('heading', {name: 'New Element Set'})
		).toBeVisible();

		await page.getByRole('link', {exact: true, name: 'Cancel'}).click();

		await expect(
			page.getByRole('link', {exact: true, name: 'Element Sets'})
		).toHaveClass(/active/);
	}
);
