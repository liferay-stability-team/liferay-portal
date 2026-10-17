/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.document.library.store.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileVersion;
import com.liferay.document.library.kernel.service.DLAppLocalServiceUtil;
import com.liferay.document.library.kernel.store.DLStoreRequest;
import com.liferay.document.library.kernel.store.DLStoreUtil;
import com.liferay.document.library.kernel.store.Store;
import com.liferay.document.library.kernel.store.StoreArea;
import com.liferay.document.library.test.util.DLAppTestUtil;
import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.petra.io.unsync.UnsyncByteArrayInputStream;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Image;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.scheduler.SchedulerJobConfiguration;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ImageLocalServiceUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.AssumeTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Adolfo Pérez
 */
@FeatureFlag("LPS-174816")
@RunWith(Arquillian.class)
public class DLStoreStoreAreaTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new AssumeTestRule("assume"), new LiferayIntegrationTestRule());

	public static void assume() {
		String dlStoreImpl = PropsUtil.get(PropsKeys.DL_STORE_IMPL);

		Assume.assumeTrue(
			StringBundler.concat(
				"Property \"", PropsKeys.DL_STORE_IMPL,
				"\" is not set to a store that supports store areas"),
			ArrayUtil.contains(_STORE_CLASS_NAMES, dlStoreImpl));
	}

	@Test
	public void testAddFile() throws Exception {
		String fileName = StringUtil.randomString();

		_addFile(fileName, Store.VERSION_DEFAULT);

		Assert.assertTrue(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, Store.VERSION_DEFAULT));

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> _assertHasStoreFile(fileName, Store.VERSION_DEFAULT));
	}

	@Test
	public void testCleanUpStoreAreas() throws Exception {
		FileEntry deletedFileEntry = DLAppTestUtil.addFileEntry(
			TestPropsValues.getGroupId());

		byte[] deletedFileEntryBytes = FileUtil.getBytes(
			deletedFileEntry.getContentStream());

		DLFileEntry deletedDLFileEntry =
			(DLFileEntry)deletedFileEntry.getModel();

		DLStoreUtil.deleteFile(
			deletedDLFileEntry.getCompanyId(),
			deletedDLFileEntry.getDataRepositoryId(),
			deletedDLFileEntry.getName());

		long deletedImageId = CounterLocalServiceUtil.increment();
		byte[] bytes = RandomTestUtil.randomBytes();

		Image deletedImage = ImageLocalServiceUtil.updateImage(
			TestPropsValues.getCompanyId(), deletedImageId, bytes, "png", 1, 1,
			bytes.length);

		ImageLocalServiceUtil.deleteImage(deletedImageId);
		ImageLocalServiceUtil.addImage(deletedImage);

		FileEntry newFileEntry = DLAppTestUtil.addFileEntry(
			TestPropsValues.getGroupId());

		byte[] newFileEntryBytes = FileUtil.getBytes(
			newFileEntry.getContentStream());

		String unreferencedDeletedFileName = StringUtil.randomString();
		String unreferencedDeletedImageFileName =
			CounterLocalServiceUtil.increment() + ".png";
		String unreferencedNewFileName = StringUtil.randomString();
		String unreferencedVersionLabel = "1.0~" + PortalUUIDUtil.generate();

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				_store.addFile(
					TestPropsValues.getCompanyId(),
					TestPropsValues.getGroupId(), unreferencedDeletedFileName,
					unreferencedVersionLabel,
					new UnsyncByteArrayInputStream(new byte[0]));
				_store.addFile(
					TestPropsValues.getCompanyId(), 0,
					unreferencedDeletedImageFileName, Store.VERSION_DEFAULT,
					new UnsyncByteArrayInputStream(new byte[0]));
			});

		StoreArea.withStoreArea(
			StoreArea.NEW,
			() -> _store.addFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				unreferencedNewFileName, unreferencedVersionLabel,
				new UnsyncByteArrayInputStream(new byte[0])));

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					"com.liferay.document.library.internal.configuration." +
						"StoreAreaConfiguration",
					HashMapDictionaryBuilder.<String, Object>put(
						"evictionAge", -1
					).put(
						"evictionQuota", 100000
					).build())) {

			SchedulerJobConfiguration schedulerJobConfiguration =
				_schedulerJobConfigurationSnapshot.get();

			UnsafeConsumer<Long, Exception> unsafeConsumer =
				schedulerJobConfiguration.getCompanyJobExecutorUnsafeConsumer();

			unsafeConsumer.accept(TestPropsValues.getCompanyId());
		}

		Assert.assertArrayEquals(
			deletedFileEntryBytes,
			FileUtil.getBytes(deletedFileEntry.getContentStream()));
		Assert.assertArrayEquals(
			bytes,
			FileUtil.getBytes(
				ImageLocalServiceUtil.getImageInputStream(
					TestPropsValues.getCompanyId(), deletedImageId, "png")));

		FileEntry movedFileEntry = DLAppLocalServiceUtil.getFileEntry(
			newFileEntry.getFileEntryId());

		Assert.assertArrayEquals(
			newFileEntryBytes,
			FileUtil.getBytes(movedFileEntry.getContentStream()));

		DLFileEntry newDLFileEntry = (DLFileEntry)newFileEntry.getModel();

		FileVersion newFileVersion = newFileEntry.getFileVersion();

		DLFileVersion newDLFileVersion =
			(DLFileVersion)newFileVersion.getModel();

		Assert.assertTrue(
			StoreArea.withStoreArea(
				StoreArea.LIVE,
				() -> _store.hasFile(
					newDLFileEntry.getCompanyId(),
					newDLFileEntry.getDataRepositoryId(),
					newDLFileEntry.getName(),
					newDLFileVersion.getStoreFileName())));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				Assert.assertFalse(
					_store.hasFile(
						TestPropsValues.getCompanyId(),
						TestPropsValues.getGroupId(),
						unreferencedDeletedFileName, unreferencedVersionLabel));
				Assert.assertFalse(
					_store.hasFile(
						TestPropsValues.getCompanyId(), 0,
						unreferencedDeletedImageFileName,
						Store.VERSION_DEFAULT));
				Assert.assertTrue(
					_store.hasFile(
						TestPropsValues.getCompanyId(),
						TestPropsValues.getGroupId(), unreferencedNewFileName,
						unreferencedVersionLabel));
			});

		ImageLocalServiceUtil.deleteImage(deletedImageId);
	}

	@Test
	public void testDeleteDirectory() throws Exception {
		String fileName = StringUtil.randomString();

		_addFile(fileName, Store.VERSION_DEFAULT);

		DLStoreUtil.deleteDirectory(
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			StringPool.BLANK);

		Assert.assertFalse(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, Store.VERSION_DEFAULT));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> _assertHasStoreFile(fileName, Store.VERSION_DEFAULT));
	}

	@Test
	public void testDeleteFile() throws Exception {
		String fileName = StringUtil.randomString();

		_addFile(fileName, Store.VERSION_DEFAULT);

		DLStoreUtil.deleteFile(
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			fileName, Store.VERSION_DEFAULT);

		Assert.assertFalse(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, Store.VERSION_DEFAULT));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> _assertHasStoreFile(fileName, Store.VERSION_DEFAULT));
	}

	@Test
	public void testDeleteFileWithMultipleVersions() throws Exception {
		String fileName = StringUtil.randomString();

		_addFile(fileName, Store.VERSION_DEFAULT);
		_addFile(fileName, "2.0");

		DLStoreUtil.deleteFile(
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			fileName);

		Assert.assertFalse(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, Store.VERSION_DEFAULT));
		Assert.assertFalse(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, "2.0"));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> {
				_assertHasStoreFile(fileName, Store.VERSION_DEFAULT);
				_assertHasStoreFile(fileName, "2.0");
			});
	}

	@Test
	public void testGetFileEntryContentStreamWhenStoreFileIsDeleted()
		throws Exception {

		FileEntry fileEntry = DLAppTestUtil.addFileEntry(
			TestPropsValues.getGroupId());

		byte[] bytes = FileUtil.getBytes(fileEntry.getContentStream());

		DLFileEntry dlFileEntry = (DLFileEntry)fileEntry.getModel();

		FileVersion fileVersion = fileEntry.getFileVersion();

		DLFileVersion dlFileVersion = (DLFileVersion)fileVersion.getModel();

		DLStoreUtil.deleteFile(
			dlFileEntry.getCompanyId(), dlFileEntry.getDataRepositoryId(),
			dlFileEntry.getName(), dlFileVersion.getStoreFileName());

		Assert.assertArrayEquals(
			bytes, FileUtil.getBytes(fileEntry.getContentStream()));
	}

	@Test
	public void testGetImageInputStreamWhenImageIsRestored() throws Exception {
		long imageId = CounterLocalServiceUtil.increment();
		byte[] bytes = RandomTestUtil.randomBytes();

		Image image = ImageLocalServiceUtil.updateImage(
			TestPropsValues.getCompanyId(), imageId, bytes, "png", 1, 1,
			bytes.length);

		ImageLocalServiceUtil.deleteImage(imageId);
		ImageLocalServiceUtil.addImage(image);

		Assert.assertArrayEquals(
			bytes,
			FileUtil.getBytes(
				ImageLocalServiceUtil.getImageInputStream(
					TestPropsValues.getCompanyId(), imageId, "png")));

		ImageLocalServiceUtil.deleteImage(imageId);
	}

	@Test
	public void testUpdateFile() throws Exception {
		String fileName = StringUtil.randomString();

		_addFile(fileName, Store.VERSION_DEFAULT);

		Company company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());

		long newRepositoryId = company.getGroupId();

		DLStoreUtil.updateFile(
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			newRepositoryId, fileName);

		Assert.assertFalse(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, Store.VERSION_DEFAULT));
		Assert.assertTrue(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), newRepositoryId, fileName,
				Store.VERSION_DEFAULT));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> _assertHasStoreFile(fileName, Store.VERSION_DEFAULT));
	}

	@Test
	public void testUpdateFileVersion() throws Exception {
		String fileName = StringUtil.randomString();

		_addFile(fileName, Store.VERSION_DEFAULT);

		DLStoreUtil.updateFileVersion(
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			fileName, Store.VERSION_DEFAULT, "2.0");

		Assert.assertFalse(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, Store.VERSION_DEFAULT));
		Assert.assertTrue(
			DLStoreUtil.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, "2.0"));

		StoreArea.withStoreArea(
			StoreArea.DELETED,
			() -> _assertHasStoreFile(fileName, Store.VERSION_DEFAULT));
		StoreArea.withStoreArea(
			StoreArea.NEW, () -> _assertHasStoreFile(fileName, "2.0"));
	}

	private void _addFile(String fileName, String version) throws Exception {
		DLStoreUtil.addFile(
			DLStoreRequest.builder(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName
			).versionLabel(
				version
			).build(),
			new byte[0]);
	}

	private void _assertHasStoreFile(String fileName, String version)
		throws Exception {

		Assert.assertTrue(
			_store.hasFile(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				fileName, version));
	}

	private static final String[] _STORE_CLASS_NAMES = {
		"com.liferay.portal.store.file.system.AdvancedFileSystemStore",
		"com.liferay.portal.store.file.system.FileSystemStore",
		"com.liferay.portal.store.gcs.GCSStore"
	};

	private static final Snapshot<SchedulerJobConfiguration>
		_schedulerJobConfigurationSnapshot = new Snapshot<>(
			DLStoreStoreAreaTest.class, SchedulerJobConfiguration.class,
			"(component.name=com.liferay.document.library.internal." +
				"CleanUpStoreAreasSchedulerJobConfiguration)",
			true);

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject(filter = "default=true", type = Store.class)
	private Store _store;

}