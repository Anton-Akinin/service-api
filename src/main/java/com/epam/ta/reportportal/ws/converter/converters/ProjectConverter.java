/*
 * Copyright (C) 2018 EPAM Systems
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

package com.epam.ta.reportportal.ws.converter.converters;

import com.epam.ta.reportportal.entity.item.issue.IssueType;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.ws.model.project.ProjectConfiguration;
import com.epam.ta.reportportal.ws.model.project.ProjectResource;
import com.epam.ta.reportportal.ws.model.project.config.IssueSubTypeResource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Pavel Bortnik
 */
public final class ProjectConverter {

	private ProjectConverter() {
		//static only
	}

	public static final Function<List<IssueType>, Map<String, List<IssueSubTypeResource>>> TO_PROJECT_SUB_TYPES = issueTypes -> issueTypes.stream()
			.collect(Collectors.groupingBy(it -> it.getIssueGroup().getTestItemIssueGroup().getValue(), Collectors.mapping(it -> {
				IssueSubTypeResource issueSubTypeResource = new IssueSubTypeResource();
				issueSubTypeResource.setLocator(it.getLocator());
				issueSubTypeResource.setColor(it.getHexColor());
				issueSubTypeResource.setLongName(it.getLongName());
				issueSubTypeResource.setShortName(it.getShortName());
				issueSubTypeResource.setTypeRef(it.getIssueGroup().getTestItemIssueGroup().getValue());
				return issueSubTypeResource;
			}, Collectors.toList())));

	public static final Function<Project, ProjectResource> TO_PROJECT_RESOURCE = project -> {
		if (project == null) {
			return null;
		}

		ProjectResource projectResource = new ProjectResource();
		projectResource.setProjectId(project.getName());
		projectResource.setCreationDate(project.getCreationDate());
		projectResource.setUsers(project.getUsers().stream().map(user -> {
			ProjectResource.ProjectUser projectUser = new ProjectResource.ProjectUser();
			projectUser.setLogin(user.getUser().getLogin());
			projectUser.setProjectRole(user.getProjectRole().toString());
			return projectUser;
		}).collect(Collectors.toList()));

		Map<String, List<IssueSubTypeResource>> subTypes = TO_PROJECT_SUB_TYPES.apply(project.getIssueTypes());

		ProjectConfiguration projectConfiguration = new ProjectConfiguration();
		projectConfiguration.setSubTypes(subTypes);
		projectConfiguration.setEmailConfig(EmailConfigConverter.TO_RESOURCE.apply(project.getProjectAttributes(),
				project.getEmailCases()
		));
		projectResource.setConfiguration(projectConfiguration);
		return projectResource;
	};

}
