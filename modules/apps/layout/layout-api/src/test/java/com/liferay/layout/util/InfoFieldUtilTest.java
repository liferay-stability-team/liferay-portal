/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.layout.util;

import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalServiceUtil;
import com.liferay.layout.util.structure.LayoutStructure;
import com.liferay.layout.util.structure.LayoutStructureItem;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
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
public class InfoFieldUtilTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		_layout = Mockito.mock(Layout.class);

		Mockito.when(
			_layout.getGroupId()
		).thenReturn(
			RandomTestUtil.randomLong()
		);

		Mockito.when(
			_layout.getPlid()
		).thenReturn(
			RandomTestUtil.randomLong()
		);

		_layoutPageTemplateStructure = Mockito.mock(
			LayoutPageTemplateStructure.class);

		_layoutPageTemplateStructureLocalServiceUtilMockedStatic.when(
			() ->
				LayoutPageTemplateStructureLocalServiceUtil.
					fetchLayoutPageTemplateStructure(
						_layout.getGroupId(), _layout.getPlid())
		).thenReturn(
			_layoutPageTemplateStructure
		);
	}

	@After
	public void tearDown() {
		_layoutPageTemplateStructureLocalServiceUtilMockedStatic.close();
	}

	@Test
	public void testIndexLayoutStructureItemFlat() {
		LayoutStructure layoutStructure = LayoutStructure.of(StringPool.BLANK);

		layoutStructure.addRootLayoutStructureItem();

		long fragmentEntryLinkId1 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId2 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId3 = RandomTestUtil.randomLong();

		_addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId1, layoutStructure,
			layoutStructure.getMainItemId());
		_addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId2, layoutStructure,
			layoutStructure.getMainItemId());
		_addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId3, layoutStructure,
			layoutStructure.getMainItemId());

		Map<Long, Integer> fragmentEntryLinkIndexes = new HashMap<>();

		ReflectionTestUtil.invoke(
			InfoFieldUtil.class, "_indexLayoutStructureItem",
			new Class<?>[] {
				int[].class, Map.class, String.class, LayoutStructure.class
			},
			new int[] {0}, fragmentEntryLinkIndexes,
			layoutStructure.getMainItemId(), layoutStructure);

		Assert.assertEquals(
			Integer.valueOf(0),
			fragmentEntryLinkIndexes.get(fragmentEntryLinkId1));
		Assert.assertEquals(
			Integer.valueOf(1),
			fragmentEntryLinkIndexes.get(fragmentEntryLinkId2));
		Assert.assertEquals(
			Integer.valueOf(2),
			fragmentEntryLinkIndexes.get(fragmentEntryLinkId3));
	}

	@Test
	public void testIndexLayoutStructureItemNested() {
		LayoutStructure layoutStructure = LayoutStructure.of(StringPool.BLANK);

		layoutStructure.addRootLayoutStructureItem();

		long fragmentEntryLinkId1 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId2 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId3 = RandomTestUtil.randomLong();

		_addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId1, layoutStructure,
			layoutStructure.getMainItemId());

		LayoutStructureItem containerLayoutStructureItem =
			layoutStructure.addContainerStyledLayoutStructureItem(
				layoutStructure.getMainItemId(), 1);

		_addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId2, layoutStructure,
			containerLayoutStructureItem.getItemId());

		_addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId3, layoutStructure,
			layoutStructure.getMainItemId());

		Map<Long, Integer> fragmentEntryLinkIndexes = new HashMap<>();

		ReflectionTestUtil.invoke(
			InfoFieldUtil.class, "_indexLayoutStructureItem",
			new Class<?>[] {
				int[].class, Map.class, String.class, LayoutStructure.class
			},
			new int[] {0}, fragmentEntryLinkIndexes,
			layoutStructure.getMainItemId(), layoutStructure);

		Assert.assertEquals(
			Integer.valueOf(0),
			fragmentEntryLinkIndexes.get(fragmentEntryLinkId1));
		Assert.assertEquals(
			Integer.valueOf(1),
			fragmentEntryLinkIndexes.get(fragmentEntryLinkId2));
		Assert.assertEquals(
			Integer.valueOf(2),
			fragmentEntryLinkIndexes.get(fragmentEntryLinkId3));
	}

	@Test
	public void testIndexLayoutStructureItemSkipsContainers() {
		LayoutStructure layoutStructure = LayoutStructure.of(StringPool.BLANK);

		layoutStructure.addRootLayoutStructureItem();

		layoutStructure.addContainerStyledLayoutStructureItem(
			layoutStructure.getMainItemId(), 0);

		Map<Long, Integer> fragmentEntryLinkIndexes = new HashMap<>();

		ReflectionTestUtil.invoke(
			InfoFieldUtil.class, "_indexLayoutStructureItem",
			new Class<?>[] {
				int[].class, Map.class, String.class, LayoutStructure.class
			},
			new int[] {0}, fragmentEntryLinkIndexes,
			layoutStructure.getMainItemId(), layoutStructure);

		Assert.assertTrue(MapUtil.isEmpty(fragmentEntryLinkIndexes));
	}

	@Test
	public void testSortByLayoutStructure() {
		long fragmentEntryLinkId1 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId2 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId3 = RandomTestUtil.randomLong();

		LayoutStructure layoutStructure = _createLayoutStructure(
			fragmentEntryLinkId1, fragmentEntryLinkId2, fragmentEntryLinkId3);

		long segmentsExperienceId = RandomTestUtil.randomLong();

		Mockito.when(
			_layoutPageTemplateStructure.getData(segmentsExperienceId)
		).thenReturn(
			layoutStructure.toString()
		);

		List<FragmentEntryLink> fragmentEntryLinks = Arrays.asList(
			_createFragmentEntryLink(fragmentEntryLinkId3),
			_createFragmentEntryLink(fragmentEntryLinkId1),
			_createFragmentEntryLink(fragmentEntryLinkId2));

		List<FragmentEntryLink> sortedFragmentEntryLinks =
			ReflectionTestUtil.invoke(
				InfoFieldUtil.class, "_sortByLayoutStructure",
				new Class<?>[] {List.class, Layout.class, long.class},
				fragmentEntryLinks, _layout, segmentsExperienceId);

		Assert.assertEquals(
			Arrays.asList(
				fragmentEntryLinkId1, fragmentEntryLinkId2,
				fragmentEntryLinkId3),
			TransformUtil.transform(
				sortedFragmentEntryLinks,
				FragmentEntryLink::getFragmentEntryLinkId));
	}

	@Test
	public void testSortByLayoutStructureAppendsUnindexedFragmentsInInputOrder() {
		long fragmentEntryLinkId1 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId2 = RandomTestUtil.randomLong();

		LayoutStructure layoutStructure = _createLayoutStructure(
			fragmentEntryLinkId1, fragmentEntryLinkId2);

		long segmentsExperienceId = RandomTestUtil.randomLong();

		Mockito.when(
			_layoutPageTemplateStructure.getData(segmentsExperienceId)
		).thenReturn(
			layoutStructure.toString()
		);

		long unindexedFragmentEntryLinkId1 = RandomTestUtil.randomLong();
		long unindexedFragmentEntryLinkId2 = RandomTestUtil.randomLong();

		List<FragmentEntryLink> fragmentEntryLinks = Arrays.asList(
			_createFragmentEntryLink(unindexedFragmentEntryLinkId1),
			_createFragmentEntryLink(fragmentEntryLinkId2),
			_createFragmentEntryLink(unindexedFragmentEntryLinkId2),
			_createFragmentEntryLink(fragmentEntryLinkId1));

		List<FragmentEntryLink> sortedFragmentEntryLinks =
			ReflectionTestUtil.invoke(
				InfoFieldUtil.class, "_sortByLayoutStructure",
				new Class<?>[] {List.class, Layout.class, long.class},
				fragmentEntryLinks, _layout, segmentsExperienceId);

		Assert.assertEquals(
			Arrays.asList(
				fragmentEntryLinkId1, fragmentEntryLinkId2,
				unindexedFragmentEntryLinkId1, unindexedFragmentEntryLinkId2),
			TransformUtil.transform(
				sortedFragmentEntryLinks,
				FragmentEntryLink::getFragmentEntryLinkId));
	}

	@Test
	public void testSortByLayoutStructureFallsBackToDefaultExperienceData() {
		long fragmentEntryLinkId1 = RandomTestUtil.randomLong();
		long fragmentEntryLinkId2 = RandomTestUtil.randomLong();

		LayoutStructure layoutStructure = _createLayoutStructure(
			fragmentEntryLinkId1, fragmentEntryLinkId2);

		Mockito.when(
			_layoutPageTemplateStructure.getDefaultSegmentsExperienceData()
		).thenReturn(
			layoutStructure.toString()
		);

		List<FragmentEntryLink> fragmentEntryLinks = Arrays.asList(
			_createFragmentEntryLink(fragmentEntryLinkId2),
			_createFragmentEntryLink(fragmentEntryLinkId1));

		List<FragmentEntryLink> sortedFragmentEntryLinks =
			ReflectionTestUtil.invoke(
				InfoFieldUtil.class, "_sortByLayoutStructure",
				new Class<?>[] {List.class, Layout.class, long.class},
				fragmentEntryLinks, _layout, RandomTestUtil.randomLong());

		Assert.assertEquals(
			Arrays.asList(fragmentEntryLinkId1, fragmentEntryLinkId2),
			TransformUtil.transform(
				sortedFragmentEntryLinks,
				FragmentEntryLink::getFragmentEntryLinkId));
	}

	@Test
	public void testSortByLayoutStructureWhenDataIsNull() {
		List<FragmentEntryLink> fragmentEntryLinks = Arrays.asList(
			_createFragmentEntryLink(RandomTestUtil.randomLong()),
			_createFragmentEntryLink(RandomTestUtil.randomLong()));

		List<FragmentEntryLink> sortedFragmentEntryLinks =
			ReflectionTestUtil.invoke(
				InfoFieldUtil.class, "_sortByLayoutStructure",
				new Class<?>[] {List.class, Layout.class, long.class},
				fragmentEntryLinks, _layout, RandomTestUtil.randomLong());

		Assert.assertSame(fragmentEntryLinks, sortedFragmentEntryLinks);
	}

	@Test
	public void testSortByLayoutStructureWhenInputSizeIsLessThanTwo() {
		List<FragmentEntryLink> fragmentEntryLinks = Collections.singletonList(
			_createFragmentEntryLink(RandomTestUtil.randomLong()));

		List<FragmentEntryLink> sortedFragmentEntryLinks =
			ReflectionTestUtil.invoke(
				InfoFieldUtil.class, "_sortByLayoutStructure",
				new Class<?>[] {List.class, Layout.class, long.class},
				fragmentEntryLinks, _layout, RandomTestUtil.randomLong());

		Assert.assertSame(fragmentEntryLinks, sortedFragmentEntryLinks);
	}

	@Test
	public void testSortByLayoutStructureWhenLayoutPageTemplateStructureIsNull() {
		_layoutPageTemplateStructureLocalServiceUtilMockedStatic.when(
			() ->
				LayoutPageTemplateStructureLocalServiceUtil.
					fetchLayoutPageTemplateStructure(
						_layout.getGroupId(), _layout.getPlid())
		).thenReturn(
			null
		);

		List<FragmentEntryLink> fragmentEntryLinks = Arrays.asList(
			_createFragmentEntryLink(RandomTestUtil.randomLong()),
			_createFragmentEntryLink(RandomTestUtil.randomLong()));

		List<FragmentEntryLink> sortedFragmentEntryLinks =
			ReflectionTestUtil.invoke(
				InfoFieldUtil.class, "_sortByLayoutStructure",
				new Class<?>[] {List.class, Layout.class, long.class},
				fragmentEntryLinks, _layout, RandomTestUtil.randomLong());

		Assert.assertSame(fragmentEntryLinks, sortedFragmentEntryLinks);
	}

	private void _addFragmentStyledLayoutStructureItem(
		long fragmentEntryLinkId, LayoutStructure layoutStructure,
		String parentItemId) {

		layoutStructure.addFragmentStyledLayoutStructureItem(
			fragmentEntryLinkId, parentItemId, -1);
	}

	private FragmentEntryLink _createFragmentEntryLink(
		long fragmentEntryLinkId) {

		FragmentEntryLink fragmentEntryLink = Mockito.mock(
			FragmentEntryLink.class);

		Mockito.when(
			fragmentEntryLink.getFragmentEntryLinkId()
		).thenReturn(
			fragmentEntryLinkId
		);

		return fragmentEntryLink;
	}

	private LayoutStructure _createLayoutStructure(
		long... fragmentEntryLinkIds) {

		LayoutStructure layoutStructure = LayoutStructure.of(StringPool.BLANK);

		layoutStructure.addRootLayoutStructureItem();

		for (long fragmentEntryLinkId : fragmentEntryLinkIds) {
			_addFragmentStyledLayoutStructureItem(
				fragmentEntryLinkId, layoutStructure,
				layoutStructure.getMainItemId());
		}

		return layoutStructure;
	}

	private Layout _layout;
	private LayoutPageTemplateStructure _layoutPageTemplateStructure;
	private final MockedStatic<LayoutPageTemplateStructureLocalServiceUtil>
		_layoutPageTemplateStructureLocalServiceUtilMockedStatic =
			Mockito.mockStatic(
				LayoutPageTemplateStructureLocalServiceUtil.class);

}