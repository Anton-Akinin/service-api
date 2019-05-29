/*
 * Copyright 2018 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.ta.reportportal.core.log.impl;

import com.epam.ta.reportportal.commons.ReportPortalUser;
import com.epam.ta.reportportal.core.item.TestItemService;
import com.epam.ta.reportportal.core.log.ICreateLogHandler;
import com.epam.ta.reportportal.dao.LaunchRepository;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.entity.item.TestItem;
import com.epam.ta.reportportal.entity.log.Log;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.converter.builders.LogBuilder;
import com.epam.ta.reportportal.ws.model.EntryCreatedAsyncRS;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nonnull;
import javax.inject.Provider;

/**
 * Create log handler. Save log and binary data related to it
 *
 * @author Henadzi Vrubleuski
 * @author Andrei Varabyeu
 */
@Service
@Primary
public class CreateLogHandler implements ICreateLogHandler {

	@Autowired
	TestItemRepository testItemRepository;

	@Autowired
	TestItemService testItemService;

	@Autowired
	LaunchRepository launchRepository;

	@Autowired
	LogRepository logRepository;

	/**
	 * We are using {@link Provider} there because we need
	 * {@link SaveLogBinaryDataTask} with scope prototype. Since current class is in
	 * singleton scope, we have to find a way to get new instance of job for new
	 * execution
	 */
	@Autowired
	private Provider<SaveLogBinaryDataTask> saveLogBinaryDataTask;

	@Autowired
	@Qualifier("saveLogsTaskExecutor")
	private TaskExecutor taskExecutor;

	@Override
	@Nonnull
	//TODO check saving an attachment of the item of the project A in the project's B directory
	public EntryCreatedAsyncRS createLog(@Nonnull SaveLogRQ request, MultipartFile file, ReportPortalUser.ProjectDetails projectDetails) {

		TestItem testItem = testItemRepository.findByUuid(request.getTestItemId())
				.orElseThrow(() -> new ReportPortalException(ErrorType.TEST_ITEM_NOT_FOUND, request.getTestItemId()));

		validate(request);

		Log log = new LogBuilder().addSaveLogRq(request).addTestItem(testItem).get();
		logRepository.save(log);

		if (null != file) {

			Long launchId = testItemService.getEffectiveLaunch(testItem).getId();

			taskExecutor.execute(saveLogBinaryDataTask.get()
					.withFile(file)
					.withProjectId(projectDetails.getProjectId())
					.withLaunchId(launchId)
					.withItemId(testItem.getItemId())
					.withLogId(log.getId()));

		}

		return new EntryCreatedAsyncRS(log.getId());
	}


}
