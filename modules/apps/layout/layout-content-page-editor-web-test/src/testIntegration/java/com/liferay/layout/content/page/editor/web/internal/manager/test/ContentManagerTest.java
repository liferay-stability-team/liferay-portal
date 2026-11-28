/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.layout.content.page.editor.web.internal.manager.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.list.constants.AssetListEntryTypeConstants;
import com.liferay.asset.list.model.AssetListEntry;
import com.liferay.asset.list.service.AssetListEntryLocalService;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.fragment.entry.processor.constants.FragmentEntryProcessorConstants;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.info.list.provider.item.selector.criterion.InfoListProviderItemSelectorReturnType;
import com.liferay.layout.display.page.LayoutDisplayPageObjectProvider;
import com.liferay.layout.manager.ContentManager;
import com.liferay.layout.model.LayoutClassedModelUsage;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructureRel;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureRelLocalService;
import com.liferay.layout.provider.LayoutStructureProvider;
import com.liferay.layout.service.LayoutClassedModelUsageLocalService;
import com.liferay.layout.test.util.ContentLayoutTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.layout.util.structure.LayoutStructure;
import com.liferay.layout.util.structure.LayoutStructureItem;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.TestInfo;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GroupThreadLocal;
import com.liferay.portal.kernel.util.JavaConstants;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.segments.service.SegmentsExperienceLocalService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Eudaldo Alonso
 */
