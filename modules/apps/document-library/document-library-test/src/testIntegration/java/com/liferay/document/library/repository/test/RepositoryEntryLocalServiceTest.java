/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.document.library.repository.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.reflect.ReflectionUtil;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.RepositoryEntry;
import com.liferay.portal.kernel.security.auth.CompanyInheritableThreadLocalCallable;
import com.liferay.portal.kernel.service.RepositoryEntryLocalService;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LogEntry;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Ben Demetrius
 */
@RunWith(Arquillian.class)
public class RepositoryEntryLocalServiceTest {

	@ClassRule
	@Rule
	public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
		new LiferayIntegrationTestRule();

	@Test
	public void testGetRepositoryEntry() throws Throwable {
		long groupId = TestPropsValues.getGroupId();
		String mappedId = RandomTestUtil.randomString();
		long repositoryId = RandomTestUtil.randomLong();
		long userId = TestPropsValues.getUserId();

		FutureTask<RepositoryEntry> futureTask = new FutureTask<>(
			new CompanyInheritableThreadLocalCallable<>(
				() -> _repositoryEntryLocalService.getRepositoryEntry(
					userId, groupId, repositoryId, mappedId)));

		Thread thread = new Thread(
			futureTask, "Repository Entry Local Service Test");

		Bundle bundle = FrameworkUtil.getBundle(
			RepositoryEntryLocalServiceTest.class);

		BundleContext bundleContext = bundle.getBundleContext();

		CountDownLatch pausedCountDownLatch = new CountDownLatch(1);
		CountDownLatch resumeCountDownLatch = new CountDownLatch(1);

		ServiceRegistration<ModelListener<RepositoryEntry>>
			serviceRegistration = bundleContext.registerService(
				(Class<ModelListener<RepositoryEntry>>)
					(Class<?>)ModelListener.class,
				new BaseModelListener<RepositoryEntry>() {

					@Override
					public void onBeforeCreate(
						RepositoryEntry repositoryEntry) {

						if (mappedId.equals(repositoryEntry.getMappedId()) &&
							(Thread.currentThread() == thread)) {

							pausedCountDownLatch.countDown();

							try {
								resumeCountDownLatch.await();
							}
							catch (InterruptedException interruptedException) {
								ReflectionUtil.throwException(
									interruptedException);
							}
						}
					}

				},
				null);

		try (LogCapture logCapture = LoggerTestUtil.configureLog4JLogger(
				"org.hibernate.engine.jdbc.spi.SqlExceptionHelper",
				LoggerTestUtil.ERROR)) {

			thread.start();

			pausedCountDownLatch.await();

			RepositoryEntry repositoryEntry =
				_repositoryEntryLocalService.getRepositoryEntry(
					userId, groupId, repositoryId, mappedId);

			resumeCountDownLatch.countDown();

			RepositoryEntry pausedRepositoryEntry = futureTask.get();

			Assert.assertEquals(
				repositoryEntry.getRepositoryEntryId(),
				pausedRepositoryEntry.getRepositoryEntryId());

			List<LogEntry> logEntries = logCapture.getLogEntries();

			Assert.assertFalse(logEntries.isEmpty());

			List<RepositoryEntry> repositoryEntries =
				_repositoryEntryLocalService.getRepositoryEntries(repositoryId);

			Assert.assertEquals(
				repositoryEntries.toString(), 1, repositoryEntries.size());
		}
		finally {
			resumeCountDownLatch.countDown();

			thread.join();

			serviceRegistration.unregister();

			_repositoryEntryLocalService.deleteRepositoryEntries(
				repositoryId, List.of(mappedId));
		}
	}

	@Inject
	private RepositoryEntryLocalService _repositoryEntryLocalService;

}