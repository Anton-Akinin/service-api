package com.epam.ta.reportportal.core.integration.plugin.м2;

import com.epam.ta.reportportal.core.plugin.PluginInfo;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public interface PluginInfoValidator {
	void validate(PluginInfo pluginInfo) throws PluginValidationException;
}
