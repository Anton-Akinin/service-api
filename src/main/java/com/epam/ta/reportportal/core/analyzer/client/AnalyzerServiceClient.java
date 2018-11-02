/*
 * Copyright 2017 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-api
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.epam.ta.reportportal.core.analyzer.client;

import com.epam.ta.reportportal.core.analyzer.IAnalyzerServiceClient;
import com.epam.ta.reportportal.core.analyzer.model.AnalyzedItemRs;
import com.epam.ta.reportportal.core.analyzer.model.IndexLaunch;
import com.epam.ta.reportportal.core.analyzer.model.IndexRs;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.epam.ta.reportportal.core.analyzer.client.ClientUtils.ANALYZER_KEY;
import static com.epam.ta.reportportal.core.analyzer.client.ClientUtils.SUPPORT_INDEX;
import static java.util.stream.Collectors.toList;

@Service
public class AnalyzerServiceClient implements IAnalyzerServiceClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerServiceClient.class);

	static final String INDEX_PATH = "/_index";
	static final String ANALYZE_PATH = "/_analyze";

	private static final String ITEM_IDS_KEY = "ids";
	private static final String INDEX_NAME_KEY = "project";

	private final RestTemplate restTemplate;

	private final DiscoveryClient discoveryClient;

	private AtomicReference<List<ServiceInstance>> analyzerInstances;

	//*************************************

	static final String INDEX_ROUTE = "index";
	static final String ANALYZE_ROUTE = "analyze";
	static final String DELETE_ROUTE = "delete";

	@Autowired
	private RabbitMqManagementClient rabbitMqManagementClient;

	@Autowired
	private AsyncRabbitTemplate asyncRabbitTemplate;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	public AnalyzerServiceClient(RestTemplate restTemplate, DiscoveryClient discoveryClient) {
		this.analyzerInstances = new AtomicReference<>(Collections.emptyList());
		this.restTemplate = restTemplate;
		this.discoveryClient = discoveryClient;
	}

	@Override
	public boolean hasClients() {
		return rabbitMqManagementClient.getAnalyzerExchanges().size() != 0;
	}

	@Override
	public List<CompletableFuture<IndexRs>> index(List<IndexLaunch> rq) {
		return rabbitMqManagementClient.getAnalyzerExchanges()
				.stream()
				.flatMap(exchange -> rq.stream()
						.map(indexLaunch -> asyncRabbitTemplate.<IndexRs>convertSendAndReceive(exchange.getName(),
								rabbitMqManagementClient.getAnalyzerQueue(INDEX_ROUTE).getName(),
								indexLaunch
						)))
				.map(future -> {
					CompletableFuture<IndexRs> f = new CompletableFuture<>();
					future.addCallback(new ListenableFutureCallback<IndexRs>() {
						@Override
						public void onFailure(Throwable ex) {
							f.completeExceptionally(ex);
						}

						@Override
						public void onSuccess(IndexRs result) {
							f.complete(result);
						}
					});
					return f;
				})
				.collect(Collectors.toList());
	}

	@Override
	public Map<String, List<AnalyzedItemRs>> analyze(IndexLaunch rq) {
		Map<String, List<AnalyzedItemRs>> result = new HashMap<>(analyzerInstances.get().size());
		analyzerInstances.get().forEach(instance -> {
			List<AnalyzedItemRs> analyzed = analyze(instance, rq);
			if (!CollectionUtils.isEmpty(analyzed)) {
				result.put(instance.getMetadata().get(ClientUtils.ANALYZER_KEY), analyzed);
				removeAnalyzedFromRq(rq, analyzed);
			}
		});
		return result;
	}

	@Override
	public void cleanIndex(String index, List<String> ids) {
		analyzerInstances.get().stream().filter(SUPPORT_INDEX).forEach(instance -> cleanIndex(instance, index, ids));
	}

	@Override
	public void deleteIndex(String index) {
		rabbitMqManagementClient.getAnalyzerExchanges()
				.forEach(exchange -> rabbitTemplate.convertAndSend(exchange.getName(),
						rabbitMqManagementClient.getAnalyzerQueue(DELETE_ROUTE).getName(),
						index
				));
	}

	private void deleteIndex(ServiceInstance instance, String index) {
		try {
			restTemplate.delete(instance.getUri().toString() + INDEX_PATH + "/" + index);
		} catch (Exception e) {
			LOGGER.error("Index deleting failed. Cannot interact with {} analyzer. Error: {}", instance.getMetadata().get(ANALYZER_KEY), e);
		}
	}

	/**
	 * Removes form rq analyzed items to make rq for the next analyzer.
	 *
	 * @param rq       Request
	 * @param analyzed List of analyzer items
	 */
	private void removeAnalyzedFromRq(IndexLaunch rq, List<AnalyzedItemRs> analyzed) {
		List<String> analyzedItemIds = analyzed.stream().map(AnalyzedItemRs::getItemId).collect(toList());
		rq.getTestItems().removeIf(it -> analyzedItemIds.contains(it.getTestItemId()));
	}

	/**
	 * Removes items with specified ids from index
	 *
	 * @param instance Analyzer instance
	 * @param project  Project/Index in ES
	 * @param ids      Ids to be removed
	 */
	private void cleanIndex(ServiceInstance instance, String project, List<String> ids) {
		try {
			restTemplate.put(instance.getUri().toString() + INDEX_PATH + "/delete",
					ImmutableMap.<String, Object>builder().put(ITEM_IDS_KEY, ids).put(INDEX_NAME_KEY, project).build()
			);
		} catch (Exception e) {
			LOGGER.error(
					"Documents deleting failed. Cannot interact with {} analyzer. Error: {}",
					instance.getMetadata().get(ANALYZER_KEY),
					e
			);
		}
	}

	/**
	 * Indexes test items logs by specified analyzer instance.
	 *
	 * @param analyzer Analyzer
	 * @param rq       Request {@link List< IndexLaunch >} to index
	 * @return {@link Optional} of {@link IndexRs} with indexed items
	 */
	private Optional<IndexRs> index(ServiceInstance analyzer, List<IndexLaunch> rq) {
		try {
			ResponseEntity<IndexRs> responseEntity = restTemplate.postForEntity(
					analyzer.getUri().toString() + INDEX_PATH,
					rq,
					IndexRs.class
			);
			return Optional.ofNullable(responseEntity.getBody());
		} catch (Exception e) {
			LOGGER.error("Indexing failed. Cannot interact with {} analyzer. Error: {}", analyzer.getMetadata().get(ANALYZER_KEY), e);
		}
		return Optional.empty();
	}

	/**
	 * Analyze test items by specified analyzer instance.
	 *
	 * @param analyzer Analyzer
	 * @param rq       Request {@link IndexLaunch} to analyze
	 * @return {@link Optional} of {@link IndexLaunch} with analyzed items
	 */
	private List<AnalyzedItemRs> analyze(ServiceInstance analyzer, IndexLaunch rq) {
		try {
			ResponseEntity<AnalyzedItemRs[]> responseEntity = restTemplate.postForEntity(
					analyzer.getUri().toString() + ANALYZE_PATH,
					Collections.singletonList(rq),
					AnalyzedItemRs[].class
			);
			AnalyzedItemRs[] rs = responseEntity.getBody();
			return Arrays.asList(rs);
		} catch (Exception e) {
			LOGGER.error("Analyzing failed. Cannot interact with {} analyzer.", analyzer.getMetadata().get(ANALYZER_KEY), e);
		}
		return Collections.emptyList();
	}

	/**
	 * Update list of available analyzers instances
	 */
/*	@EventListener
	private void getAnalyzerServiceInstances(ConsulUpdateEvent event) {
		List<ServiceInstance> collect = discoveryClient.getServices()
				.stream()
				.flatMap(service -> discoveryClient.getInstances(service).stream())
				.filter(instance -> instance.getMetadata().containsKey(ANALYZER_KEY))
				.sorted(comparingInt(SERVICE_PRIORITY))
				.collect(toList());
		analyzerInstances.set(collect);
	}*/
}
