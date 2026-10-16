/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.form.web.internal.portlet.action;

import com.liferay.dynamic.data.mapping.form.builder.context.DDMFormContextDeserializer;
import com.liferay.dynamic.data.mapping.form.builder.context.DDMFormContextDeserializerRequest;
import com.liferay.dynamic.data.mapping.form.web.internal.portlet.action.helper.SaveFormInstanceMVCCommandHelper;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormLayout;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureService;
import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;

import java.util.Collections;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Aswin Narayanadas
 */
public class SaveStructureMVCActionCommandTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		ReflectionTestUtil.setFieldValue(
			_saveStructureMVCActionCommand, "ddlFormBuilderContextToDDMForm",
			_ddlFormBuilderContextToDDMForm);
		ReflectionTestUtil.setFieldValue(
			_saveStructureMVCActionCommand,
			"ddlFormBuilderContextToDDMFormLayout",
			_ddlFormBuilderContextToDDMFormLayout);
		ReflectionTestUtil.setFieldValue(
			_saveStructureMVCActionCommand, "jsonFactory",
			new JSONFactoryImpl());
		ReflectionTestUtil.setFieldValue(
			_saveStructureMVCActionCommand, "_ddmStructureService",
			_ddmStructureService);
		ReflectionTestUtil.setFieldValue(
			_saveStructureMVCActionCommand, "_portal", _portal);
		ReflectionTestUtil.setFieldValue(
			_saveStructureMVCActionCommand, "saveFormInstanceMVCCommandHelper",
			_saveFormInstanceMVCCommandHelper);
	}

	@Test
	public void testDoProcessActionWhenRedirectAttributeIsNotSet()
		throws Exception {

		ActionRequest actionRequest = _processAction(
			RandomTestUtil.randomString());

		Assert.assertNull(actionRequest.getAttribute(WebKeys.REDIRECT));

		Mockito.verify(
			actionRequest, Mockito.never()
		).setAttribute(
			Mockito.eq(WebKeys.REDIRECT), Mockito.any()
		);
	}

	private ActionRequest _processAction(String redirect) throws Exception {
		ActionRequest actionRequest = Mockito.mock(ActionRequest.class);
		ActionResponse actionResponse = Mockito.mock(ActionResponse.class);

		Mockito.when(
			actionRequest.getParameter("description")
		).thenReturn(
			"{}"
		);

		Mockito.when(
			actionRequest.getParameter("groupId")
		).thenReturn(
			String.valueOf(RandomTestUtil.randomLong())
		);

		Mockito.when(
			actionRequest.getParameter("name")
		).thenReturn(
			RandomTestUtil.randomString()
		);

		Mockito.when(
			actionRequest.getParameter("redirect")
		).thenReturn(
			redirect
		);

		Mockito.when(
			actionRequest.getParameter("serializedFormBuilderContext")
		).thenReturn(
			"{}"
		);

		Mockito.when(
			actionRequest.getParameter("structureId")
		).thenReturn(
			"0"
		);

		Mockito.when(
			actionRequest.getParameter("structureKey")
		).thenReturn(
			RandomTestUtil.randomString()
		);

		DDMForm ddmForm = new DDMForm();

		ddmForm.setAvailableLocales(Collections.singleton(LocaleUtil.US));
		ddmForm.setDefaultLocale(LocaleUtil.US);

		Mockito.when(
			_ddlFormBuilderContextToDDMForm.deserialize(
				Mockito.any(DDMFormContextDeserializerRequest.class))
		).thenReturn(
			ddmForm
		);

		Mockito.when(
			_ddlFormBuilderContextToDDMFormLayout.deserialize(
				Mockito.any(DDMFormContextDeserializerRequest.class))
		).thenReturn(
			new DDMFormLayout()
		);

		Mockito.when(
			_saveFormInstanceMVCCommandHelper.getNameMap(
				Mockito.any(DDMForm.class), Mockito.anyString(),
				Mockito.anyString())
		).thenReturn(
			new HashMap<>()
		);

		try (MockedStatic<ServiceContextFactory> mockedStatic =
				Mockito.mockStatic(ServiceContextFactory.class)) {

			mockedStatic.when(
				() -> ServiceContextFactory.getInstance(
					DDMStructure.class.getName(), actionRequest)
			).thenReturn(
				new ServiceContext()
			);

			_saveStructureMVCActionCommand.doProcessAction(
				actionRequest, actionResponse);
		}

		return actionRequest;
	}

	@SuppressWarnings("unchecked")
	private final DDMFormContextDeserializer<DDMForm>
		_ddlFormBuilderContextToDDMForm =
			(DDMFormContextDeserializer<DDMForm>)Mockito.mock(
				DDMFormContextDeserializer.class);

	@SuppressWarnings("unchecked")
	private final DDMFormContextDeserializer<DDMFormLayout>
		_ddlFormBuilderContextToDDMFormLayout =
			(DDMFormContextDeserializer<DDMFormLayout>)Mockito.mock(
				DDMFormContextDeserializer.class);

	private final DDMStructureService _ddmStructureService = Mockito.mock(
		DDMStructureService.class);
	private final Portal _portal = Mockito.mock(Portal.class);
	private final SaveFormInstanceMVCCommandHelper
		_saveFormInstanceMVCCommandHelper = Mockito.mock(
			SaveFormInstanceMVCCommandHelper.class);
	private final SaveStructureMVCActionCommand _saveStructureMVCActionCommand =
		new SaveStructureMVCActionCommand();

}