package com.liferay.announcements.web.internal;

import com.liferay.announcements.kernel.model.AnnouncementsEntry;
import com.liferay.announcements.kernel.service.AnnouncementsEntryLocalServiceUtil;
import com.liferay.announcements.web.internal.display.context.AnnouncementsAdminViewDisplayContext;

import com.liferay.portal.kernel.dao.search.SearchContainer;
import com.liferay.portal.kernel.portlet.LiferayPortletRequest;
import com.liferay.portal.kernel.portlet.LiferayPortletResponse;
import com.liferay.portal.kernel.test.portlet.MockRenderRequest;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import com.liferay.portal.test.rule.LiferayUnitTestRule;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AnnouncementsAdminViewDisplayContextTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testGetSearchContainerOrderByAsc() {

		//  RenderRequest (USED by ParamUtil)
		MockRenderRequest renderRequest = new MockRenderRequest();
		renderRequest.setParameter("orderByType", "asc");

		// Liferay request/response (JUST dependencies)
		LiferayPortletRequest liferayPortletRequest =
			Mockito.mock(LiferayPortletRequest.class);

		LiferayPortletResponse liferayPortletResponse =
			Mockito.mock(LiferayPortletResponse.class);

		//  ThemeDisplay
		ThemeDisplay themeDisplay = Mockito.mock(ThemeDisplay.class);
		Mockito.when(themeDisplay.getCompanyId()).thenReturn(1L);

		//  HttpServletRequest
		MockHttpServletRequest httpServletRequest =
			new MockHttpServletRequest();

		httpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		try (MockedStatic<AnnouncementsEntryLocalServiceUtil> mockedStatic =
				 Mockito.mockStatic(
					 AnnouncementsEntryLocalServiceUtil.class)) {

			//  Mock entries
			List<AnnouncementsEntry> mockEntries = new ArrayList<>();

			AnnouncementsEntry e1 = Mockito.mock(AnnouncementsEntry.class);
			AnnouncementsEntry e2 = Mockito.mock(AnnouncementsEntry.class);

			Date d1 = new Date(1000);
			Date d2 = new Date(2000);

			Mockito.when(e1.getCreateDate()).thenReturn(d1);
			Mockito.when(e2.getCreateDate()).thenReturn(d2);

			// Reverse order (to verify sorting)
			mockEntries.add(e2);
			mockEntries.add(e1);

			//  Mock service calls
			mockedStatic.when(
				() -> AnnouncementsEntryLocalServiceUtil.getEntries(
					Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
					Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyInt())
			).thenReturn(mockEntries);

			mockedStatic.when(
				() -> AnnouncementsEntryLocalServiceUtil.getEntriesCount(
					Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
					Mockito.anyBoolean())
			).thenReturn(2);

			//  CORRECT constructor usage
			AnnouncementsAdminViewDisplayContext displayContext =
				Mockito.spy(
					new AnnouncementsAdminViewDisplayContext(
						httpServletRequest,
						liferayPortletRequest,
						liferayPortletResponse,
						renderRequest
					));

			// Avoid null logic issues
			Mockito.doReturn("0_0")
				.when(displayContext).getDistributionScope();

			Mockito.doReturn("announcements")
				.when(displayContext).getNavigation();

			// Call method
			SearchContainer<AnnouncementsEntry> searchContainer =
				displayContext.getSearchContainer();

			List<AnnouncementsEntry> results =
				searchContainer.getResults();

			//  Assert ascending sorting
			Assert.assertTrue(
				results.get(0).getCreateDate().before(
					results.get(1).getCreateDate()));
		}
	}
}