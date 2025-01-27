package org.component;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.PropertyConfigurerSupport;

import java.util.HashMap;
import java.util.Map;

public class EmailCsvProcessorComponentConfigurer extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

    // Map holding configurations in key-value form for property resolution
    private final Map<String, Class<?>> configProperties = new HashMap<>();

    public EmailCsvProcessorComponentConfigurer() {
        // Declare available properties (customize based on your component's needs)
        configProperties.put("sampleProperty", String.class); // Example of custom property for demonstration
    }

    /**
     * This method sets a property to the EmailCsvProcessorComponent.
     *
     * @param camelContext The Camel context (if needed for custom logic)
     * @param target       The instance of the target property owner (EmailCsvProcessorComponent in this case)
     * @param name         The name of the property
     * @param value        The value to set on the target object
     * @param ignoreCase   Whether to ignore case when matching the property name
     * @return true if the property was set, otherwise false
     */
    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target instanceof EmailCsvProcessorComponent) {
            EmailCsvProcessorComponent component = (EmailCsvProcessorComponent) target;
            if (ignoreCase && "sampleProperty".equalsIgnoreCase(name) || "sampleProperty".equals(name)) {
                // Set the value of a sample property
                System.out.println("Setting property 'sampleProperty' to value: " + value); // Logging for demo purposes
                // Here, you can set any custom property on the component, e.g., `component.setSomeProperty(value)`
                return true;
            }
        }
        return false; // Return false if the property name or target is not supported
    }

    /**
     * This method gets the class type of a property by name.
     *
     * @param name       The name of the property
     * @param ignoreCase Whether to ignore case when matching the property name
     * @return The Java Class type of the property, or null if no property found
     */
    @Override
    public Class<?> getOptionType(String name, boolean ignoreCase) {
        for (Map.Entry<String, Class<?>> entry : configProperties.entrySet()) {
            if (ignoreCase && entry.getKey().equalsIgnoreCase(name) || entry.getKey().equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * This method retrieves the value of a given property name.
     *
     * @param target     The object for which the property is being retrieved
     * @param name       The name of the property
     * @param ignoreCase Whether to ignore case when matching the property name
     * @return The property value (if exists), otherwise null
     */
    @Override
    public Object getOptionValue(Object target, String name, boolean ignoreCase) {
        if (target instanceof EmailCsvProcessorComponent) {
            // Retrieve a value for the demo "sampleProperty"
            if (ignoreCase && "sampleProperty".equalsIgnoreCase(name) || "sampleProperty".equals(name)) {
                // Placeholder logic — return the actual value from the component if needed
                return "sampleValue"; // Example default value
            }
        }
        return null;
    }

    /**
     * This method gets the type of a property by only using the property name (case-sensitive).
     *
     * @param target The instance of the target property owner (EmailCsvProcessorComponent in this case)
     * @param name   The name of the property
     * @return The Java Class type of the property or null
     */
    @Override
    public Class<?> getOptionType(Object target, String name) {
        return configProperties.get(name); // Return the property type based on its name
    }

    /**
     * Retrieves the runtime value of a property from the given target object.
     *
     * @param target The instance of the target property owner (EmailCsvProcessorComponent in this case)
     * @param name   The name of the property
     * @return The value of the property, or null if not found
     */
    @Override
    public Object getOptionValue(Object target, String name) {
        if (target instanceof EmailCsvProcessorComponent && configProperties.containsKey(name)) {
            return getOptionValue(target, name, false); // Delegate to the case-sensitive version
        }
        return null;
    }

    /**
     * Gets all property names and their types for the EmailCsvProcessorComponent.
     *
     * @param target The instance of the target property owner (EmailCsvProcessorComponent in this case)
     * @return A map of all properties and their types
     */
    @Override
    public Map<String, Class<?>> getOptions(Object target) {
        if (target instanceof EmailCsvProcessorComponent) {
            return configProperties; // Relevant properties for the component
        }
        return new HashMap<>(); // Return an empty map if the target is not supported
    }
}