/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.store.file.system.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.store.Store;
import com.liferay.document.library.kernel.store.StoreArea;
import com.liferay.document.library.kernel.store.StoreAreaProcessor;
import com.liferay.petra.io.unsync.UnsyncByteArrayInputStream;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.AssumeTestRule;
import com.liferay.portal.kernel.test.util.CompanyTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.time.Duration;

import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 */
@FeatureFlag("LPS-174816")
@RunWith(Arquillian.class)
public class FileSystemStoreStoreAreaProcessorTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new AssumeTestRule("assume"), new LiferayIntegrationTestRule());

	public static void assume() {
		String fileSystemStoreClassName =
			"com.liferay.portal.store.file.system.FileSystemStore";
		String dlStoreImpl = PropsUtil.get(PropsKeys.DL_STORE_IMPL);

		Assume.assumeTrue(
			StringBundler.concat(
				"Property \"", PropsKeys.DL_STORE_IMPL, "\" is not set to \"",
				fileSystemStoreClassName, "\""),
			dlStoreImpl.equals(fileSystemStoreClassName));
	}

	@Before
	public void setUp() throws Exception {
		_company = CompanyTestUtil.addCompany();
	}

	@After
	public void tearDown() throws Exception {
		_companyLocalService.deleteCompany(_company);

		StoreArea.runWithStoreAreas(
			() -> _store.deleteDirectory(_company.getCompanyId()),
			StoreArea.DELETED, StoreArea.LIVE, StoreArea.NEW);
	}

	@Test
	public void testCleanUpDeletedStoreAreaKeepsRecentFiles() throws Exception {
		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				String fileName = StringUtil.randomString();

				_store.addFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT,
					new UnsyncByteArrayInputStream(new byte[0]));

				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));

				_storeAreaProcessor.cleanUpDeletedStoreArea(
					_company.getCompanyId(), 1, name -> true, StringPool.BLANK,
					Duration.ofDays(1));

				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));
			});
	}

	@Test
	public void testCleanUpDeletedStoreAreaRemovesFilesIncrementally()
		throws Exception {

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				for (String fileName : RandomTestUtil.randomStrings(4)) {
					_store.addFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT,
						new UnsyncByteArrayInputStream(new byte[0]));
				}

				int runCount = 0;

				String startOffset = StringPool.BLANK;

				do {
					startOffset = _storeAreaProcessor.cleanUpDeletedStoreArea(
						_company.getCompanyId(), 1, name -> true, startOffset,
						Duration.ofDays(-1));

					runCount++;
				}
				while (Validator.isNotNull(startOffset));

				Assert.assertTrue(runCount > 1);

				String[] fileNames = _store.getFileNames(
					_company.getCompanyId(), _company.getGroupId(),
					StringPool.BLANK);

				Assert.assertEquals(
					Arrays.toString(fileNames), 0, fileNames.length);
			});
	}

	@Test
	public void testCleanUpDeletedStoreAreaRemovesOldFiles() throws Exception {
		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				String fileName = StringUtil.randomString();

				_store.addFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT,
					new UnsyncByteArrayInputStream(new byte[0]));

				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));

				_storeAreaProcessor.cleanUpDeletedStoreArea(
					_company.getCompanyId(), 1, name -> true, StringPool.BLANK,
					Duration.ofDays(-1));

				Assert.assertFalse(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));
			});
	}

	@Test
	public void testCleanUpNewStoreAreaKeepsRecentFiles() throws Exception {
		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> {
				String fileName = StringUtil.randomString();

				_store.addFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT,
					new UnsyncByteArrayInputStream(new byte[0]));

				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));

				_storeAreaProcessor.cleanUpNewStoreArea(
					_company.getCompanyId(), 1, name -> false, StringPool.BLANK,
					Duration.ofDays(1));

				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));
			});
	}

	@Test
	public void testCleanUpNewStoreAreaMovesFilesIncrementally()
		throws Exception {

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> {
				for (String fileName : RandomTestUtil.randomStrings(4)) {
					_store.addFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT,
						new UnsyncByteArrayInputStream(new byte[0]));
				}

				int runCount = 0;

				String startOffset = StringPool.BLANK;

				do {
					startOffset = _storeAreaProcessor.cleanUpNewStoreArea(
						_company.getCompanyId(), 1, name -> false, startOffset,
						Duration.ofDays(-1));

					runCount++;
				}
				while (Validator.isNotNull(startOffset));

				Assert.assertTrue(runCount > 1);

				String[] fileNames = _store.getFileNames(
					_company.getCompanyId(), _company.getGroupId(),
					StringPool.BLANK);

				Assert.assertEquals(
					Arrays.toString(fileNames), 0, fileNames.length);
			});

		StoreArea.withStoreArea(
			StoreArea.LIVE,
			() -> {
				String[] fileNames = _store.getFileNames(
					_company.getCompanyId(), _company.getGroupId(),
					StringPool.BLANK);

				Assert.assertEquals(
					Arrays.toString(fileNames), 4, fileNames.length);
			});
	}

	@Test
	public void testCleanUpNewStoreAreaMovesOldFiles() throws Exception {
		String fileName = StringUtil.randomString();

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> {
				_store.addFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT,
					new UnsyncByteArrayInputStream(new byte[0]));

				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));

				_storeAreaProcessor.cleanUpNewStoreArea(
					_company.getCompanyId(), 1, name -> false, StringPool.BLANK,
					Duration.ofDays(-1));

				Assert.assertFalse(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));
			});

		StoreArea.withStoreArea(
			StoreArea.LIVE,
			() -> Assert.assertTrue(
				_store.hasFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT)));
	}

	@Test
	public void testCleanUpNewStoreAreaMovesUnreferencedFilesToDeletedStoreArea()
		throws Exception {

		String fileName = StringUtil.randomString();

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> {
				_store.addFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT,
					new UnsyncByteArrayInputStream(new byte[0]));

				_storeAreaProcessor.cleanUpNewStoreArea(
					_company.getCompanyId(), 1, name -> true, StringPool.BLANK,
					Duration.ofDays(-1));

				Assert.assertFalse(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName, Store.VERSION_DEFAULT));
			});

		StoreArea.withStoreArea(
			StoreArea.LIVE,
			() -> Assert.assertFalse(
				_store.hasFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT)));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> Assert.assertTrue(
				_store.hasFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT)));
	}

	@Test
	public void testCopy() throws Exception {
		String fileName = StringUtil.randomString();

		_store.addFile(
			_company.getCompanyId(), _company.getGroupId(), fileName,
			Store.VERSION_DEFAULT, new UnsyncByteArrayInputStream(new byte[0]));

		StoreArea.withStoreArea(
			StoreArea.LIVE,
			() -> Assert.assertTrue(
				_store.hasFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT)));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> Assert.assertFalse(
				_store.hasFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT)));

		boolean copied = _storeAreaProcessor.copy(
			StoreArea.LIVE.getPath(
				_company.getCompanyId(), _company.getGroupId(), fileName,
				Store.VERSION_DEFAULT),
			StoreArea.DELETED.getPath(
				_company.getCompanyId(), _company.getGroupId(), fileName,
				Store.VERSION_DEFAULT));

		Assert.assertTrue(copied);

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> Assert.assertTrue(
				_store.hasFile(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					Store.VERSION_DEFAULT)));
	}

	@Test
	public void testCopyCompanyDirectory() throws Exception {
		String fileName1 = StringUtil.randomString();

		_store.addFile(
			_company.getCompanyId(), _company.getGroupId(), fileName1,
			Store.VERSION_DEFAULT, new UnsyncByteArrayInputStream(new byte[0]));

		long repositoryId = _company.getGroupId() * 10;
		String fileName2 = StringUtil.randomString();

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> _store.addFile(
				_company.getCompanyId(), repositoryId, fileName2,
				Store.VERSION_DEFAULT,
				new UnsyncByteArrayInputStream(new byte[0])));

		Assert.assertTrue(
			_storeAreaProcessor.copyDirectory(
				_company.getCompanyId(),
				new StoreArea[] {StoreArea.LIVE, StoreArea.NEW},
				StoreArea.DELETED));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName1, Store.VERSION_DEFAULT));
				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), repositoryId, fileName2,
						Store.VERSION_DEFAULT));
			});
	}

	@Test
	public void testCopyDirectory() throws Exception {
		String fileName1 = StringUtil.randomString();

		_store.addFile(
			_company.getCompanyId(), _company.getGroupId(), fileName1,
			Store.VERSION_DEFAULT, new UnsyncByteArrayInputStream(new byte[0]));

		long repositoryId = _company.getGroupId() * 10;
		String fileName2 = StringUtil.randomString();

		_store.addFile(
			_company.getCompanyId(), repositoryId, fileName2,
			Store.VERSION_DEFAULT, new UnsyncByteArrayInputStream(new byte[0]));

		_storeAreaProcessor.copyDirectory(
			_company.getCompanyId(), _company.getGroupId(), StringPool.BLANK,
			new StoreArea[] {StoreArea.LIVE}, StoreArea.DELETED);

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				Assert.assertTrue(
					_store.hasFile(
						_company.getCompanyId(), _company.getGroupId(),
						fileName1, Store.VERSION_DEFAULT));
				Assert.assertFalse(
					_store.hasFile(
						_company.getCompanyId(), repositoryId, fileName2,
						Store.VERSION_DEFAULT));
			});
	}

	@Test
	public void testCopyKeepsContent() throws Exception {
		String fileName = StringUtil.randomString();
		byte[] bytes = RandomTestUtil.randomBytes();

		_store.addFile(
			_company.getCompanyId(), _company.getGroupId(), fileName, "1.0~abc",
			new UnsyncByteArrayInputStream(bytes));

		Assert.assertTrue(
			_storeAreaProcessor.copy(
				StoreArea.LIVE.getPath(
					_company.getCompanyId(), _company.getGroupId(), fileName,
					"1.0~abc"),
				StoreArea.NEW.getPath(
					_company.getCompanyId(), _company.getGroupId() + 1,
					fileName, "2.0~def")));

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> Assert.assertArrayEquals(
				bytes,
				FileUtil.getBytes(
					_store.getFileAsStream(
						_company.getCompanyId(), _company.getGroupId() + 1,
						fileName, "2.0~def"))));
	}

	@Test
	public void testCopyNonexistentFile() throws Exception {
		Assert.assertFalse(
			_storeAreaProcessor.copy(
				StoreArea.LIVE.getPath(
					_company.getCompanyId(), _company.getGroupId(),
					StringUtil.randomString(), Store.VERSION_DEFAULT),
				StoreArea.LIVE.getPath(
					_company.getCompanyId(), _company.getGroupId(),
					StringUtil.randomString(), Store.VERSION_DEFAULT)));
	}

	private Company _company;

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject(
		filter = "store.type=com.liferay.portal.store.file.system.FileSystemStore",
		type = Store.class
	)
	private Store _store;

	@Inject(
		filter = "store.type=com.liferay.portal.store.file.system.FileSystemStore"
	)
	private StoreAreaProcessor _storeAreaProcessor;

}