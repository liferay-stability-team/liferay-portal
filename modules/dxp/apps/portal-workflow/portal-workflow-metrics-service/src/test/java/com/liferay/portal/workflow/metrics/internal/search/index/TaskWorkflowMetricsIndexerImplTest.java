/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.workflow.metrics.internal.search.index;

import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.search.capabilities.SearchCapabilities;
import com.liferay.portal.search.engine.adapter.SearchEngineAdapter;
import com.liferay.portal.search.engine.adapter.document.UpdateByQueryDocumentRequest;
import com.liferay.portal.search.engine.adapter.index.RefreshIndexRequest;
import com.liferay.portal.search.index.IndexNameBuilder;
import com.liferay.portal.test.rule.LiferayUnitTestRule;
import com.liferay.portal.util.FastDateFormatFactoryImpl;
import com.liferay.portal.workflow.metrics.internal.petra.executor.WorkflowMetricsPortalExecutor;
import com.liferay.portal.workflow.metrics.model.DeleteTaskRequest;
import com.liferay.portal.workflow.metrics.model.UpdateTaskRequest;
import com.liferay.portal.workflow.metrics.model.UserAssignment;
import com.liferay.portal.workflow.metrics.search.index.constants.WorkflowMetricsIndexNameConstants;

import java.util.Collections;
import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * @author Saurasish Basak
 */
public class TaskWorkflowMetricsIndexerImplTest {

	@ClassRule
	public static LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		ReflectionTestUtil.setFieldValue(
			FastDateFormatFactoryUtil.class, "_fastDateFormatFactory",
			new FastDateFormatFactoryImpl());

		Mockito.when(
			_searchCapabilities.isWorkflowMetricsSupported()
		).thenReturn(
			true
		);

		Mockito.when(
			_indexNameBuilder.getIndexName(_COMPANY_ID)
		).thenReturn(
			_INDEX_NAME_PREFIX
		);

		Mockito.doAnswer(
			invocation -> {
				UnsafeRunnable<?> unsafeRunnable = invocation.getArgument(0);

				unsafeRunnable.run();

				return null;
			}
		).when(
			_workflowMetricsPortalExecutor
		).execute(
			Mockito.<UnsafeRunnable<?>>any()
		);

		ReflectionTestUtil.setFieldValue(
			_taskWorkflowMetricsIndexerImpl, "searchCapabilities",
			_searchCapabilities);
		ReflectionTestUtil.setFieldValue(
			_taskWorkflowMetricsIndexerImpl, "searchEngineAdapter",
			_searchEngineAdapter);
		ReflectionTestUtil.setFieldValue(
			_taskWorkflowMetricsIndexerImpl, "workflowMetricsPortalExecutor",
			_workflowMetricsPortalExecutor);
		ReflectionTestUtil.setFieldValue(
			_taskWorkflowMetricsIndexerImpl, "_indexNameBuilder",
			_indexNameBuilder);
		ReflectionTestUtil.setFieldValue(
			_taskWorkflowMetricsIndexerImpl,
			"_slaTaskResultWorkflowMetricsIndexer",
			_slaTaskResultWorkflowMetricsIndexer);
	}

	@Test
	public void testDeleteTaskRefreshesInstanceIndex() {
		DeleteTaskRequest.Builder deleteTaskRequestBuilder =
			new DeleteTaskRequest.Builder();

		DeleteTaskRequest deleteTaskRequest =
			deleteTaskRequestBuilder.companyId(
				_COMPANY_ID
			).taskId(
				_TASK_ID
			).build();

		_taskWorkflowMetricsIndexerImpl.deleteTask(deleteTaskRequest);

		_assertRefreshExecutedBeforeUpdateByQuery();
	}

	@Test
	public void testUpdateTaskRefreshesInstanceIndex() {
		UpdateTaskRequest.Builder updateTaskRequestBuilder =
			new UpdateTaskRequest.Builder();

		UpdateTaskRequest updateTaskRequest =
			updateTaskRequestBuilder.assetTitleMap(
				Collections.emptyMap()
			).assetTypeMap(
				Collections.emptyMap()
			).assignments(
				Collections.singletonList(
					new UserAssignment(
						RandomTestUtil.randomLong(), "Test User"))
			).companyId(
				_COMPANY_ID
			).modifiedDate(
				new Date()
			).taskId(
				_TASK_ID
			).userId(
				RandomTestUtil.randomLong()
			).build();

		_taskWorkflowMetricsIndexerImpl.updateTask(updateTaskRequest);

		_assertRefreshExecutedBeforeUpdateByQuery();
	}

	private void _assertRefreshExecutedBeforeUpdateByQuery() {
		String expectedInstanceIndexName =
			_INDEX_NAME_PREFIX +
				WorkflowMetricsIndexNameConstants.SUFFIX_INSTANCE;

		ArgumentCaptor<RefreshIndexRequest> refreshIndexRequestArgumentCaptor =
			ArgumentCaptor.forClass(RefreshIndexRequest.class);
		ArgumentCaptor<UpdateByQueryDocumentRequest>
			updateByQueryDocumentRequestArgumentCaptor =
				ArgumentCaptor.forClass(UpdateByQueryDocumentRequest.class);

		InOrder inOrder = Mockito.inOrder(_searchEngineAdapter);

		inOrder.verify(
			_searchEngineAdapter
		).execute(
			refreshIndexRequestArgumentCaptor.capture()
		);

		inOrder.verify(
			_searchEngineAdapter
		).execute(
			updateByQueryDocumentRequestArgumentCaptor.capture()
		);

		RefreshIndexRequest refreshIndexRequest =
			refreshIndexRequestArgumentCaptor.getValue();

		Assert.assertArrayEquals(
			new String[] {expectedInstanceIndexName},
			refreshIndexRequest.getIndexNames());

		UpdateByQueryDocumentRequest updateByQueryDocumentRequest =
			updateByQueryDocumentRequestArgumentCaptor.getValue();

		Assert.assertArrayEquals(
			new String[] {expectedInstanceIndexName},
			updateByQueryDocumentRequest.getIndexNames());
	}

	private static final long _COMPANY_ID = RandomTestUtil.randomLong();

	private static final String _INDEX_NAME_PREFIX =
		RandomTestUtil.randomString();

	private static final long _TASK_ID = RandomTestUtil.randomLong();

	private final IndexNameBuilder _indexNameBuilder = Mockito.mock(
		IndexNameBuilder.class);
	private final SearchCapabilities _searchCapabilities = Mockito.mock(
		SearchCapabilities.class);
	private final SearchEngineAdapter _searchEngineAdapter = Mockito.mock(
		SearchEngineAdapter.class);
	private final SLATaskResultWorkflowMetricsIndexer
		_slaTaskResultWorkflowMetricsIndexer = Mockito.mock(
			SLATaskResultWorkflowMetricsIndexer.class);
	private final TaskWorkflowMetricsIndexerImpl
		_taskWorkflowMetricsIndexerImpl = new TaskWorkflowMetricsIndexerImpl();
	private final WorkflowMetricsPortalExecutor _workflowMetricsPortalExecutor =
		Mockito.mock(WorkflowMetricsPortalExecutor.class);

}