/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {openConfirmModal} from 'frontend-js-components-web';
import {postForm} from 'frontend-js-web';

import propsTransformer from '../../src/main/resources/META-INF/resources/js/DDMFormViewFormInstanceRecordsManagementToolbarPropsTransformer';

jest.mock('frontend-js-components-web');

jest.mock('frontend-js-web', () => ({
	...jest.requireActual('frontend-js-web'),
	postForm: jest.fn(),
}));

function deleteRecords({
	bulkSelection = false,
	isConfirmed = true,
	searchValue,
} = {}) {
	Liferay.SearchContainer = {
		get: () => ({
			select: {
				get: () => bulkSelection,
			},
		}),
	};

	propsTransformer({
		additionalProps: {
			deleteFormInstanceRecordURL: 'deleteFormInstanceRecordURL',
		},
		portletNamespace: 'ns',
		searchValue,
	}).onActionButtonClick(null, {item: {data: {action: 'deleteRecords'}}});

	openConfirmModal.mock.calls[0][0].onConfirm(isConfirmed);
}

describe('DDMFormViewFormInstanceRecordsManagementToolbarPropsTransformer', () => {
	beforeEach(() => {
		document.body.innerHTML = `
			<form id="nssearchContainerForm">
				<input checked name="nsrowIds" type="checkbox" value="1" />
				<input checked name="nsrowIds" type="checkbox" value="2" />
				<input name="nsrowIds" type="checkbox" value="3" />
			</form>
		`;
	});

	afterEach(() => {
		delete Liferay.SearchContainer;

		jest.clearAllMocks();
	});

	it('deletes the checked entries', () => {
		deleteRecords();

		expect(postForm).toHaveBeenCalledWith(
			document.getElementById('nssearchContainerForm'),
			{
				data: {
					deleteFormInstanceRecordIds: '1,2',
					selectAll: false,
				},
				url: 'deleteFormInstanceRecordURL',
			}
		);
	});

	it('deletes all entries when all entries are selected', () => {
		deleteRecords({bulkSelection: true});

		expect(postForm.mock.calls[0][1].data.selectAll).toBe(true);
	});

	it('deletes only the checked entries when a search is active', () => {
		deleteRecords({bulkSelection: true, searchValue: 'keyword'});

		expect(postForm.mock.calls[0][1].data).toEqual({
			deleteFormInstanceRecordIds: '1,2',
			selectAll: false,
		});
	});

	it('does not delete entries when the deletion is not confirmed', () => {
		deleteRecords({isConfirmed: false});

		expect(postForm).not.toHaveBeenCalled();
	});
});
