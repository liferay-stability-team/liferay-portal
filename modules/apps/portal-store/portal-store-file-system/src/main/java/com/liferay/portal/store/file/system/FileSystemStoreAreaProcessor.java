/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.store.file.system;

import com.liferay.document.library.kernel.store.Store;
import com.liferay.document.library.kernel.store.StoreArea;
import com.liferay.document.library.kernel.store.StoreAreaProcessor;
import com.liferay.petra.function.UnsafeFunction;
import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.feature.flag.FeatureFlagManagerUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import java.time.Instant;
import java.time.temporal.TemporalAmount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Mariano Álvaro Sáiz
 */
public class FileSystemStoreAreaProcessor implements StoreAreaProcessor {

	public FileSystemStoreAreaProcessor(File rootDir, Store store) {
		_store = store;

		_rootPath = rootDir.toPath();
	}

	@Override
	public String cleanUpDeletedStoreArea(
		long companyId, int deletionQuota, Predicate<String> predicate,
		String startOffset, TemporalAmount temporalAmount) {

		return _processStoreArea(
			companyId, deletionQuota,
			path -> {
				if (!predicate.test(_getName(path))) {
					return false;
				}

				Files.delete(path);

				return true;
			},
			startOffset, StoreArea.DELETED, temporalAmount);
	}

	@Override
	public String cleanUpNewStoreArea(
		long companyId, int evictionQuota, Predicate<String> predicate,
		String startOffset, TemporalAmount temporalAmount) {

		return _processStoreArea(
			companyId, evictionQuota,
			path -> {
				String name = _getName(path);

				if (predicate.test(name)) {
					Path deletedPath = _move(
						path, StoreArea.NEW.relocate(name, StoreArea.DELETED));

					Files.setLastModifiedTime(
						deletedPath, FileTime.from(Instant.now()));
				}
				else {
					_move(path, StoreArea.NEW.relocate(name, StoreArea.LIVE));
				}

				return true;
			},
			startOffset, StoreArea.NEW, temporalAmount);
	}

	@Override
	public boolean copy(String sourceFileName, String destinationFileName) {
		StoreFile sourceStoreFile = new StoreFile(sourceFileName);
		StoreFile destinationStoreFile = new StoreFile(destinationFileName);

		return _copy(
			sourceStoreFile._companyId, sourceStoreFile._repositoryId,
			sourceStoreFile._fileName, sourceStoreFile._versionLabel,
			sourceStoreFile._storeArea, destinationStoreFile._companyId,
			destinationStoreFile._repositoryId, destinationStoreFile._fileName,
			destinationStoreFile._versionLabel,
			destinationStoreFile._storeArea);
	}

	@Override
	public boolean copyDirectory(
		long companyId, long repositoryId, String dirName,
		StoreArea[] sourceStoreAreas, StoreArea destinationStoreArea) {

		for (StoreArea sourceStoreArea : sourceStoreAreas) {
			String[] fileNames = StoreArea.withStoreArea(
				sourceStoreArea,
				() -> _store.getFileNames(companyId, repositoryId, dirName));

			for (String fileName : fileNames) {
				String[] versionLabels = StoreArea.withStoreArea(
					sourceStoreArea,
					() -> _store.getFileVersions(
						companyId, repositoryId, fileName));

				for (String versionLabel : versionLabels) {
					if (!_copy(
							companyId, repositoryId, fileName, versionLabel,
							sourceStoreArea, companyId, repositoryId, fileName,
							versionLabel, destinationStoreArea)) {

						return false;
					}
				}
			}
		}

		return true;
	}

	@Override
	public boolean copyDirectory(
		long companyId, StoreArea[] sourceStoreAreas,
		StoreArea destinationStoreArea) {

		for (StoreArea sourceStoreArea : sourceStoreAreas) {
			File companyDir = new File(
				_rootPath.toFile(), sourceStoreArea.getPath(companyId));

			File[] repositoryDirs = companyDir.listFiles(
				file ->
					file.isDirectory() && Validator.isNumber(file.getName()));

			if (repositoryDirs == null) {
				continue;
			}

			for (File repositoryDir : repositoryDirs) {
				if (!copyDirectory(
						companyId, GetterUtil.getLong(repositoryDir.getName()),
						StringPool.BLANK, new StoreArea[] {sourceStoreArea},
						destinationStoreArea)) {

					return false;
				}
			}
		}

		return true;
	}

