/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.document.library.web.internal.struts.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.struts.StrutsAction;
import com.liferay.portal.kernel.test.constants.TestDataConstants;
import com.liferay.portal.kernel.test.context.ContextUserReplace;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.RoleTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Shakir Shamim
 */
@RunWith(Arquillian.class)
public class GetFileStrutsActionTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		serviceContext.setAddGroupPermissions(false);
		serviceContext.setAddGuestPermissions(false);

		_fileEntry = _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString() + ".txt", ContentTypes.TEXT_PLAIN,
			TestDataConstants.TEST_BYTE_ARRAY, null, null, null,
			serviceContext);
	}

	@Test
	public void testGetFile() throws Exception {
		_testGetFileByIdWithOnlyDownloadPermission();
		_testGetFileByIdWithOnlyViewPermission();
		_testGetFileByIdWithoutViewAndDownloadPermission();
		_testGetFileByIdWithViewAndDownloadPermission();
		_testGetFileByNameWithOnlyViewPermission();
		_testGetFileByNameWithViewAndDownloadPermission();
	}

	private User _addUser(String... actionIds) throws Exception {
		User user = UserTestUtil.addUser(_group.getGroupId());

		_users.add(user);

		Role role = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);

		_roles.add(role);

		_resourcePermissionLocalService.setResourcePermissions(
			_group.getCompanyId(), DLFileEntry.class.getName(),
			ResourceConstants.SCOPE_INDIVIDUAL,
			String.valueOf(_fileEntry.getFileEntryId()), role.getRoleId(),
			actionIds);

		_userLocalService.addRoleUser(role.getRoleId(), user.getUserId());

		return user;
	}

	private void _assertFileSent(
		MockHttpServletResponse mockHttpServletResponse) {

		Assert.assertEquals(
			HttpServletResponse.SC_OK, mockHttpServletResponse.getStatus());
		Assert.assertArrayEquals(
			TestDataConstants.TEST_BYTE_ARRAY,
			mockHttpServletResponse.getContentAsByteArray());
	}

	private void _assertUnauthorized(
		MockHttpServletResponse mockHttpServletResponse) {

		Assert.assertEquals(
			HttpServletResponse.SC_UNAUTHORIZED,
			mockHttpServletResponse.getStatus());
	}

	private Map<String, String> _getFileEntryIdParameters() {
		return HashMapBuilder.put(
			"fileEntryId", String.valueOf(_fileEntry.getFileEntryId())
		).build();
	}

	private Map<String, String> _getFileEntryNameParameters() throws Exception {
		DLFileEntry dlFileEntry = _dlFileEntryLocalService.getDLFileEntry(
			_fileEntry.getFileEntryId());

		return HashMapBuilder.put(
			"folderId", String.valueOf(_fileEntry.getFolderId())
		).put(
			"groupId", String.valueOf(_group.getGroupId())
		).put(
			"name", dlFileEntry.getName()
		).build();
	}

	private MockHttpServletResponse _getMockHttpServletResponse(
			User user, Map<String, String> parameters)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(user);

		try (ContextUserReplace contextUserReplace = new ContextUserReplace(
				user, permissionChecker)) {

			MockHttpServletRequest mockHttpServletRequest =
				new MockHttpServletRequest();

			ThemeDisplay themeDisplay = new ThemeDisplay();

			themeDisplay.setPermissionChecker(permissionChecker);
			themeDisplay.setScopeGroupId(_group.getGroupId());

			mockHttpServletRequest.setAttribute(
				WebKeys.THEME_DISPLAY, themeDisplay);

			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				mockHttpServletRequest.setParameter(
					entry.getKey(), entry.getValue());
			}

			MockHttpServletResponse mockHttpServletResponse =
				new MockHttpServletResponse();

			_getFileStrutsAction.execute(
				mockHttpServletRequest, mockHttpServletResponse);

			return mockHttpServletResponse;
		}
	}

	private void _testGetFileByIdWithOnlyDownloadPermission() throws Exception {
		_assertUnauthorized(
			_getMockHttpServletResponse(
				_addUser(ActionKeys.DOWNLOAD), _getFileEntryIdParameters()));
	}

	private void _testGetFileByIdWithOnlyViewPermission() throws Exception {
		_assertUnauthorized(
			_getMockHttpServletResponse(
				_addUser(ActionKeys.VIEW), _getFileEntryIdParameters()));
	}

	private void _testGetFileByIdWithoutViewAndDownloadPermission()
		throws Exception {

		_assertUnauthorized(
			_getMockHttpServletResponse(
				_addUser(), _getFileEntryIdParameters()));
	}

	private void _testGetFileByIdWithViewAndDownloadPermission()
		throws Exception {

		_assertFileSent(
			_getMockHttpServletResponse(
				_addUser(ActionKeys.DOWNLOAD, ActionKeys.VIEW),
				_getFileEntryIdParameters()));
	}

	private void _testGetFileByNameWithOnlyViewPermission() throws Exception {
		_assertUnauthorized(
			_getMockHttpServletResponse(
				_addUser(ActionKeys.VIEW), _getFileEntryNameParameters()));
	}

	private void _testGetFileByNameWithViewAndDownloadPermission()
		throws Exception {

		_assertFileSent(
			_getMockHttpServletResponse(
				_addUser(ActionKeys.DOWNLOAD, ActionKeys.VIEW),
				_getFileEntryNameParameters()));
	}

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@Inject
	private DLFileEntryLocalService _dlFileEntryLocalService;

	private FileEntry _fileEntry;

	@Inject(filter = "path=/document_library/get_file")
	private StrutsAction _getFileStrutsAction;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@DeleteAfterTestRun
	private final List<Role> _roles = new ArrayList<>();

	@Inject
	private UserLocalService _userLocalService;

	@DeleteAfterTestRun
	private final List<User> _users = new ArrayList<>();

}