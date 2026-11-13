/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import '@testing-library/jest-dom';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {openModal} from 'frontend-js-components-web';
import {fetch as liferayFetch} from 'frontend-js-web';
import React from 'react';

import PreviewButton from '../../../src/main/resources/META-INF/resources/js/article/PreviewButton';

jest.mock('frontend-js-components-web', () => ({
	openModal: jest.fn(),
	openToast: jest.fn(),
}));

const NAMESPACE = 'namespace';

const renderComponent = ({articleId = '', classNameId = '0'} = {}) => {
	const form = document.createElement('form');

	form.id = `${NAMESPACE}fm1`;
	form.method = 'post';

	[
		['articleId', articleId],
		['classNameId', classNameId],
		['jakarta-portlet-action', ''],
	].forEach(([name, value]) => {
		const input = document.createElement('input');

		input.id = `${NAMESPACE}${name}`;
		input.name = `${NAMESPACE}${name}`;
		input.value = value;

		form.appendChild(input);
	});

	document.body.appendChild(form);

	return render(
		<PreviewButton
			disabled={false}
			getPreviewURL={() => 'http://localhost/preview'}
			namespace={NAMESPACE}
			newArticle
			saveAsDraftURL="http://localhost/save-as-draft"
		/>
	);
};

describe('PreviewButton', () => {
	beforeEach(() => {
		jest.clearAllMocks();

		Liferay.componentReady = jest.fn(() => new Promise(() => {}));
		Liferay.Util.fetch = liferayFetch;
	});

	afterEach(() => {
		document.getElementById(`${NAMESPACE}fm1`)?.remove();

		delete Liferay.componentReady;
		delete Liferay.Util.fetch;
	});

	it('shares the new article ID and Friendly URLs when Preview creates the draft', async () => {
		renderComponent();

		fetch.mockResponseOnce(
			JSON.stringify({
				articleId: '12345',
				friendlyUrlMap: {en_US: 'preview-title'},
				version: '1.0',
			})
		);

		await userEvent.click(screen.getByRole('button'));

		await waitFor(() => expect(openModal).toHaveBeenCalled());

		expect(Liferay.fire).toHaveBeenCalledWith('asyncFormSubmission', {
			articleId: '12345',
			friendlyUrlMap: {en_US: 'preview-title'},
		});
	});

	it('enables Preview for a new article', () => {
		renderComponent();

		expect(screen.getByRole('button')).toBeEnabled();
	});

	it('disables Preview for new structure default values', () => {
		renderComponent({classNameId: '20100'});

		expect(screen.getByRole('button')).toBeDisabled();
	});

	it('enables Preview for saved structure default values', () => {
		renderComponent({articleId: '12345', classNameId: '20100'});

		expect(screen.getByRole('button')).toBeEnabled();
	});
});