	private boolean _copy(
		long sourceCompanyId, long sourceRepositoryId, String sourceFileName,
		String sourceVersionLabel, StoreArea sourceStoreArea,
		long destinationCompanyId, long destinationRepositoryId,
		String destinationFileName, String destinationVersionLabel,
		StoreArea destinationStoreArea) {

		try {
			if (!StoreArea.withStoreArea(
					sourceStoreArea,
					() -> _store.hasFile(
						sourceCompanyId, sourceRepositoryId, sourceFileName,
						sourceVersionLabel))) {

				return false;
			}

			try (InputStream inputStream = StoreArea.withStoreArea(
					sourceStoreArea,
					() -> _store.getFileAsStream(
						sourceCompanyId, sourceRepositoryId, sourceFileName,
						sourceVersionLabel))) {

				StoreArea.withStoreArea(
					destinationStoreArea,
					() -> _store.addFile(
						destinationCompanyId, destinationRepositoryId,
						destinationFileName, destinationVersionLabel,
						inputStream));
			}

			return true;
		}
		catch (IOException | PortalException exception) {
			if (_log.isInfoEnabled()) {
				_log.info(exception);
			}

			return false;
		}
	}

	private void _deleteEmptyAncestors(Path path, Path storeAreaPath) {
		File file = path.toFile();

		while ((file != null) && !file.equals(storeAreaPath.toFile())) {
			if (!file.delete()) {
				return;
			}

			file = file.getParentFile();
		}
	}

	private String _getName(Path path) {
		return StringUtil.replace(
			String.valueOf(_rootPath.relativize(path)), File.separatorChar,
			CharPool.SLASH);
	}

	private Path _move(Path path, String name) throws IOException {
		Path destinationPath = _rootPath.resolve(name);

		Files.createDirectories(destinationPath.getParent());

		return Files.move(
			path, destinationPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private String _processStoreArea(
		long companyId, int evictionQuota,
		UnsafeFunction<Path, Boolean, IOException> unsafeFunction,
		String startOffset, StoreArea storeArea,
		TemporalAmount temporalAmount) {

		if (!FeatureFlagManagerUtil.isEnabled(companyId, "LPS-174816")) {
			return StringPool.BLANK;
		}

		Path storeAreaPath = _rootPath.resolve(storeArea.getPath(companyId));

		if (Files.notExists(storeAreaPath)) {
			return StringPool.BLANK;
		}

		List<Path> paths = new ArrayList<>();

		try {
			Files.walkFileTree(
				storeAreaPath,
				new SimpleFileVisitor<Path>() {

					@Override
					public FileVisitResult visitFile(
						Path path, BasicFileAttributes basicFileAttributes) {

						String name = _getName(path);

						if (name.compareTo(startOffset) > 0) {
							paths.add(path);
						}

						return FileVisitResult.CONTINUE;
					}

				});
		}
		catch (IOException ioException) {
			_log.error(ioException);

			return StringPool.BLANK;
		}

		Collections.sort(paths);

		Instant evictionInstant = Instant.now(
		).minus(
			temporalAmount
		);
		int evictedFileQuota = Math.max(evictionQuota, 1);

		for (Path path : paths) {
			try {
				FileTime fileTime = Files.getLastModifiedTime(path);

				if (fileTime.toInstant(
					).isBefore(
						evictionInstant
					) && unsafeFunction.apply(path)) {

					_deleteEmptyAncestors(path.getParent(), storeAreaPath);

					evictedFileQuota--;
				}
			}
			catch (IOException ioException) {
				_log.error("Unable to process " + path, ioException);
			}

			if (evictedFileQuota == 0) {
				return _getName(path);
			}
		}

		return StringPool.BLANK;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		FileSystemStoreAreaProcessor.class);

	private final Path _rootPath;
	private final Store _store;

	private static class StoreFile {

		private StoreFile(String name) {
			StoreArea storeArea = StoreArea.LIVE;

			for (StoreArea curStoreArea : StoreArea.values()) {
				String path = curStoreArea.getPath();

				if (Validator.isNotNull(path) && name.startsWith(path)) {
					storeArea = curStoreArea;

					name = name.substring(path.length());

					break;
				}
			}

			_storeArea = storeArea;

			int x = name.indexOf(CharPool.SLASH);

			_companyId = GetterUtil.getLong(name.substring(0, x));

			int y = name.indexOf(CharPool.SLASH, x + 1);

			_repositoryId = GetterUtil.getLong(name.substring(x + 1, y));

			int z = name.lastIndexOf(CharPool.SLASH);

			_fileName = name.substring(y + 1, z);
			_versionLabel = name.substring(z + 1);
		}

		private final long _companyId;
		private final String _fileName;
		private final long _repositoryId;
		private final StoreArea _storeArea;
		private final String _versionLabel;

	}

}