package org.ligoj.app.plugin.req.squash;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Simple wrapper item for test.
 * 
 * @param <K>
 *            Wrapped data type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class TableItem<K> {

	/**
	 * data
	 */
	private List<K> aaData;

}