@RunWith(Arquillian.class)
public class ContentManagerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_layout = LayoutTestUtil.addTypeContentLayout(_group);
	}

	@Test
	@TestInfo("LPD-117785")
	public void testGetFragmentEntryLinkMappedLayoutDisplayPageObjectProviders()
		throws Exception {

		FileEntry fileEntry = _addFileEntry(RandomTestUtil.randomString());

		FragmentEntryLink fragmentEntryLink =
			_fragmentEntryLinkLocalService.createFragmentEntryLink(0);

		fragmentEntryLink.setGroupId(_group.getGroupId());
		fragmentEntryLink.setEditableValues(
			JSONUtil.put(
				FragmentEntryProcessorConstants.
					KEY_EDITABLE_FRAGMENT_ENTRY_PROCESSOR,
				JSONUtil.put(
					RandomTestUtil.randomString(),
					JSONUtil.put(
						LocaleUtil.toLanguageId(LocaleUtil.US),
						JSONUtil.put(
							"classNameId",
							_portal.getClassNameId(FileEntry.class.getName())
						).put(
							"externalReferenceCode",
							fileEntry.getExternalReferenceCode()
						)))
			).toString());

		ServiceContextThreadLocal.pushServiceContext(new ServiceContext());

		try (SafeCloseable safeCloseable =
				GroupThreadLocal.setGroupIdWithSafeCloseable(0)) {

			Set<LayoutDisplayPageObjectProvider<?>>
				layoutDisplayPageObjectProviders =
					_contentManager.
						getFragmentEntryLinkMappedLayoutDisplayPageObjectProviders(
							fragmentEntryLink);

			Assert.assertEquals(
				List.of(fileEntry.getFileEntryId()),
				TransformUtil.transform(
					layoutDisplayPageObjectProviders,
					LayoutDisplayPageObjectProvider::getClassPK));
		}
		finally {
			ServiceContextThreadLocal.popServiceContext();
		}
	}

	@Test
	@TestInfo("LPS-103454")
	public void testGetPageContentsJSONArrayWithMultipleUsages()
		throws Exception {

		FileEntry fileEntry = _addFileEntry(null);

		_layoutClassedModelUsageLocalService.addLayoutClassedModelUsage(
			_group.getGroupId(), StringPool.BLANK,
			_portal.getClassNameId(FileEntry.class.getName()),
			fileEntry.getFileEntryId(), RandomTestUtil.randomString(),
			RandomTestUtil.randomLong(), _layout.getPlid(),
			new ServiceContext());
		_layoutClassedModelUsageLocalService.addLayoutClassedModelUsage(
			_group.getGroupId(), StringPool.BLANK,
			_portal.getClassNameId(FileEntry.class.getName()),
			fileEntry.getFileEntryId(), RandomTestUtil.randomString(),
			RandomTestUtil.randomLong(), _layout.getPlid(),
			new ServiceContext());

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setAttribute(
			JavaConstants.JAKARTA_PORTLET_RESPONSE,
			new MockLiferayResourceResponse());
		mockHttpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY,
			ContentLayoutTestUtil.getThemeDisplay(
				_companyLocalService.fetchCompany(
					TestPropsValues.getCompanyId()),
				_group, _layout));

		JSONArray jsonArray = ReflectionTestUtil.invoke(
			_contentManager, "getPageContentsJSONArray",
			new Class<?>[] {
				HttpServletRequest.class, HttpServletResponse.class, long.class,
				long.class
			},
			mockHttpServletRequest, new MockHttpServletResponse(),
			_layout.getPlid(),
			_segmentsExperienceLocalService.fetchDefaultSegmentsExperienceId(
				_layout.getPlid()));

		Assert.assertEquals(1, jsonArray.length());
	}

	@Test
	@TestInfo("LPD-72807")
	public void testGetPageContentsJSONArrayWithoutPermissions()
		throws Exception {

		User user = UserTestUtil.addGroupUser(
			_group, RoleConstants.SITE_MEMBER);

		AssetListEntry assetListEntry =
			_assetListEntryLocalService.addAssetListEntry(
				RandomTestUtil.randomString(), TestPropsValues.getUserId(),
				_group.getGroupId(), RandomTestUtil.randomString(),
				AssetListEntryTypeConstants.TYPE_DYNAMIC,
				ServiceContextTestUtil.getServiceContext(
					_group, TestPropsValues.getUserId()));

		Layout draftLayout = _layout.fetchDraftLayout();

		ContentLayoutTestUtil.addCollectionDisplayToLayout(
			JSONUtil.put(
				"classPK", assetListEntry.getAssetListEntryId()
			).put(
				"type", InfoListProviderItemSelectorReturnType.class.getName()
			),
			draftLayout, _layoutStructureProvider, null, null, 0,
			_segmentsExperienceLocalService.fetchDefaultSegmentsExperienceId(
				draftLayout.getPlid()));

		ContentLayoutTestUtil.publishLayout(draftLayout, _layout);

		ThemeDisplay themeDisplay = ContentLayoutTestUtil.getThemeDisplay(
			_companyLocalService.fetchCompany(TestPropsValues.getCompanyId()),
			_group, _layout);

		themeDisplay.setPermissionChecker(
			PermissionCheckerFactoryUtil.getPermissionCheckerFactory(
			).create(
				user
			));
		themeDisplay.setUser(user);

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setAttribute(
			JavaConstants.JAKARTA_PORTLET_RESPONSE,
			new MockLiferayResourceResponse());
		mockHttpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		JSONArray jsonArray = ReflectionTestUtil.invoke(
			_contentManager, "getPageContentsJSONArray",
			new Class<?>[] {
				HttpServletRequest.class, HttpServletResponse.class, long.class,
				long.class
			},
			mockHttpServletRequest, new MockHttpServletResponse(),
			_layout.getPlid(),
			_segmentsExperienceLocalService.fetchDefaultSegmentsExperienceId(
				_layout.getPlid()));

		Assert.assertEquals(1, jsonArray.length());

		JSONObject jsonObject = jsonArray.getJSONObject(0);

		JSONObject actionsJSONObject = jsonObject.getJSONObject("actions");

		Assert.assertFalse(actionsJSONObject.has("editURL"));
	}

	@Test
	@TestInfo("LPD-117785")
	public void testUpdateLayoutClassedModelUsage() throws Exception {
		FileEntry fileEntry = _addFileEntry(RandomTestUtil.randomString());

		LayoutPageTemplateStructure layoutPageTemplateStructure =
			_layoutPageTemplateStructureLocalService.
				fetchLayoutPageTemplateStructure(
					_group.getGroupId(), _layout.getPlid());

		LayoutPageTemplateStructureRel layoutPageTemplateStructureRel =
			_layoutPageTemplateStructureRelLocalService.
				fetchLayoutPageTemplateStructureRel(
					layoutPageTemplateStructure.
						getLayoutPageTemplateStructureId(),
					_segmentsExperienceLocalService.
						fetchDefaultSegmentsExperienceId(_layout.getPlid()));

		LayoutStructure layoutStructure = LayoutStructure.of(
			layoutPageTemplateStructureRel.getData());

		LayoutStructureItem layoutStructureItem =
			layoutStructure.addContainerStyledLayoutStructureItem(
				layoutStructure.getMainItemId(), 0);

		layoutStructureItem.updateItemConfig(
			JSONUtil.put(
				"styles",
				JSONUtil.put(
					"backgroundImage",
					JSONUtil.put(
						"classNameId",
						_portal.getClassNameId(FileEntry.class.getName())
					).put(
						"externalReferenceCode",
						fileEntry.getExternalReferenceCode()
					))));

		layoutPageTemplateStructureRel.setData(layoutStructure.toString());

		ServiceContextThreadLocal.pushServiceContext(new ServiceContext());

		try (SafeCloseable safeCloseable =
				GroupThreadLocal.setGroupIdWithSafeCloseable(0)) {

			_contentManager.updateLayoutClassedModelUsage(
				layoutPageTemplateStructureRel);
		}
		finally {
			ServiceContextThreadLocal.popServiceContext();
		}

		List<LayoutClassedModelUsage> layoutClassedModelUsages =
			_layoutClassedModelUsageLocalService.getLayoutClassedModelUsages(
				_portal.getClassNameId(FileEntry.class.getName()),
				fileEntry.getFileEntryId());

		Assert.assertEquals(
			layoutClassedModelUsages.toString(), 1,
			layoutClassedModelUsages.size());
	}

	private FileEntry _addFileEntry(String externalReferenceCode)
		throws Exception {

		return _dlAppLocalService.addFileEntry(
			externalReferenceCode, TestPropsValues.getUserId(),
			_group.getGroupId(), DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString(), ContentTypes.TEXT_PLAIN,
			RandomTestUtil.randomString(), StringPool.BLANK, StringPool.BLANK,
			StringPool.BLANK, "liferay".getBytes(), null, null, null,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	@Inject
	private AssetListEntryLocalService _assetListEntryLocalService;

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject
	private ContentManager _contentManager;

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@Inject
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@DeleteAfterTestRun
	private Group _group;

	private Layout _layout;

	@Inject
	private LayoutClassedModelUsageLocalService
		_layoutClassedModelUsageLocalService;

	@Inject
	private LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService;

	@Inject
	private LayoutPageTemplateStructureRelLocalService
		_layoutPageTemplateStructureRelLocalService;

	@Inject
	private LayoutStructureProvider _layoutStructureProvider;

	@Inject
	private Portal _portal;

	@Inject
	private SegmentsExperienceLocalService _segmentsExperienceLocalService;

}