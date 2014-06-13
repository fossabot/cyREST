package org.cytoscape.rest.internal.datamapper;

import java.util.HashMap;
import java.util.Map;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.rest.internal.MappingFactoryManager;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.vizmap.VisualMappingFunction;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;

import com.fasterxml.jackson.databind.JsonNode;

public class VisualStyleMapper {

	private static final String TITLE = "title";
	private static final String MAPPINGS = "mappings";
	private static final String DEFAULTS = "defaults";

	private static final String MAPPING_TYPE = "type";
	private static final String MAPPING_DISCRETE = "discrete";
	private static final String MAPPING_PASSTHROUGH = "passthrough";
	private static final String MAPPING_CONTINUOUS = "continuous";

	private static final String MAPPING_COLUMN = "column";
	private static final String MAPPING_COLUMN_TYPE = "column_type";
	private static final String MAPPING_VP = "visual_property";

	private static final String MAPPING_DISCRETE_MAP = "map";
	private static final String MAPPING_DISCRETE_KEY = "key";
	private static final String MAPPING_DISCRETE_VALUE = "value";

	public VisualStyle buildVisualStyle(final MappingFactoryManager factoryManager, final VisualStyleFactory factory,
			final VisualLexicon lexicon, final JsonNode rootNode) {

		final JsonNode title = rootNode.get(TITLE);
		final VisualStyle style = factory.createVisualStyle(title.textValue());

		final JsonNode defaults = rootNode.get(DEFAULTS);
		final JsonNode mappings = rootNode.get(MAPPINGS);

		parseDefaults(defaults, style, lexicon);
		parseMappings(mappings, style, lexicon, factoryManager);

		return style;
	}

	public void buildMappings(final VisualStyle style, final MappingFactoryManager factoryManager,
			final VisualLexicon lexicon, final JsonNode mappings) {
		parseMappings(mappings, style, lexicon, factoryManager);
	}
	
	public void updateStyleName(final VisualStyle style,
			final VisualLexicon lexicon, final JsonNode rootNode) {
		final String newTitle = rootNode.get(TITLE).textValue();
		style.setTitle(newTitle);
	}

	@SuppressWarnings("rawtypes")
	private final void parseDefaults(final JsonNode defaults, final VisualStyle style, final VisualLexicon lexicon) {
		for (final JsonNode vpNode : defaults) {
			String vpName = vpNode.get(MAPPING_VP).textValue();
			final VisualProperty vp = getVisualProperty(vpName, lexicon);
			if (vp == null) {
				continue;
			}
			style.setDefaultValue(vp, vp.parseSerializableString(vpNode.get("value").textValue()));

		}
	}

	private final void parseMappings(JsonNode mappings, VisualStyle style, VisualLexicon lexicon,
			MappingFactoryManager factoryManager) {
		for (final JsonNode mapping : mappings) {
			final String type = mapping.get(MAPPING_TYPE).textValue();
			final String column = mapping.get(MAPPING_COLUMN).textValue();
			final String colType = mapping.get(MAPPING_COLUMN_TYPE).textValue();
			final String vpName = mapping.get(MAPPING_VP).textValue();

			final VisualProperty vp = getVisualProperty(vpName, lexicon);
			final Class<?> columnType = getColumnClass(colType);
			if (vp == null || columnType == null) {
				return;
			}

			VisualMappingFunction newMapping = null;
			if (type.equals(MAPPING_DISCRETE)) {
				final VisualMappingFunctionFactory factory = factoryManager.getFactory(DiscreteMapping.class);
				newMapping = parseDiscrete(column, columnType, vp, factory, mapping.get(MAPPING_DISCRETE_MAP));
			} else if (type.equals(MAPPING_CONTINUOUS)) {
				final VisualMappingFunctionFactory factory = factoryManager.getFactory(ContinuousMapping.class);
				newMapping = parseContinuous(column, columnType, vp, factory);
			} else if (type.equals(MAPPING_PASSTHROUGH)) {
				final VisualMappingFunctionFactory factory = factoryManager.getFactory(PassthroughMapping.class);
				newMapping = parsePassthrough(column, columnType, vp, factory);
			}

			if (newMapping != null) {
				style.addVisualMappingFunction(newMapping);
			}
		}
	}

	private final VisualProperty getVisualProperty(String vpName, VisualLexicon lexicon) {
		VisualProperty vp = null;

		if (vpName.startsWith("NODE")) {
			vp = lexicon.lookup(CyNode.class, vpName);
		} else if (vpName.startsWith("EDGE")) {
			vp = lexicon.lookup(CyEdge.class, vpName);
		} else if (vpName.startsWith("NETWORK")) {
			vp = lexicon.lookup(CyNetwork.class, vpName);
		}
		return vp;
	}

	private final Class<?> getColumnClass(final String type) {
		if (type.equals(Double.class.getSimpleName())) {
			return Double.class;
		} else if (type.equals(Long.class.getSimpleName())) {
			return Long.class;
		} else if (type.equals(Integer.class.getSimpleName())) {
			return Integer.class;
		} else if (type.equals(Float.class.getSimpleName())) {
			return Float.class;
		} else if (type.equals(Boolean.class.getSimpleName())) {
			return Boolean.class;
		} else if (type.equals(String.class.getSimpleName())) {
			return String.class;
		} else {
			return null;
		}
	}

	private final Object parseKeyValue(final Class<?> type, final String value) {
		if (type == Double.class) {
			return Double.parseDouble(value);
		} else if (type == Long.class) {
			return Long.parseLong(value);
		} else if (type == Integer.class) {
			return Integer.parseInt(value);
		} else if (type == Float.class) {
			return Float.parseFloat(value);
		} else if (type == Boolean.class) {
			return Boolean.parseBoolean(value);
		} else if (type == String.class) {
			return value;
		} else {
			return null;
		}
	}

	private final DiscreteMapping parseDiscrete(String columnName, Class<?> type, VisualProperty<?> vp,
			VisualMappingFunctionFactory factory, JsonNode discreteMapping) {
		DiscreteMapping mapping = (DiscreteMapping) factory.createVisualMappingFunction(columnName, type, vp);

		final Map map = new HashMap();
		for (JsonNode pair : discreteMapping) {
			final Object key = parseKeyValue(type, pair.get(MAPPING_DISCRETE_KEY).textValue());
			if (key != null) {
				map.put(key, vp.parseSerializableString(pair.get(MAPPING_DISCRETE_VALUE).textValue()));
			}
		}
		mapping.putAll(map);
		return mapping;
	}

	private final ContinuousMapping parseContinuous(String columnName, Class<?> type, VisualProperty<?> vp,
			VisualMappingFunctionFactory factory) {

		ContinuousMapping mapping = (ContinuousMapping) factory.createVisualMappingFunction(columnName, type, vp);

		return mapping;
	}

	private final PassthroughMapping parsePassthrough(String columnName, Class<?> type, VisualProperty<?> vp,
			VisualMappingFunctionFactory factory) {

		return (PassthroughMapping) factory.createVisualMappingFunction(columnName, type, vp);

	}

}
