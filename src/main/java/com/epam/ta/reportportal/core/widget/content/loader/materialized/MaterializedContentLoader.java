package com.epam.ta.reportportal.core.widget.content.loader.materialized;

import com.epam.ta.reportportal.entity.widget.Widget;
import org.springframework.util.MultiValueMap;

import java.util.Map;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public interface MaterializedContentLoader {

	String REFRESH = "refresh";
	String ATTRIBUTES = "attributes";
	String VIEW_NAME = "viewName";

	Map<String, Object> loadContent(Widget widget, MultiValueMap<String, String> params);
}
