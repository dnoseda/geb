/* 
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geb

import geb.buildadapter.BuildAdapterFactory

/**
 * Manages the process of creating {@link geb.Configuration} objects, which control the runtime behaviour of Geb.
 * <p>
 * Typically usage of this class is hidden from the user as the {@link geb.Browser} uses this class internally
 * to load configuration based on its construction. If however custom configuration loading mechanics are necessary,
 * users can use this class or a subclass of to create a {@link geb.Configuration} object and construct the {@link geb.Browser}
 * with that.
 * <p>
 * Another avenue for custom configuration is usage of the {@link geb.BuildAdapter build adapter}. The build adapter that
 * will be used with any loaded configurations will be what is provided by {@link #createBuildAdapter()}
 * 
 * @see geb.Configuration
 * @see geb.Browser
 */
class ConfigurationLoader {
	
	final ClassLoader classLoader
	final String environment
	final Properties properties
	final BuildAdapter buildAdapter
	
	/**
	 * Configures the loader using the defaults.
	 * 
	 * @see ConfigurationLoader(ClassLoader, String, Properties)
	 */
	ConfigurationLoader() {
		this(null, null, null)
	}
	
	/**
	 * Configures the loader with the given environment for parsing config scripts, and defaults for everything else.
	 * 
	 * @see ConfigurationLoader(ClassLoader, String, Properties)
	 */
	ConfigurationLoader(String environment) {
		this(environment, null, null)
	}
	
	/**
	 * Sets the loader environment.
	 * <p>
	 * If any of the parameters are {@code null}, the appropriate {@code getDefault«something»()} method will be used to supply the value.
	 * 
	 * @param classLoader The loader to use to find classpath resources and to {@link #createBuildAdapter() load the build adapter}
	 * @param environment If loading a config script, the environment to load it with
	 * @param properties The properties given to created {@link geb.Configuration} objects
	 * @see #getDefaultClassLoader()
	 * @see #getDefaultEnvironment()
	 * @see getDefaultProperties()
	 */
	ConfigurationLoader(String environment, Properties properties, ClassLoader classLoader) {
		this.environment = environment ?: getDefaultEnvironment()
		this.properties = properties ?: getDefaultProperties()
		this.classLoader = classLoader ?: getDefaultClassLoader()
	}
	
	/**
	 * Creates a config using the default path for the config script.
	 * <p>
	 * Uses {@link #getDefaultConfigScriptResourcePath()} for the path.
	 * 
	 * @throws geb.ConfigurationLoader.UnableToLoadException if the config script exists but could not be read or parsed.
	 * @see #getConf(String)
	 */
	Configuration getConf() throws UnableToLoadException {
		getConf(null)
	}
	
	/**
	 * Creates a config backed by the classpath config script resource at the given path.
	 * <p>
	 * If no classpath resource can be found at the given path, an empty config object will be used.
	 * 
	 * @param configFileResourcePath the classpath relative path to the config script to use (if {@code null}, {@link #getDefaultConfigScriptResourcePath() default} will be used).
	 * @throws geb.ConfigurationLoader.UnableToLoadException if the config script exists but could not be read or parsed.
	 * @see #getConf(URL)
	 */
	Configuration getConf(String configFileResourcePath) throws UnableToLoadException {
		configFileResourcePath = configFileResourcePath ?: getDefaultConfigScriptResourcePath()
		def resource = classLoader.getResource(configFileResourcePath)
		resource ? getConf(resource) : createConf(new ConfigObject())
	}
	
	/**
	 * Creates a config backed by the config script at the given URL.
	 * <p>
	 * If URL is {@code null} or doesn't exist, an exception will be thrown.
	 * 
	 * @param configLocation The absolute URL to the config script to use for the config (cannot be {@code null})
	 * @throws geb.ConfigurationLoader.UnableToLoadException if the config script exists but could not be read or parsed.
	 */
	Configuration getConf(URL configLocation) throws UnableToLoadException {
		if (configLocation == null) {
			throw new IllegalArgumentException("configLocation cannot be null")
		}
		
		createConf(loadRawConfig(configLocation))
	}
	
	/**
	 * This implementation returns {@code new GroovyClassLoader()} which uses 
	 * {@code Thread.currentThread().contextClassLoader} as the parent.
	 * 
	 * @see groovy.lang.GroovyClassLoader
	 */
	protected GroovyClassLoader getDefaultClassLoader() {
		new GroovyClassLoader()
	}
	
	/**
	 * This implementation returns {@code System.properties["geb.env"]}
	 */
	protected String getDefaultEnvironment() {
		System.properties["geb.env"]
	}

	/**
	 * This implementation returns {@code System.properties}
	 */	
	protected Properties getDefaultProperties() {
		System.properties
	}
	
	/**
	 * This implementation returns {@code "GebConfig.groovy"}
	 */
	String getDefaultConfigScriptResourcePath() {
		"GebConfig.groovy"
	}
	
	static class UnableToLoadException extends geb.error.GebException {
		UnableToLoadException(URL configLocation, String environment, Throwable cause) {
			super("Unable to load configuration @ '$configLocation' (with environment: $environment)", cause)
		}
	}
	
	/**
	 * Reads the config scripts at {@code configLocation} with the {@link #createSlurper()}
	 * 
	 * @throws geb.ConfigurationLoader.UnableToLoadException if the config script could not be read.
	 */
	protected ConfigObject loadRawConfig(URL configLocation) throws UnableToLoadException {
		def slurper = createSlurper()
		try {
			slurper.parse(configLocation)
		} catch (Throwable e) {
			throw new UnableToLoadException(configLocation, slurper.environment, e)
		}
	}
	
	/**
	 * Creates a config slurper with environment we were constructed with (if any) and sets
	 * the slurpers classloader to the classloader we were constructed with.
	 */
	protected ConfigSlurper createSlurper() {
		def slurper = environment ? new ConfigSlurper(environment) : new ConfigSlurper()
		slurper.classLoader = classLoader
		slurper
	}
	
	/**
	 * Creates a new {@link geb.Configuration} backed by {@code rawConfig} with the {@code properties} and {@code classLoader}
	 * we were constructed with, and a {@link geb.BuildAdapter build adapter}.
	 * 
	 * @see geb.Configuration#Configuration
	 */
	protected createConf(ConfigObject rawConfig) {
		new Configuration(rawConfig, properties, createBuildAdapter(), classLoader)
	}
	
	/**
	 * Uses the {@link geb.buildadapter.BuildAdapterFactory#getBuildAdapter(ClassLoader) build adapter factory} to load
	 * a build adapter with the {@code classLoader} we were constructed with.
	 */
	protected BuildAdapter createBuildAdapter() {
		BuildAdapterFactory.getBuildAdapter(classLoader)
	}
	
}